package org.velvet.entity.girl.psyche;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.survival.SurvivalBrain;
import org.velvet.entity.girl.survival.Threats;

import java.util.EnumMap;
import java.util.Map;

/**
 * What goes on inside her: her character ({@link Personality}), her emotions right now ({@link Emotion}) and
 * adrenaline. Server side; the emotions and adrenaline are synced (in tenths) for the editor and for how she
 * shows them.
 * <p>
 * Every quarter of a second she takes in her situation: how dangerous things are and how well she'd do
 * against them, whether she's safe and at home, the weather, the time of day, her owner (near, busy, gone),
 * music, how hungry and hurt she is, who sees her undressed, gross monsters close by. Each emotion is pulled
 * towards where that puts it, as strongly as her character feels it ({@link Personality#sensitivity}), at its
 * own pace ({@link Emotion#rise}, {@link Emotion#fade}), and settles at her character's baseline when nothing
 * goes on. On top come moments: being hurt, a gift, a tasty meal, a kind word, a victory. Emotions also
 * damp each other (joy pushes sadness and worry back, fear pushes joy away, anger crowds out fear and shame).
 * <p>
 * Like people (and girls are said to be better at this) she doesn't just drop what hurt her: bad moments
 * leave something behind that comes back as sadness when it's quiet, fading over a good while. A
 * zombie with nothing in her hands scares her; one she's armed and armored for is a mix of excitement,
 * a little fear and fighting spirit. Something good while in danger (a kill, an advancement of her owner's,
 * the dragon) waits until she knows she's safe; then the joy comes all at once.
 * <p>
 * Adrenaline isn't an emotion but a hormone: real danger she can't easily handle floods her with it. It
 * dulls her emotions, hunger and pain stop mattering, her senses reach further, and her mind does nothing but
 * get her out alive (see {@link #adrenaline()} and its users).
 */
public final class Psyche {

    /** Quarter of a second. */
    public static final int UPDATE_INTERVAL = 5;
    private static final float DT = UPDATE_INTERVAL / 20.0F;
    private static final float ADRENALINE_RISE = 3.0F, ADRENALINE_FADE = 0.06F;
    /** Emotions above this show, in her face and in emotes. */
    public static final float EXTREME = 0.9F;
    private static final int JOY_EMOTE_TICKS = 20 * 4, JOY_EMOTE_COOLDOWN = 20 * 25;

    public enum Emote { NONE, CRY, JOY, ANGRY }

    private final AbstractGirlEntity girl;
    private Personality personality;
    private final EnumMap<Emotion, Float> emotions = new EnumMap<>(Emotion.class);
    private float adrenaline;
    /** What bad moments left behind, coming back as sadness when it's quiet. */
    private float hurtMemory;
    /** Joy waiting for her to be safe before she lets it out. */
    private float pendingJoy;
    /** Game time of the last time her owner did something with her (talked, gave something, opened her menu). */
    private long lastAttention;
    private Emote emote = Emote.NONE;
    private int emoteTicks, joyCooldown;
    private boolean musicNear;
    /** What happened to her lately, in a few words each, for the language models: newest last. */
    private final java.util.ArrayDeque<String> recent = new java.util.ArrayDeque<>();
    private static final int RECENT = 8;
    @Nullable private Situation last;

    public Psyche(AbstractGirlEntity girl) {
        this.girl = girl;
        this.personality = Personality.random(girl.getRandom());
        for (Emotion e : Emotion.values()) emotions.put(e, personality.baseline(e));
    }

    public Personality personality() {
        return personality;
    }

    public void setPersonality(Personality personality) {
        this.personality = personality;
    }

    /** The emotion as it is (dulled by adrenaline: what she actually feels). */
    public float get(Emotion e) {
        return emotions.get(e) * (1 - 0.6F * adrenaline);
    }

    /** Set from the debug editor. */
    public void set(Emotion e, float value) {
        emotions.put(e, Mth.clamp(value, 0, 1));
    }

    public float adrenaline() {
        return adrenaline;
    }

    public void setAdrenaline(float value) {
        adrenaline = Mth.clamp(value, 0, 1);
    }

    public Emote emote() {
        return emote;
    }

    /** The strongest emotion she has right now, or null if none is worth mentioning. */
    @Nullable
    public Emotion dominant() {
        Emotion best = null;
        float max = 0.25F;
        for (Emotion e : Emotion.values()) {
            if (get(e) > max) {
                max = get(e);
                best = e;
            }
        }
        return best;
    }

    public Mood mood() {
        return Mood.of(this);
    }

    // ---- What her mind does with it ----

    /**
     * How much better her odds must be before she takes a fight: fear wants more, anger less (1 is as her
     * survival brain judges it).
     */
    public double fightMargin() {
        return 1 + 0.6 * get(Emotion.FEAR) - 0.4 * get(Emotion.ANGER);
    }

    /** Under adrenaline she notices things further away: up to twice as far. */
    public double senseRange() {
        return 1 + adrenaline;
    }

    /** Fighting for her life: nothing but getting out alive gets her attention (no mining, chatting, errands). */
    public boolean survivalOnly() {
        return adrenaline >= 0.4F;
    }

    /** Not in the mood for a scene. */
    public boolean notInTheMood() {
        return get(Emotion.SADNESS) >= 0.8F || get(Emotion.ANGER) >= 0.7F || get(Emotion.FEAR) >= 0.6F
                || get(Emotion.DISGUST) >= 0.8F || adrenaline >= 0.2F;
    }

    // ---- Moments ----

    /** Remembers a moment for the language models ("your owner gave you a cake"). */
    public void note(String what) {
        if (!recent.isEmpty() && recent.peekLast().equals(what)) return;
        recent.addLast(what);
        while (recent.size() > RECENT) recent.removeFirst();
    }

    public java.util.List<String> recent() {
        return java.util.List.copyOf(recent);
    }

    /** Something hit her. */
    public void onHurt(DamageSource source, float amount) {
        String by = source.getEntity() != null ? source.getEntity().getName().getString() : source.type().msgId();
        note("got hurt by " + by + " (" + Math.round(amount) + " damage)");
        float share = Mth.clamp(amount / Math.max(1, girl.getMaxHealth()), 0.05F, 1);
        if (source.getEntity() instanceof Player player) {
            boolean owner = player.getUUID().equals(girl.getOwnerUUID());
            if (owner) {
                // from the one she trusts: that hurts in more ways than one
                impulse(Emotion.SADNESS, 0.25F + share);
                impulse(Emotion.ANGER, (0.15F + share) * (1.2F - personality.get(Trait.LOYALTY)));
                impulse(Emotion.SHAME, 0.1F);
                hurtMemory += 0.3F + share;
            } else {
                impulse(Emotion.ANGER, 0.3F + share);
                impulse(Emotion.FEAR, 0.15F + share * 0.5F);
                hurtMemory += 0.1F;
            }
        } else if (source.getEntity() instanceof LivingEntity) {
            impulse(Emotion.FEAR, 0.1F + share);
            impulse(Emotion.ANGER, (0.1F + share) * personality.get(Trait.TEMPER) * 1.5F);
        } else {
            impulse(Emotion.FEAR, share * 0.6F);
        }
        impulse(Emotion.JOY, -share);
    }

    /** She killed something: how glad she is depends on how much of a threat it was (bosses most). */
    public void onKill(LivingEntity victim) {
        float worth = victim instanceof EnderDragon ? 1.0F : victim instanceof WitherBoss || victim instanceof Warden ? 0.9F
                : (float) Mth.clamp(Threats.strength(victim) / 40.0, 0.05, 0.5);
        pendingJoy += worth;
        impulse(Emotion.ANGER, -0.2F);
        note("killed a " + victim.getType().getDescription().getString());
    }

    /** Her owner (or someone) gave her something. */
    public void onGift(ItemStack stack, boolean fromOwner) {
        attention(fromOwner);
        note((fromOwner ? "your owner" : "someone") + " gave you " + stack.getCount() + " " + stack.getHoverName().getString());
        float value = Math.min(1, giftValue(stack));
        float greed = personality.get(Trait.GREED);
        impulse(Emotion.JOY, (0.12F + value * (0.3F + greed * 0.4F)) * (fromOwner ? 1.2F : 1));
        impulse(Emotion.SADNESS, -0.15F - value * 0.2F);
        impulse(Emotion.ANGER, -0.1F);
        if (isTasty(stack)) impulse(Emotion.SADNESS, -0.15F);
    }

    /** She ate something. */
    public void onAte(ItemStack stack) {
        note("ate " + stack.getHoverName().getString());
        if (isGross(stack)) {
            impulse(Emotion.DISGUST, 0.6F);
            impulse(Emotion.JOY, -0.1F);
        } else if (isTasty(stack)) {
            // something nice cheers anyone up
            impulse(Emotion.JOY, 0.2F);
            impulse(Emotion.SADNESS, -0.25F);
        }
    }

    /** Words in chat: {@code warmth} from -1 (mean) to 1 (sweet). */
    public void onWords(float warmth, boolean fromOwner) {
        attention(fromOwner);
        float weight = fromOwner ? 1.0F : 0.6F;
        if (warmth > 0) {
            impulse(Emotion.JOY, warmth * 0.3F * weight);
            impulse(Emotion.SADNESS, -warmth * 0.3F * weight);
            impulse(Emotion.ANXIETY, -warmth * 0.15F * weight);
            impulse(Emotion.SHAME, warmth * 0.05F * personality.get(Trait.SHYNESS));
        } else if (warmth < 0) {
            impulse(Emotion.SADNESS, -warmth * 0.3F * weight * personality.get(Trait.SENSITIVITY) * 2);
            impulse(Emotion.ANGER, -warmth * 0.3F * weight * (0.5F + personality.get(Trait.PRIDE)));
            impulse(Emotion.SHAME, -warmth * 0.15F * weight);
            hurtMemory += -warmth * 0.2F;
        }
    }

    /** Her owner got an advancement; the end of the dragon is a big one. Saved up until it's safe. */
    public void onOwnerAdvancement(boolean big, String title) {
        pendingJoy += big ? 0.9F : 0.15F;
        note("your owner got the advancement \"" + title + "\"");
    }

    public void onSceneFinished() {
        note("had a good time with your partner");
        impulse(Emotion.JOY, 0.3F);
        impulse(Emotion.SADNESS, -0.2F);
        impulse(Emotion.ANXIETY, -0.15F);
    }

    public void onLevelUp() {
        impulse(Emotion.JOY, 0.2F);
    }

    /** Her owner did something with her: she isn't ignored. */
    public void attention(boolean fromOwner) {
        if (fromOwner) lastAttention = girl.level().getGameTime();
    }

    private void impulse(Emotion e, float amount) {
        float gain = amount > 0 ? personality.sensitivity(e) : 1;
        emotions.put(e, Mth.clamp(emotions.get(e) + amount * gain * (1 - 0.6F * adrenaline), 0, 1));
    }

    // ---- Tick ----

    public void tick() {
        if (girl.tickCount % UPDATE_INTERVAL != 0) return;
        // a new girl (or one just loaded) hasn't been ignored by anyone yet
        if (lastAttention == 0) lastAttention = girl.level().getGameTime();
        Situation s = Situation.of(girl, this);
        last = s;
        updateAdrenaline(s);
        EnumMap<Emotion, Float> target = targets(s);
        float recovery = personality.recovery();
        for (Emotion e : Emotion.values()) {
            float now = emotions.get(e), goal = target.get(e);
            float rate = goal > now ? e.rise : e.fade * recovery;
            now += (goal - now) * Math.min(1, rate * DT);
            emotions.put(e, Mth.clamp(now, 0, 1));
        }
        inhibit();
        hurtMemory = Math.max(0, hurtMemory - 0.0015F * recovery);
        releaseJoy(s);
        updateEmote(s);
    }

    /** Danger she can't easily handle; real and close. */
    private void updateAdrenaline(Situation s) {
        float severity = 0;
        if (s.hunted) {
            severity = 0.3F + 0.7F * (float) Mth.clamp(1 - s.odds / 2.0, 0, 1);
            if (s.health < 0.5F) severity += 0.3F;
            if (s.apex) severity = 1;
            severity *= 0.7F + 0.3F * (1 - personality.get(Trait.COURAGE));
        } else if (s.burning || s.falling) {
            severity = 0.6F;
        }
        severity = Mth.clamp(severity, 0, 1);
        float rate = severity > adrenaline ? ADRENALINE_RISE : ADRENALINE_FADE;
        adrenaline += (severity - adrenaline) * Math.min(1, rate * DT);
        if (adrenaline < 0.01F) adrenaline = 0;
    }

    /** Where the situation puts each emotion. */
    private EnumMap<Emotion, Float> targets(Situation s) {
        EnumMap<Emotion, Float> t = new EnumMap<>(Emotion.class);
        for (Emotion e : Emotion.values()) t.put(e, personality.baseline(e));

        // danger: scary when she can't handle it; when she can, a thrill and fighting spirit
        if (s.threat > 0) {
            float confidence = (float) Mth.clamp((s.odds - 0.7) / 2.0, 0, 1);
            add(t, Emotion.FEAR, s.threat * (1 - confidence));
            add(t, Emotion.ANGER, s.threat * confidence * (0.4F + personality.get(Trait.TEMPER) * 0.6F));
            add(t, Emotion.JOY, s.threat * confidence * 0.4F * (0.3F + personality.get(Trait.PLAYFULNESS)));
            add(t, Emotion.ANXIETY, s.threat * 0.3F);
        }
        if (s.health < 0.35F) add(t, Emotion.FEAR, (0.35F - s.health) * 1.5F);
        if (s.burning) add(t, Emotion.FEAR, 0.6F);
        if (s.lavaNear) add(t, Emotion.FEAR, 0.3F);

        // worries: the dark outside at night, being alone, an empty stomach, things heard but not seen
        if (s.nightOutside) add(t, Emotion.ANXIETY, s.ownerNear ? 0.1F : 0.3F);
        if (!s.ownerNear) add(t, Emotion.ANXIETY, s.ownerOnline ? 0.1F : 0.2F);
        if (s.hunger > 0.6F) add(t, Emotion.ANXIETY, (s.hunger - 0.6F) * 0.8F);
        if (s.unseenThreats) add(t, Emotion.ANXIETY, 0.25F);
        if (s.thunder && !s.sheltered) add(t, Emotion.ANXIETY, 0.25F);
        if (s.ownerHurt) add(t, Emotion.ANXIETY, 0.3F * personality.get(Trait.EMPATHY) * 2);

        // quiet sadness: rain on the roof, safe at home, everything she needs, and him busy with his own things
        if (s.safe && s.raining && s.sheltered && s.ownerBusy) add(t, Emotion.SADNESS, 0.25F + 0.4F * personality.get(Trait.MELANCHOLY));
        if (s.safe && s.ignoredFor > 20 * 60 * 10) add(t, Emotion.SADNESS, 0.15F + 0.2F * personality.get(Trait.SOCIABILITY));
        if (!s.ownerOnline) add(t, Emotion.SADNESS, 0.1F * personality.get(Trait.LOYALTY) * 2);
        // what hurt her comes back when it's quiet
        if (s.safe && s.idle) add(t, Emotion.SADNESS, Math.min(0.5F, hurtMemory * 0.4F));

        // good times: him around and paying attention, a sunny day, music
        if (s.safe && s.ownerNear && s.ignoredFor < 20 * 60 * 2) add(t, Emotion.JOY, 0.15F + 0.2F * personality.get(Trait.WARMTH));
        if (s.safe && s.sunny && !s.sheltered) add(t, Emotion.JOY, 0.1F * personality.get(Trait.OPTIMISM) * 2);
        if (s.music) {
            add(t, Emotion.JOY, 0.35F);
            t.put(Emotion.SADNESS, t.get(Emotion.SADNESS) * 0.3F);
            t.put(Emotion.ANXIETY, t.get(Emotion.ANXIETY) * 0.5F);
        }

        // gross: rotting monsters close up, slime
        if (s.grossNear > 0) add(t, Emotion.DISGUST, s.grossNear * 0.5F);

        // seen undressed by people who aren't her owner (or by anyone, if she's shy enough)
        if (s.watchersWhileNude > 0) add(t, Emotion.SHAME, Math.min(0.8F, 0.3F * s.watchersWhileNude) * (0.3F + personality.get(Trait.MODESTY)));

        // a slow ebb and flow over the days, more for sensitive girls
        float rhythm = Mth.sin((girl.level().getDayTime() / 24000.0F + girl.getId() * 0.37F) * Mth.TWO_PI / 3.0F);
        add(t, Emotion.SADNESS, Math.max(0, -rhythm) * 0.06F * personality.get(Trait.SENSITIVITY) * 2);
        add(t, Emotion.JOY, Math.max(0, rhythm) * 0.06F);

        for (Emotion e : Emotion.values()) t.put(e, Mth.clamp(t.get(e) * personality.sensitivity(e), 0, 1));
        return t;
    }

    private static void add(EnumMap<Emotion, Float> t, Emotion e, float amount) {
        t.put(e, t.get(e) + amount);
    }

    /** Emotions push each other back. */
    private void inhibit() {
        float joy = emotions.get(Emotion.JOY), fear = emotions.get(Emotion.FEAR), anger = emotions.get(Emotion.ANGER);
        scale(Emotion.SADNESS, 1 - 0.04F * joy);
        scale(Emotion.ANXIETY, 1 - 0.04F * joy);
        scale(Emotion.JOY, 1 - 0.08F * fear);
        scale(Emotion.FEAR, 1 - 0.03F * anger);
        scale(Emotion.SHAME, 1 - 0.03F * anger);
    }

    private void scale(Emotion e, float factor) {
        emotions.put(e, emotions.get(e) * factor);
    }

    /** Good things that happened in danger: only once she knows she's safe. */
    private void releaseJoy(Situation s) {
        if (pendingJoy <= 0 || !s.safe || adrenaline > 0.15F) return;
        impulse(Emotion.JOY, Math.min(1, pendingJoy));
        impulse(Emotion.FEAR, -0.3F);
        impulse(Emotion.ANXIETY, -0.3F);
        pendingJoy = 0;
    }

    private void updateEmote(Situation s) {
        if (joyCooldown > 0) joyCooldown -= UPDATE_INTERVAL;
        if (emoteTicks > 0) emoteTicks -= UPDATE_INTERVAL;
        Emote next = Emote.NONE;
        boolean free = s.safe && adrenaline < 0.1F && !girl.getCurrentAction().isBusy();
        if (free) {
            if (emote == Emote.JOY && emoteTicks > 0) next = Emote.JOY;
            else if (get(Emotion.SADNESS) >= EXTREME) next = Emote.CRY;
            else if (get(Emotion.ANGER) >= EXTREME) next = Emote.ANGRY;
            else if (get(Emotion.JOY) >= EXTREME && joyCooldown <= 0) {
                next = Emote.JOY;
                emoteTicks = JOY_EMOTE_TICKS;
                joyCooldown = JOY_EMOTE_COOLDOWN;
            }
        }
        emote = next;
    }

    /** What she took in last; for the language models and the editor. */
    @Nullable
    public Situation situation() {
        return last;
    }

    // ---- Comforting things nearby ----

    /** A jukebox playing a disc within earshot (checked every two seconds). */
    boolean musicNearby() {
        if (girl.tickCount % 40 == 0 && girl.level() instanceof ServerLevel level) {
            musicNear = false;
            BlockPos at = girl.blockPosition();
            int cx = at.getX() >> 4, cz = at.getZ() >> 4;
            for (int x = cx - 1; x <= cx + 1 && !musicNear; x++) {
                for (int z = cz - 1; z <= cz + 1 && !musicNear; z++) {
                    if (!level.hasChunk(x, z)) continue;
                    LevelChunk chunk = level.getChunk(x, z);
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be instanceof JukeboxBlockEntity jukebox && jukebox.getSongPlayer().isPlaying()
                                && be.getBlockPos().distSqr(at) < 20 * 20) {
                            musicNear = true;
                            break;
                        }
                    }
                }
            }
        }
        return musicNear;
    }

    static boolean isTasty(ItemStack s) {
        return s.is(Items.CAKE) || s.is(Items.COOKIE) || s.is(Items.PUMPKIN_PIE) || s.is(Items.HONEY_BOTTLE)
                || s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE) || s.is(Items.GOLDEN_CARROT)
                || s.is(Items.SWEET_BERRIES) || s.is(Items.GLOW_BERRIES) || s.is(Items.MELON_SLICE)
                || s.is(Items.COOKED_SALMON) || s.is(Items.MUSHROOM_STEW) || s.is(Items.RABBIT_STEW) || s.is(Items.BEETROOT_SOUP);
    }

    static boolean isGross(ItemStack s) {
        return s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.POISONOUS_POTATO) || s.is(Items.PUFFERFISH);
    }

    private static float giftValue(ItemStack s) {
        if (s.is(Items.DIAMOND) || s.is(Items.EMERALD) || s.is(Items.NETHERITE_INGOT) || s.is(Items.ENCHANTED_GOLDEN_APPLE)) return 1;
        if (s.is(Items.GOLD_INGOT) || s.is(Items.AMETHYST_SHARD) || s.is(Items.POPPY) || s.is(Items.DANDELION)
                || s.is(Items.CORNFLOWER) || s.is(Items.LILY_OF_THE_VALLEY) || s.is(Items.CAKE)) return 0.6F;
        if (isTasty(s)) return 0.4F;
        return s.getRarity().ordinal() * 0.3F + 0.05F;
    }

    static boolean isGross(LivingEntity e) {
        return e instanceof Zombie || e instanceof Husk || e instanceof Drowned || e instanceof Slime;
    }

    // ---- Sync, files ----

    /** The emotions in tenths, 4 bits each, for the synced data. */
    public int packEmotions() {
        int packed = 0;
        for (Emotion e : Emotion.values()) packed |= Math.round(Emotion.tenth(get(e)) * 10) << (e.ordinal() * 4);
        return packed;
    }

    public static float unpack(int packed, Emotion e) {
        return ((packed >> (e.ordinal() * 4)) & 0xF) / 10.0F;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.add("traits", personality.toJson());
        JsonObject em = new JsonObject();
        for (Emotion e : Emotion.values()) em.addProperty(e.id(), Emotion.tenth(emotions.get(e)));
        o.add("emotions", em);
        o.addProperty("adrenaline", Emotion.tenth(adrenaline));
        o.addProperty("hurt_memory", Math.round(hurtMemory * 100) / 100.0F);
        return o;
    }

    public void readJson(JsonObject o) {
        if (o.has("traits") && o.get("traits").isJsonObject()) personality = Personality.fromJson(o.getAsJsonObject("traits"), girl.getRandom());
        if (o.has("emotions") && o.get("emotions").isJsonObject()) {
            JsonObject em = o.getAsJsonObject("emotions");
            for (Emotion e : Emotion.values()) if (em.has(e.id())) emotions.put(e, Mth.clamp(em.get(e.id()).getAsFloat(), 0, 1));
        }
        if (o.has("adrenaline")) adrenaline = Mth.clamp(o.get("adrenaline").getAsFloat(), 0, 1);
        if (o.has("hurt_memory")) hurtMemory = Math.max(0, o.get("hurt_memory").getAsFloat());
    }

    public void save(CompoundTag tag) {
        tag.putString("psyche", toJson().toString());
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("psyche")) return;
        try {
            readJson(com.google.gson.JsonParser.parseString(tag.getString("psyche")).getAsJsonObject());
        } catch (RuntimeException ignored) {
            // a broken entry: she keeps the character she rolled
        }
    }

    /** Her situation as she sees it, every update. */
    public static final class Situation {
        public float threat;
        public double odds = 10;
        public boolean hunted, apex, unseenThreats, burning, falling, lavaNear;
        public float health = 1, hunger;
        public boolean safe, idle, sheltered, raining, thunder, sunny, nightOutside;
        public boolean ownerNear, ownerOnline, ownerBusy, ownerHurt;
        public long ignoredFor;
        public boolean music;
        public float grossNear;
        public int watchersWhileNude;

        static Situation of(AbstractGirlEntity girl, Psyche psyche) {
            Situation s = new Situation();
            SurvivalBrain survival = girl.survival();
            Threats threats = survival.threats();
            var pos = girl.position();
            for (Threats.Threat t : threats.all()) {
                double d = t.distanceTo(pos);
                if (t.hunting || d < 10) {
                    float w = t.kind == Threats.Kind.APEX ? 1 : t.kind == Threats.Kind.EXPLOSIVE ? 0.5F : 0.25F;
                    s.threat += (float) (w * Mth.clamp(1 - d / 24, 0.2, 1));
                }
                if (t.hunting) s.hunted = true;
                if (t.kind == Threats.Kind.APEX && (t.hunting || d < 20)) s.apex = true;
                if (!t.visible) s.unseenThreats = true;
            }
            s.threat = Math.min(1, s.threat);
            if (s.threat > 0) s.odds = survival.fightOdds();
            s.burning = girl.isOnFire();
            s.falling = girl.fallDistance > 4;
            s.lavaNear = girl.level().getBlockStates(girl.getBoundingBox().inflate(2)).anyMatch(b -> b.getFluidState().is(net.minecraft.tags.FluidTags.LAVA));
            s.health = girl.getHealth() / girl.getMaxHealth();
            s.hunger = 1 - girl.getFood() / (float) org.velvet.entity.girl.GirlNeeds.MAX_FOOD;
            s.safe = !girl.isInDanger() && threats.isEmpty() && survival.safeLongEnough();
            s.idle = !girl.getCurrentAction().isBusy() && girl.getNavigation().isDone();
            BlockPos head = BlockPos.containing(girl.getEyePosition());
            s.sheltered = !girl.level().canSeeSky(head);
            s.raining = girl.level().isRaining();
            s.thunder = girl.level().isThundering();
            boolean day = girl.level().isDay();
            s.sunny = day && !s.raining;
            s.nightOutside = !day && !s.sheltered && girl.level().getMaxLocalRawBrightness(head) < 8;
            Player owner = girl.getOwnerUUID() == null ? null : girl.level().getPlayerByUUID(girl.getOwnerUUID());
            s.ownerOnline = owner != null;
            if (owner != null) {
                double d = owner.distanceTo(girl);
                s.ownerNear = d < 24;
                s.ignoredFor = girl.level().getGameTime() - psyche.lastAttention;
                s.ownerBusy = s.ownerNear && d > 4 && s.ignoredFor > 20 * 60 * 2;
                s.ownerHurt = owner.getHealth() < owner.getMaxHealth() * 0.4F;
            }
            s.music = psyche.musicNearby();
            for (LivingEntity e : girl.level().getEntitiesOfClass(LivingEntity.class, girl.getBoundingBox().inflate(4), e -> e != girl && e.isAlive())) {
                if (isGross(e)) s.grossNear += 0.5F;
            }
            s.grossNear = Math.min(1, s.grossNear);
            if (girl.getModelVariant() == AbstractGirlEntity.VARIANT_NUDE && girl.tickCount % 20 == 0) {
                for (Player p : girl.level().players()) {
                    if (p.isSpectator() || p.distanceTo(girl) > 10 || !p.hasLineOfSight(girl)) continue;
                    boolean owns = p.getUUID().equals(girl.getOwnerUUID()) || p.getUUID().equals(girl.getPartnerUUID());
                    if (!owns || psyche.personality.get(Trait.SHYNESS) > 0.75F) s.watchersWhileNude++;
                }
                psyche.lastWatchers = s.watchersWhileNude;
            } else {
                s.watchersWhileNude = psyche.lastWatchers;
            }
            return s;
        }
    }

    private int lastWatchers;

    /** Everything that goes into it, for the debug editor. */
    public Map<String, Object> debug() {
        return Map.of("pending_joy", pendingJoy, "hurt_memory", hurtMemory, "emote", emote.name());
    }

    /** For other code that kills things for her (her fights): the victim. */
    public static void onKilled(AbstractGirlEntity girl, LivingEntity victim) {
        girl.psyche().onKill(victim);
    }

    static boolean isPlayer(@Nullable LivingEntity e) {
        return e instanceof ServerPlayer;
    }

    static boolean isMob(@Nullable LivingEntity e) {
        return e instanceof Mob;
    }
}

package org.velvet.entity.girl.brain;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.velvet.Config;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlBehavior;
import org.velvet.entity.girl.GirlNeeds;
import org.velvet.entity.girl.psyche.Emotion;
import org.velvet.entity.girl.psyche.Psyche;
import org.velvet.entity.girl.survival.Threats;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Her words and, with a language model, most of her decisions; server side.
 * <p>
 * With the internal brain she answers what starts with her name (or @name): a few orders and stock lines,
 * and how she feels when asked. With a language model she's a companion to talk to: she hears everything
 * her owner writes in chat (and what players near her say), answers her owner every time, comments on
 * advancements, deaths and who comes and goes now and then, and every minute or so, when nothing's going on,
 * the model decides what she does next. Every request tells the model who she is (her persona and her
 * character), how she feels, where she is and what's around her, what happened lately, and, if her owner's
 * game can render it, shows it what she sees. Her owner is marked as such, so the model knows whose orders
 * count.
 * <p>
 * What the model can't do is fight for her: in danger (under adrenaline) she's on her own, the models are
 * far too slow for that, and she doesn't spend a thought on chatting.
 */
public final class GirlBrain {

    private static final int HISTORY = 24;
    private static final int MAX_MESSAGE = 256;
    /** Actions a language model may take by putting them on a line of their own. */
    private static final List<String> ACTIONS = List.of("/follow", "/stay", "/home", "/fight", "/flee", "/shelter",
            "/mine", "/gather", "/eat", "/equip", "/wander", "/idle");
    /** How long an order to fight, run or hide stands. */
    private static final int ORDER_TICKS = 20 * 30;
    private static final double OWNER_NEAR = 64;
    private static final int QUEUE = 6;

    /** Who a message comes from, as the model is told. */
    enum Source { OWNER, PLAYER, EVENT, SELF }

    private record Pending(String line, Source source) {}

    private final AbstractGirlEntity girl;
    private final Deque<AiClient.Message> history = new ArrayDeque<>();
    private final Deque<Pending> queue = new ArrayDeque<>();
    private boolean waiting;
    private long lastRequest;
    private long lastComment = -100000;
    private long nextThought;

    public GirlBrain(AbstractGirlEntity girl) {
        this.girl = girl;
    }

    private boolean external() {
        return girl.behavior().provider.isExternal() && Config.AI_ENABLED.get();
    }

    public void tick() {
        if (!external() || girl.tickCount % 20 != 0) return;
        long now = girl.level().getGameTime();
        // an answer that never came (a request that broke on the way) doesn't keep her quiet forever
        if (waiting && now - lastRequest > (Config.AI_TIMEOUT_SECONDS.get() + 20) * 20L) waiting = false;
        if (!waiting && !queue.isEmpty() && now - lastRequest >= Config.AI_COOLDOWN_SECONDS.get() * 20L) {
            send(queue.pollFirst());
            return;
        }
        // nothing going on: the model decides what she does next
        GirlBehavior b = girl.behavior();
        if (!b.autonomy || waiting || now < nextThought || girl.getCurrentAction().isBusy() || girl.psyche().survivalOnly()
                || org.velvet.entity.girl.psyche.CrisisGuard.crisis()) return;
        Player owner = owner();
        if (owner == null || owner.distanceTo(girl) > OWNER_NEAR) return;
        boolean first = nextThought == 0;
        nextThought = now + Config.AI_AUTONOMY_SECONDS.get() * 20L;
        if (first) return; // not the moment she's loaded
        enqueue(new Pending("[self] Some time has passed. Decide what you do now (one action line), and say something only if"
                + " you really have a reason to; otherwise answer with the action alone, or /quiet.", Source.SELF));
    }

    /** Something happened soon worth a fresh decision (her owner came back, it got dark): think sooner. */
    public void thinkSoon() {
        nextThought = Math.min(nextThought, girl.level().getGameTime() + 60);
    }

    // ---- What she hears ----

    /**
     * A player said {@code text} in chat; {@code addressed} if it started with her name or mentioned her.
     * Messages not addressed to her only come here when she listens to everything (a language model).
     */
    public void hear(ServerPlayer player, String text, boolean addressed) {
        text = text.length() > MAX_MESSAGE ? text.substring(0, MAX_MESSAGE) : text;
        boolean fromOwner = player.getUUID().equals(girl.getOwnerUUID());
        float warmth = Sentiment.of(text);
        if (warmth != 0) girl.psyche().onWords(warmth, fromOwner);
        else girl.psyche().attention(fromOwner);
        if (!external()) {
            if (addressed) internalReply(player, text, fromOwner);
            return;
        }
        if (girl.psyche().survivalOnly()) {
            // no time for talking now
            if (fromOwner && addressed) GirlChat.say(girl, Component.translatable("chat.velvet.ai.not_now"));
            return;
        }
        String who = player.getGameProfile().getName();
        Source source = fromOwner ? Source.OWNER : Source.PLAYER;
        String line = (fromOwner ? "[owner] " : "[player] ") + who + (addressed ? " (to you)" : "") + ": " + text;
        enqueue(new Pending(line, source));
    }

    /**
     * Something happened on the server she might say something about: an advancement, a death, someone
     * coming or going. {@code important}: about her owner. Not every time, so the chat isn't flooded.
     */
    public void hearEvent(String event, boolean important) {
        if (!external() || !GirlChat.allowed(girl, GirlChat.Kind.EVENT) || girl.psyche().survivalOnly()) return;
        long now = girl.level().getGameTime();
        if (!important && now - lastComment < Config.AI_EVENT_COOLDOWN_SECONDS.get() * 20L) return;
        if (girl.getRandom().nextFloat() > (important ? 0.7F : 0.3F)) return;
        lastComment = now;
        enqueue(new Pending("[event] " + event + " (comment on it if you like, in one short line, or answer /quiet)", Source.EVENT));
    }

    private void enqueue(Pending p) {
        long now = girl.level().getGameTime();
        if (!waiting && now - lastRequest >= Config.AI_COOLDOWN_SECONDS.get() * 20L) {
            send(p);
            return;
        }
        // waiting for an answer: in line; her owner's messages are never dropped
        queue.addLast(p);
        while (queue.size() > QUEUE) {
            Pending drop = null;
            for (Pending q : queue) {
                if (q.source != Source.OWNER) {
                    drop = q;
                    break;
                }
            }
            queue.remove(drop != null ? drop : queue.peekFirst());
        }
    }

    // ---- Talking to the model ----

    private void send(@Nullable Pending p) {
        if (p == null) return;
        GirlBehavior b = girl.behavior();
        MinecraftServer server = girl.getServer();
        if (server == null) return;
        UUID owner = girl.getOwnerUUID();
        String key = owner != null ? AiKeys.get(server).key(owner, b.provider) : null;
        if (b.provider.needsKey && key == null) {
            if (p.source == Source.OWNER) {
                GirlChat.tellOwner(girl, Component.translatable("chat.velvet.ai.no_key", girl.getName(), b.provider.title)
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }
        String base = b.provider.local && !b.endpoint.isBlank() ? b.endpoint.trim() : b.provider.baseUrl;
        if (base.isBlank()) {
            GirlChat.tellOwner(girl, Component.translatable("chat.velvet.ai.no_endpoint", girl.getName()).withStyle(ChatFormatting.RED));
            return;
        }
        String url = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String model = b.model.isBlank() ? b.provider.defaultModel() : b.model;
        remember(AiClient.Message.user(p.line));
        waiting = true;
        lastRequest = girl.level().getGameTime();
        try {
            request(p, b, server, url, key, model);
        } catch (RuntimeException e) {
            waiting = false;
            fail(p, b, e);
        }
    }

    private void fail(Pending p, GirlBehavior b, Throwable error) {
        org.velvet.Velvet.LOGGER.warn("{}'s {} request failed: {}", girl.getName().getString(), b.provider.title, AiClient.describe(error));
        if (p.source == Source.OWNER) {
            GirlChat.tellOwner(girl, Component.translatable("chat.velvet.ai.error", girl.getName(),
                    b.provider.title, AiClient.describe(error)).withStyle(ChatFormatting.RED));
        }
    }

    private void request(Pending p, GirlBehavior b, MinecraftServer server, String url, @Nullable String key, String model) {
        // what she sees, if her owner's game can show it (only for this turn: pictures are big)
        CompletableFuture<String> eyes = b.vision ? EyesService.request(girl) : CompletableFuture.completedFuture(null);
        String system = systemPrompt();
        eyes.whenComplete((jpeg, ignored) -> server.execute(() -> {
            List<AiClient.Message> messages = new ArrayList<>(history);
            if (jpeg != null && !messages.isEmpty()) {
                AiClient.Message last = messages.removeLast();
                messages.add(last.withImage(jpeg));
            }
            try {
                AiClient.chat(b.provider, url, key, model, system, messages, Config.AI_MAX_TOKENS.get())
                        .whenComplete((reply, error) -> server.execute(() -> {
                            waiting = false;
                            if (!girl.isAlive()) return;
                            if (error != null) {
                                fail(p, b, error);
                            } else {
                                answer(reply, p.source);
                            }
                        }));
            } catch (RuntimeException e) {
                waiting = false;
                fail(p, b, e);
            }
        }));
    }

    /**
     * The model's answer: orders (only her owner's, or her own decisions), how she feels about it (/feel), and
     * what she says (nothing for /quiet).
     */
    private void answer(String reply, Source source) {
        StringBuilder said = new StringBuilder();
        boolean mayAct = source == Source.OWNER || source == Source.SELF;
        for (String line : reply.split("\n")) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith("/feel")) {
                feel(lower.substring(5));
                continue;
            }
            if (lower.equals("/quiet") || lower.equals("/silent")) continue;
            if (ACTIONS.contains(lower)) {
                if (mayAct) act(lower);
                continue;
            }
            if (trimmed.isEmpty()) continue;
            if (!said.isEmpty()) said.append(' ');
            said.append(trimmed);
        }
        remember(AiClient.Message.assistant(reply));
        String text = said.toString();
        if (text.length() > 512) text = text.substring(0, 512) + "…";
        if (!text.isEmpty() && GirlChat.allowed(girl, kindOf(source))) {
            GirlChat.say(girl, Component.literal(text), kindOf(source));
            VoiceBridge.speak(girl, text);
        }
    }

    private static GirlChat.Kind kindOf(Source source) {
        return switch (source) {
            case OWNER, PLAYER -> GirlChat.Kind.ANSWER;
            case EVENT -> GirlChat.Kind.EVENT;
            case SELF -> GirlChat.Kind.IDLE;
        };
    }

    /** "/feel joy+0.2 sadness-0.1": the model says how what happened makes her feel. */
    private void feel(String spec) {
        for (String part : spec.trim().split("[\\s,]+")) {
            int sign = Math.max(part.indexOf('+'), part.indexOf('-'));
            if (sign <= 0) continue;
            Emotion e = null;
            for (Emotion candidate : Emotion.values()) if (candidate.id().equals(part.substring(0, sign))) e = candidate;
            if (e == null) continue;
            try {
                float delta = Float.parseFloat(part.substring(sign));
                girl.psyche().set(e, girl.psyche().get(e) + Math.max(-0.5F, Math.min(0.5F, delta)));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void act(String action) {
        switch (action) {
            case "/follow" -> {
                girl.setFollowing(true);
                girl.setShouldBeAtTarget(false);
            }
            case "/stay", "/idle" -> girl.setFollowing(false);
            case "/wander" -> {
                girl.setFollowing(false);
                girl.setShouldBeAtTarget(false);
            }
            case "/fight" -> girl.survival().order(org.velvet.entity.girl.survival.SurvivalBrain.Tactic.FIGHT, ORDER_TICKS);
            case "/flee" -> girl.survival().order(org.velvet.entity.girl.survival.SurvivalBrain.Tactic.FLEE, ORDER_TICKS);
            case "/shelter" -> girl.survival().order(org.velvet.entity.girl.survival.SurvivalBrain.Tactic.SHELTER, ORDER_TICKS * 2);
            case "/mine", "/gather" -> girl.orderMining(action.equals("/mine"), ORDER_TICKS * 4);
            case "/eat" -> girl.needs().eatSoon();
            case "/equip" -> org.velvet.entity.girl.mind.Gear.prepareForFight(girl);
            case "/home" -> {
                if (girl.getHomePos() != null) {
                    girl.setFollowing(false);
                    girl.setShouldBeAtTarget(true);
                    girl.setTargetVec(Vec3.atCenterOf(girl.getHomePos()));
                }
            }
            default -> {}
        }
    }

    private void remember(AiClient.Message message) {
        // the services want turns to alternate: lines that come in while she's answering join the last one
        AiClient.Message last = history.peekLast();
        if (last != null && last.role().equals(message.role())) {
            history.removeLast();
            message = new AiClient.Message(message.role(), last.content() + "\n" + message.content());
        }
        history.addLast(message);
        while (history.size() > HISTORY) history.removeFirst();
        // the conversation has to start with a user turn for the APIs
        while (!history.isEmpty() && !history.getFirst().role().equals("user")) history.removeFirst();
    }

    @Nullable
    private Player owner() {
        UUID id = girl.getOwnerUUID();
        return id == null ? null : girl.level().getPlayerByUUID(id);
    }

    // ---- What the model is told ----

    private String systemPrompt() {
        GirlBehavior b = girl.behavior();
        Psyche psyche = girl.psyche();
        StringBuilder s = new StringBuilder();
        s.append("You are ").append(girl.getName().getString())
                .append(", a young woman living in a Minecraft world, a companion of your owner. You are not an assistant: you")
                .append(" are a person with your own character and moods, and you talk in the game chat like one: one to three")
                .append(" short sentences, in the language of whoever you answer (your owner's if in doubt), no markdown, no")
                .append(" emotes in asterisks. Joke, tease, be glad, grumble, as your character and mood make you.\n");
        if (!b.persona.isBlank()) s.append("Who you are: ").append(b.persona.trim()).append('\n');
        if (!b.talk) {
            s.append("IMPORTANT: you can't speak or write at all right now; nobody would hear a word. Never answer with words:")
                    .append(" answer only with action lines and /feel lines, or /quiet. You still understand everything.\n");
        } else if (b.chattiness <= 0) {
            s.append("You only answer when spoken to; on your own you stay quiet.\n");
        }
        s.append("Your character (0..1): ").append(psyche.personality().describe(8)).append('\n');
        s.append("\nHow you feel right now: ").append(psyche.mood().describe()).append(" (");
        for (Emotion e : Emotion.values()) {
            if (e.ordinal() > 0) s.append(", ");
            s.append(e.id()).append(' ').append(Emotion.tenth(psyche.get(e)));
        }
        s.append(')');
        if (psyche.adrenaline() >= 0.1F) s.append(", adrenaline ").append(Emotion.tenth(psyche.adrenaline()));
        s.append(". Let it show in how you talk; when you're asked how you are, answer truthfully from this.\n");

        s.append("\nRight now:\n");
        BlockPos pos = girl.blockPosition();
        String biome = girl.level().getBiome(pos).unwrapKey().map(k -> k.location().getPath().replace('_', ' ')).orElse("somewhere");
        s.append("- at ").append(pos.getX()).append(' ').append(pos.getY()).append(' ').append(pos.getZ()).append(" in the ")
                .append(girl.level().dimension().location().getPath().replace('_', ' ')).append(", ").append(biome).append('\n');
        long time = girl.level().getDayTime() % 24000;
        int hour = (int) ((time / 1000 + 6) % 24), minute = (int) (time % 1000 * 60 / 1000);
        s.append("- ").append(String.format(Locale.ROOT, "%02d:%02d", hour, minute)).append(girl.level().isNight() ? ", night" : ", day")
                .append(girl.level().isThundering() ? ", thunderstorm" : girl.level().isRaining() ? ", raining" : ", clear sky")
                .append(girl.level().canSeeSky(pos.above()) ? ", outside" : ", under a roof").append('\n');
        s.append("- health ").append(Math.round(girl.getHealth())).append('/').append(Math.round(girl.getMaxHealth()))
                .append(", food ").append(girl.getFood()).append('/').append(GirlNeeds.MAX_FOOD).append('\n');
        s.append("- ").append(girl.getCurrentAction().isBusy() ? "busy with your partner"
                : girl.isFollowing() ? "following your owner" : girl.shouldBeAtTarget() ? "on your way home" : "on your own");
        if (girl.getHomePos() != null) s.append(", home is ").append(Math.round(Math.sqrt(girl.getHomePos().distSqr(pos)))).append(" blocks away");
        s.append('\n');
        List<String> people = new ArrayList<>();
        for (Player p : girl.level().players()) {
            double d = p.distanceTo(girl);
            if (d > 48 || p.isSpectator()) continue;
            boolean isOwner = p.getUUID().equals(girl.getOwnerUUID());
            people.add(p.getGameProfile().getName() + (isOwner ? " (your owner)" : "") + " " + Math.round(d) + " blocks away"
                    + (p.getHealth() < p.getMaxHealth() * 0.4F ? ", badly hurt" : ""));
        }
        Player owner = owner();
        if (owner == null) s.append("- your owner isn't here\n");
        if (!people.isEmpty()) s.append("- people near you: ").append(String.join("; ", people)).append('\n');
        List<Threats.Threat> threats = girl.survival().threats().all();
        if (!threats.isEmpty()) {
            s.append("- danger you know of: ");
            for (int i = 0; i < Math.min(4, threats.size()); i++) {
                Threats.Threat t = threats.get(i);
                if (i > 0) s.append(", ");
                s.append(t.entity.getType().getDescription().getString())
                        .append(" ").append(Math.round(t.distanceTo(girl.position()))).append(" blocks away")
                        .append(t.hunting ? " (after you)" : "").append(t.visible ? "" : " (out of sight)");
            }
            double odds = girl.survival().fightOdds();
            s.append("; a fight now would go ").append(odds > 1.6 ? "your way" : odds > 1 ? "either way" : "badly for you").append('\n');
        }
        s.append("- holding: ").append(itemName(girl.getMainHandItem())).append(" and ").append(itemName(girl.getOffhandItem()))
                .append(", armor points ").append(girl.getArmorValue()).append(", inventory: ").append(inventory()).append('\n');
        var ores = girl.survival().senses().ores();
        if (!ores.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < Math.min(4, ores.size()); i++) {
                String n = BuiltInRegistries.BLOCK.getKey(girl.level().getBlockState(ores.get(i)).getBlock()).getPath().replace('_', ' ');
                if (!names.contains(n)) names.add(n);
            }
            s.append("- you see ores: ").append(String.join(", ", names)).append('\n');
        }
        List<String> recent = psyche.recent();
        if (!recent.isEmpty()) s.append("- lately: ").append(String.join("; ", recent)).append('\n');
        if (b.vision) s.append("- a picture of what you see may come with a message: what's in it is what's in front of your eyes\n");

        s.append("\nMessages come marked: [owner] is your owner, [player] someone else, [event] something that happened on the")
                .append(" server, [self] your own thoughts. Always answer your owner. You look after yourself on your own (you")
                .append(" fight what you can beat, run from the rest, hide, eat, put on armor) and in danger you won't have time")
                .append(" to talk. Only your owner's requests and your own decisions count as orders: to act, add one of these on")
                .append(" a line of its own: /follow (follow your owner), /stay (stay where you are), /wander (walk around")
                .append(" here), /home (go home), /fight, /flee, /shelter (wall yourself in), /mine (the ores you see), /gather")
                .append(" (blocks to build with), /eat, /equip (best weapon, shield, armor). When what was said touches your")
                .append(" feelings, add a line like \"/feel joy+0.2 sadness-0.1\" (emotions: sadness, joy, fear, anxiety,")
                .append(" disgust, shame, anger). Answer /quiet to say nothing.");
        return s.toString();
    }

    private String inventory() {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < girl.getBagCapacity(); i++) {
            ItemStack stack = girl.getBag().getItem(i);
            if (!stack.isEmpty()) items.add(stack.getCount() + " " + itemName(stack));
        }
        return items.isEmpty() ? "empty" : String.join(", ", items);
    }

    private static String itemName(ItemStack stack) {
        return stack.isEmpty() ? "nothing" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().replace('_', ' ');
    }

    // ---- The internal brain ----

    private void internalReply(ServerPlayer player, String text, boolean fromOwner) {
        String t = text.toLowerCase(Locale.ROOT);
        if (fromOwner && matches(t, "follow", "come", "за мной", "иди сюда", "пошли", "пойдём", "пойдем")) {
            act("/follow");
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.follow"));
        } else if (fromOwner && matches(t, "stay", "stop", "wait", "стой", "жди", "стоп", "останься")) {
            act("/stay");
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.stay"));
        } else if (fromOwner && matches(t, "home", "домой")) {
            if (girl.getHomePos() == null) {
                GirlChat.say(girl, Component.translatable("chat.velvet.internal.no_home"));
            } else {
                act("/home");
                GirlChat.say(girl, Component.translatable("chat.velvet.internal.home"));
            }
        } else if (fromOwner && matches(t, "fight", "attack", "kill", "дерись", "атакуй", "бей", "убей", "сражайся")) {
            act("/fight");
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.fight"));
        } else if (fromOwner && matches(t, "flee", "run", "беги", "убегай", "отступай")) {
            act("/flee");
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.flee"));
        } else if (fromOwner && matches(t, "hide", "shelter", "спрячься", "укройся", "прячься")) {
            act("/shelter");
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.shelter"));
        } else if (fromOwner && matches(t, "mine", "dig", "копай", "добывай", "руду", "шахтёр")) {
            act("/mine");
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.mine"));
        } else if (fromOwner && matches(t, "gather", "collect", "собери", "набери", "блоки")) {
            act("/gather");
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.gather"));
        } else if (fromOwner && matches(t, "eat", "поешь", "покушай", "ешь")) {
            act("/eat");
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.eat"));
        } else if (fromOwner && matches(t, "equip", "gear up", "экипируйся", "надень", "вооружись", "броню")) {
            act("/equip");
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.equip"));
        } else if (matches(t, "mood", "feel", "настроение", "чувствуешь", "грустишь", "злишься", "боишься")) {
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.mood", girl.psyche().mood().label()));
        } else if (matches(t, "how are you", "how r u", "как дела", "как ты", "как себя")) {
            String state = girl.getHealth() < girl.getMaxHealth() / 2 ? "hurt"
                    : girl.getFood() < 8 ? "hungry" : girl.isInDanger() ? "danger" : null;
            if (state != null) GirlChat.say(girl, Component.translatable("chat.velvet.internal.status." + state));
            else GirlChat.say(girl, Component.translatable("chat.velvet.internal.mood", girl.psyche().mood().label()));
        } else if (matches(t, "hi", "hello", "hey", "привет", "здравствуй", "хай")) {
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.hello", player.getName()));
        } else if (Sentiment.of(text) > 0) {
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.thanks." + girl.getRandom().nextInt(3)));
        } else if (Sentiment.of(text) < 0) {
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.hurt." + girl.getRandom().nextInt(3)));
        } else {
            int n = girl.getRandom().nextInt(4);
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.idle." + n));
        }
    }

    private static boolean matches(String text, String... words) {
        for (String w : words) {
            int i = text.indexOf(w);
            while (i >= 0) {
                boolean start = i == 0 || !Character.isLetter(text.charAt(i - 1));
                int end = i + w.length();
                boolean stop = end >= text.length() || !Character.isLetter(text.charAt(end));
                if (start && stop) return true;
                i = text.indexOf(w, i + 1);
            }
        }
        return false;
    }

    // ---- Saving ----

    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (AiClient.Message m : history) {
            CompoundTag t = new CompoundTag();
            t.putString("role", m.role());
            t.putString("content", m.content());
            list.add(t);
        }
        tag.put("chat_history", list);
    }

    public void load(CompoundTag tag) {
        history.clear();
        for (Tag t : tag.getList("chat_history", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            String role = c.getString("role");
            if (role.equals("user") || role.equals("assistant")) history.addLast(new AiClient.Message(role, c.getString("content")));
        }
        while (history.size() > HISTORY) history.removeFirst();
    }
}

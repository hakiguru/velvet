package org.velvet.entity.girl.brain;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.velvet.Config;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlBehavior;
import org.velvet.entity.girl.GirlNeeds;
import org.velvet.entity.girl.survival.Threats;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What she says when spoken to in chat (her name at the start, or @name), server side. With the internal
 * brain she understands a few orders and answers with stock lines; with a language model the conversation,
 * her persona and what she sees around her go to the model, and she can answer with orders of her own
 * (lines like "/follow"), which only count when her owner was talking. The last messages are kept with her.
 */
public final class GirlBrain {

    private static final int HISTORY = 20;
    private static final int MAX_MESSAGE = 256;
    /** Actions a language model may take by putting them on a line of their own. */
    private static final List<String> ACTIONS = List.of("/follow", "/stay", "/home", "/fight", "/flee", "/shelter",
            "/mine", "/gather", "/eat", "/equip");
    /** How long an order to fight, run or hide stands. */
    private static final int ORDER_TICKS = 20 * 30;

    private final AbstractGirlEntity girl;
    private final Deque<AiClient.Message> history = new ArrayDeque<>();
    private boolean waiting;
    private long lastRequest;

    public GirlBrain(AbstractGirlEntity girl) {
        this.girl = girl;
    }

    public void tick() {}

    /** A player said {@code text} to her. */
    public void hear(ServerPlayer player, String text) {
        text = text.length() > MAX_MESSAGE ? text.substring(0, MAX_MESSAGE) : text;
        boolean fromOwner = player.getUUID().equals(girl.getOwnerUUID());
        GirlBehavior b = girl.behavior();
        if (!b.provider.isExternal() || !Config.AI_ENABLED.get()) {
            internalReply(player, text, fromOwner);
            return;
        }
        long now = girl.level().getGameTime();
        if (waiting || now - lastRequest < Config.AI_COOLDOWN_SECONDS.get() * 20L) {
            player.displayClientMessage(Component.translatable("chat.velvet.ai.busy", girl.getName()), true);
            return;
        }
        String key = null;
        UUID owner = girl.getOwnerUUID();
        MinecraftServer server = girl.getServer();
        if (server == null) return;
        if (owner != null) key = AiKeys.get(server).key(owner, b.provider);
        if (b.provider.needsKey && key == null) {
            GirlChat.tellOwner(girl, Component.translatable("chat.velvet.ai.no_key", girl.getName(), b.provider.title)
                    .withStyle(ChatFormatting.RED));
            internalReply(player, text, fromOwner);
            return;
        }
        String base = b.provider.local && !b.endpoint.isBlank() ? b.endpoint.trim() : b.provider.baseUrl;
        if (base.isBlank()) {
            GirlChat.tellOwner(girl, Component.translatable("chat.velvet.ai.no_endpoint", girl.getName()).withStyle(ChatFormatting.RED));
            return;
        }
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String model = b.model.isBlank() ? b.provider.defaultModel() : b.model;
        String speaker = player.getGameProfile().getName();
        remember(AiClient.Message.user(speaker + (fromOwner ? " (your owner)" : "") + ": " + text));
        waiting = true;
        lastRequest = now;
        List<AiClient.Message> messages = new ArrayList<>(history);
        try {
            AiClient.chat(b.provider, base, key, model, systemPrompt(), messages, Config.AI_MAX_TOKENS.get())
                    .whenComplete((reply, error) -> server.execute(() -> {
                        waiting = false;
                        if (!girl.isAlive()) return;
                        if (error != null) {
                            GirlChat.tellOwner(girl, Component.translatable("chat.velvet.ai.error", girl.getName(),
                                    b.provider.title, AiClient.describe(error)).withStyle(ChatFormatting.RED));
                        } else {
                            answer(reply, fromOwner);
                        }
                    }));
        } catch (RuntimeException e) {
            waiting = false;
            GirlChat.tellOwner(girl, Component.translatable("chat.velvet.ai.error", girl.getName(),
                    b.provider.title, AiClient.describe(e)).withStyle(ChatFormatting.RED));
        }
    }

    /** Says the model's answer, after carrying out the orders in it (only for her owner). */
    private void answer(String reply, boolean fromOwner) {
        StringBuilder said = new StringBuilder();
        for (String line : reply.split("\n")) {
            String trimmed = line.trim();
            String action = trimmed.toLowerCase(Locale.ROOT);
            if (ACTIONS.contains(action)) {
                if (fromOwner) act(action);
                continue;
            }
            if (trimmed.isEmpty()) continue;
            if (!said.isEmpty()) said.append(' ');
            said.append(trimmed);
        }
        remember(AiClient.Message.assistant(reply));
        String text = said.toString();
        if (text.length() > 512) text = text.substring(0, 512) + "…";
        if (!text.isEmpty()) GirlChat.say(girl, Component.literal(text));
    }

    private void act(String action) {
        switch (action) {
            case "/follow" -> {
                girl.setFollowing(true);
                girl.setShouldBeAtTarget(false);
            }
            case "/stay" -> girl.setFollowing(false);
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
        history.addLast(message);
        while (history.size() > HISTORY) history.removeFirst();
        // the conversation has to start with the player for the APIs
        while (!history.isEmpty() && !history.getFirst().role().equals("user")) history.removeFirst();
    }

    // ---- What the model is told ----

    private String systemPrompt() {
        GirlBehavior b = girl.behavior();
        StringBuilder s = new StringBuilder();
        s.append("You are ").append(girl.getName().getString())
                .append(", a companion character in Minecraft. You talk to players in the game chat: answer in one to three short")
                .append(" sentences, in the language the player wrote in, no markdown, no emotes in asterisks.\n");
        if (!b.persona.isBlank()) s.append("Your character: ").append(b.persona.trim()).append('\n');
        s.append("\nRight now:\n");
        s.append("- health ").append(Math.round(girl.getHealth())).append('/').append(Math.round(girl.getMaxHealth()))
                .append(", food ").append(girl.getFood()).append('/').append(GirlNeeds.MAX_FOOD).append('\n');
        s.append("- ").append(girl.isFollowing() ? "following your owner" : "not following anyone").append('\n');
        s.append("- in ").append(girl.level().dimension().location().getPath()).append(", ")
                .append(girl.level().isNight() ? "night" : "day").append('\n');
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
            s.append('\n');
        }
        s.append("- holding: ").append(itemName(girl.getMainHandItem())).append(" and ").append(itemName(girl.getOffhandItem()))
                .append(", armor points ").append(girl.getArmorValue()).append(", inventory: ").append(inventory()).append('\n');
        if (!threats.isEmpty()) {
            double odds = girl.survival().fightOdds();
            s.append("- a fight now would go ").append(odds > 1.6 ? "your way" : odds > 1 ? "either way" : "badly for you").append('\n');
        }
        var ores = girl.survival().senses().ores();
        if (!ores.isEmpty()) {
            s.append("- you see ores: ");
            List<String> names = new ArrayList<>();
            for (int i = 0; i < Math.min(4, ores.size()); i++) {
                String n = BuiltInRegistries.BLOCK.getKey(girl.level().getBlockState(ores.get(i)).getBlock()).getPath().replace('_', ' ');
                if (!names.contains(n)) names.add(n);
            }
            s.append(String.join(", ", names)).append('\n');
        }
        s.append("- ").append(girl.level().getMaxLocalRawBrightness(girl.blockPosition()) < 7 ? "it's dark here" : "it's light here")
                .append(", building blocks with you: ").append(girl.survival().countBuildingBlocks()).append('\n');
        s.append("\nYou look after yourself on your own (you fight what you can beat, run from the rest, hide, eat, put on armor).")
                .append(" Only when your owner asks for it, you can act by adding one of these on a line of its own: ")
                .append("/follow (follow them), /stay (stop following), /home (go home), /fight (fight what's after you),")
                .append(" /flee (run away), /shelter (wall yourself in), /mine (mine the ores you see), /gather (collect blocks to")
                .append(" build with), /eat (eat something), /equip (best weapon, shield and armor on).");
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
        } else if (matches(t, "how are you", "how r u", "как дела", "как ты", "как себя")) {
            String state = girl.getHealth() < girl.getMaxHealth() / 2 ? "hurt"
                    : girl.getFood() < 8 ? "hungry" : girl.isInDanger() ? "danger" : "fine";
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.status." + state));
        } else if (matches(t, "hi", "hello", "hey", "привет", "здравствуй", "хай")) {
            GirlChat.say(girl, Component.translatable("chat.velvet.internal.hello", player.getName()));
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

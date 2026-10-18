package org.velvet.client.gui.editor;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.velvet.client.gui.RangeSlider;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlBehavior;
import org.velvet.entity.girl.GirlNeeds;
import org.velvet.entity.girl.brain.AiProvider;
import org.velvet.menu.GirlEditorMenu;
import org.velvet.network.packet.SaveGirlEditorPacket;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.velvet.client.gui.PanelArt.*;

/**
 * The character tab: her name and how she goes about her day on the left (wandering, picking things up,
 * eating, running from monsters, who hears her in chat and how she follows), and what there is to know about
 * her on the right.
 */
public final class CharacterTab implements EditorTab {

    private record Toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {}

    private Host host;
    private Font font;
    private int x, y, w, h;
    private final GirlEditorMenu.GirlEntry target;
    @Nullable private final AbstractGirlEntity girl;

    public CharacterTab(GirlEditorMenu.GirlEntry target, @Nullable AbstractGirlEntity girl) {
        this.target = target;
        this.girl = girl;
    }

    private EditorState state() {
        return host.state();
    }

    private GirlBehavior behavior() {
        return host.state().behavior;
    }

    @Override
    public void init(Host host, int x, int y, int w, int h) {
        this.host = host;
        this.font = host.font();
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        int colW = w / 2 - 6;

        EditBox nameBox = host.add(new EditBox(font, x, y + 11, Math.min(160, colW), 18, Component.translatable("editor.velvet.name")));
        nameBox.setMaxLength(SaveGirlEditorPacket.MAX_NAME_LENGTH);
        nameBox.setValue(state().name);
        nameBox.setHint(state().preset.displayName().copy().withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setResponder(v -> {
            if (!v.equals(state().name)) state().dirty = true;
            state().name = v;
            state().updatePreview();
        });

        List<Toggle> toggles = List.of(
                new Toggle("wander", () -> behavior().wander, v -> behavior().wander = v),
                new Toggle("pick_up_items", () -> behavior().pickUpItems, v -> behavior().pickUpItems = v),
                new Toggle("auto_eat", () -> behavior().autoEat, v -> behavior().autoEat = v),
                new Toggle("flee_monsters", () -> behavior().fleeMonsters, v -> behavior().fleeMonsters = v));
        int ty = y + 46;
        int step = Mth.clamp((h - 46 - 3 * 18 - 20) / 4, 15, 18);
        for (Toggle t : toggles) {
            host.add(Checkbox.builder(Component.translatable("editor.velvet.ai." + t.key()), font)
                    .pos(x, ty).maxWidth(colW).selected(t.get().getAsBoolean())
                    .tooltip(Tooltip.create(Component.translatable("editor.velvet.ai." + t.key() + ".tooltip")))
                    .onValueChange((cb, v) -> {
                        t.set().accept(v);
                        state().dirty = true;
                    }).build());
            ty += step;
        }
        ty += 2;
        host.add(Button.builder(visibilityLabel(), b -> {
            behavior().chatVisibility = behavior().chatVisibility == GirlBehavior.ChatVisibility.PRIVATE
                    ? GirlBehavior.ChatVisibility.PUBLIC : GirlBehavior.ChatVisibility.PRIVATE;
            b.setMessage(visibilityLabel());
            state().dirty = true;
        }).bounds(x, ty, colW, 16).tooltip(Tooltip.create(Component.translatable("editor.velvet.ai.chat.tooltip"))).build());
        ty += 18;
        host.add(new RangeSlider(x, ty, colW, 16, GirlBehavior.MIN_STOP, GirlBehavior.MAX_STOP, 0.5,
                () -> behavior().followStopDistance, v -> { behavior().followStopDistance = v; state().dirty = true; },
                v -> Component.translatable("editor.velvet.ai.stop_distance", blocks(v))));
        host.add(new RangeSlider(x, ty + 18, colW, 16, GirlBehavior.MIN_RUN, GirlBehavior.MAX_RUN, 0.5,
                () -> behavior().followRunDistance, v -> { behavior().followRunDistance = v; state().dirty = true; },
                v -> Component.translatable("editor.velvet.ai.run_distance", blocks(v))));
        host.add(new RangeSlider(x, ty + 36, colW, 16, GirlBehavior.MIN_TELEPORT, GirlBehavior.MAX_TELEPORT, 1,
                () -> behavior().followTeleportDistance, v -> { behavior().followTeleportDistance = v; state().dirty = true; },
                v -> Component.translatable("editor.velvet.ai.teleport_distance", blocks(v))));
    }

    private Component visibilityLabel() {
        return Component.translatable("editor.velvet.ai.chat",
                Component.translatable("editor.velvet.ai.chat." + behavior().chatVisibility.id()));
    }

    private static String blocks(double v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.drawString(font, Component.translatable("editor.velvet.name"), x, y + 1, LABEL, false);
        g.drawString(font, Component.translatable("editor.velvet.ai.section.behavior"), x, y + 35, LABEL, false);

        int colW = w / 2 - 6;
        int ix = x + w / 2 + 4;
        int iy = y + 2;
        var a = target.appearance();
        boolean live = girl != null;
        float health = live ? girl.getHealth() : a.getFloat("health");
        float maxHealth = live ? girl.getMaxHealth() : a.getFloat("max_health");
        int food = live ? girl.getFood() : a.getInt("food");
        BlockPos home = live ? girl.getHomePos() : a.contains("home") ? BlockPos.of(a.getLong("home")) : null;
        BlockPos pos = live ? girl.blockPosition() : target.pos();
        line(g, ix, iy, "editor.velvet.preset", state().preset.displayName());
        line(g, ix, iy + 12, "editor.velvet.owner", ownerName());
        String where = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        if (!target.dimension().getPath().equals("overworld")) where = target.dimension().getPath() + ": " + where;
        line(g, ix, iy + 24, "editor.velvet.position", Component.literal(where));
        line(g, ix, iy + 36, "editor.velvet.home", home == null
                ? Component.translatable("editor.velvet.none") : Component.literal(home.toShortString()));
        line(g, ix, iy + 48, "editor.velvet.health", Component.literal(Math.round(health) + " / " + Math.round(maxHealth)));
        line(g, ix, iy + 60, "editor.velvet.food", Component.literal(food + " / " + GirlNeeds.MAX_FOOD));
        line(g, ix, iy + 72, "editor.velvet.brain", behavior().provider == AiProvider.INTERNAL
                ? Component.translatable("editor.velvet.ai.internal")
                : Component.literal(behavior().provider.title + (behavior().model.isBlank() ? "" : " · " + behavior().model)));
        g.drawString(font, font.plainSubstrByWidth(target.id().toString(), colW), ix, iy + 88, 0xFF8A8A8A, false);
        int hy = iy + 104;
        Component name = state().name.isEmpty() ? state().preset.displayName() : Component.literal(state().name);
        for (var l : font.split(Component.translatable("editor.velvet.chat_hint", name), colW)) {
            if (hy + 9 > y + h) break;
            g.drawString(font, l, ix, hy, MUTED, false);
            hy += 10;
        }
    }

    private void line(GuiGraphics g, int lx, int ly, String key, Component value) {
        g.drawString(font, Component.translatable(key), lx, ly, MUTED, false);
        int labelW = 58;
        g.drawString(font, font.plainSubstrByWidth(value.getString(), w / 2 - 6 - labelW), lx + labelW, ly, LABEL, false);
    }

    private Component ownerName() {
        Minecraft mc = Minecraft.getInstance();
        UUID owner = girl != null ? girl.getOwnerUUID() : mc.player != null ? mc.player.getUUID() : null;
        if (owner == null) return Component.translatable("editor.velvet.none");
        if (mc.player != null && owner.equals(mc.player.getUUID())) {
            return Component.translatable("editor.velvet.owner_you", mc.player.getName());
        }
        PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(owner) : null;
        return Component.literal(info != null ? info.getProfile().getName() : owner.toString());
    }
}

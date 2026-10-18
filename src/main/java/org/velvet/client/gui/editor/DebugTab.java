package org.velvet.client.gui.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.velvet.client.gui.RangeSlider;
import org.velvet.entity.girl.GirlSkill;
import org.velvet.entity.girl.NpcSettings;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.velvet.client.gui.PanelArt.*;

/**
 * The debug tool's own tabs (creative only), all kept in her settings file
 * ({@code <world>/velvet/npcs/<id>.json}) or on her:
 * <ul>
 *   <li>INSTINCT: each part of her survival instinct on or off.</li>
 *   <li>CHARACTER: her character, to come.</li>
 *   <li>LEVELS: the skill levels she has (starting levels for a new one).</li>
 *   <li>FLAGS: AI off (she stands like a mob without AI), damage from players, invulnerable.</li>
 * </ul>
 */
public final class DebugTab implements EditorTab {

    public enum Page { INSTINCT, CHARACTER, LEVELS, FLAGS }

    private record Toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {}

    private final Page page;
    private Host host;
    private Font font;
    private int x, y, w, h;

    public DebugTab(Page page) {
        this.page = page;
    }

    private NpcSettings settings() {
        return host.state().settings;
    }

    @Override
    public void init(Host host, int x, int y, int w, int h) {
        this.host = host;
        this.font = host.font();
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        switch (page) {
            case INSTINCT -> toggles(List.of(
                    new Toggle("survival", () -> settings().survival, v -> settings().survival = v),
                    new Toggle("fight_back", () -> settings().fightBack, v -> settings().fightBack = v),
                    new Toggle("use_shield", () -> settings().useShield, v -> settings().useShield = v),
                    new Toggle("stealth", () -> settings().stealth, v -> settings().stealth = v),
                    new Toggle("shelter", () -> settings().shelter, v -> settings().shelter = v),
                    new Toggle("pillar_up", () -> settings().pillarUp, v -> settings().pillarUp = v),
                    new Toggle("dig_in", () -> settings().digIn, v -> settings().digIn = v),
                    new Toggle("gather_blocks", () -> settings().gatherBlocks, v -> settings().gatherBlocks = v),
                    new Toggle("mine_ores", () -> settings().mineOres, v -> settings().mineOres = v),
                    new Toggle("use_items", () -> settings().useItems, v -> settings().useItems = v),
                    new Toggle("ask_for_help", () -> settings().askForHelp, v -> settings().askForHelp = v)));
            case FLAGS -> toggles(List.of(
                    new Toggle("ai", () -> settings().ai, v -> settings().ai = v),
                    new Toggle("damage_from_players", () -> settings().damageFromPlayers, v -> settings().damageFromPlayers = v),
                    new Toggle("invulnerable", () -> settings().invulnerable, v -> settings().invulnerable = v)));
            case LEVELS -> levels();
            case CHARACTER -> {}
        }
    }

    private void toggles(List<Toggle> toggles) {
        int colW = (w - 6) / 2;
        int rows = (toggles.size() + 1) / 2;
        int step = Mth.clamp((h - 14) / Math.max(1, rows), 16, 20);
        for (int i = 0; i < toggles.size(); i++) {
            Toggle t = toggles.get(i);
            int col = i / rows, row = i % rows;
            host.add(Checkbox.builder(Component.translatable("editor.velvet.debug." + t.key()), font)
                    .pos(x + col * (colW + 6), y + 14 + row * step).maxWidth(colW).selected(t.get().getAsBoolean())
                    .tooltip(Tooltip.create(Component.translatable("editor.velvet.debug." + t.key() + ".tooltip")))
                    .onValueChange((cb, v) -> {
                        t.set().accept(v);
                        host.state().dirty = true;
                    }).build());
        }
    }

    private void levels() {
        int colW = (w - 6) / 2;
        GirlSkill[] skills = GirlSkill.values();
        int rows = (skills.length + 1) / 2;
        int step = Mth.clamp((h - 14) / rows, 17, 22);
        int[] levels = host.state().skillLevels;
        for (int i = 0; i < skills.length; i++) {
            GirlSkill skill = skills[i];
            int col = i / rows, row = i % rows;
            host.add(new RangeSlider(x + col * (colW + 6), y + 14 + row * step, colW, 16, 0, GirlSkill.MAX_LEVEL, 1,
                    () -> levels[skill.ordinal()], v -> {
                        levels[skill.ordinal()] = (int) Math.round(v);
                        host.state().dirty = true;
                    }, v -> Component.translatable("editor.velvet.debug.level", Component.translatable(skill.translationKey()), (int) v)));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.drawString(font, Component.translatable("editor.velvet.debug.page." + page.name().toLowerCase()), x, y + 1, LABEL, false);
        if (page == Page.CHARACTER) {
            int ly = y + 16;
            for (var line : font.split(Component.translatable("editor.velvet.debug.character.soon"), w)) {
                g.drawString(font, line, x, ly, MUTED, false);
                ly += 10;
            }
        }
        Component file = Component.translatable("editor.velvet.debug.file");
        g.drawString(font, font.plainSubstrByWidth(file.getString(), w), x, y + h - 9, 0xFF8A8A8A, false);
    }
}

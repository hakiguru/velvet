package org.velvet.client.gui.editor;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.velvet.Velvet;
import org.velvet.client.gui.RangeSlider;
import org.velvet.client.model.AbstractGirlGeoModel;
import org.velvet.client.render.AbstractGirlRenderer;
import org.velvet.client.skin.SkinCache;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.BoneTweaks;
import org.velvet.skin.GirlSkin;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.velvet.client.gui.PanelArt.*;

/**
 * The model tab, a small Blockbench: the bone tree on the left (click a name to pick it, the arrow to fold it,
 * the eye to hide it), the 3D view in the middle (click a bone to pick it; drag to turn the view, right drag
 * to move it, wheel to zoom; drag the coloured handles to move, turn or scale the picked bone with the tool
 * chosen in the toolbar or with G, R and S), poses to try on the right of the view, and the picked bone's
 * numbers, colour and her texture on the far right. Ctrl+Z / Ctrl+Y undo and redo.
 * <p>
 * The "Scene" section fits the scenes to her preset (see {@link org.velvet.client.scene.SceneFits}): pick a
 * scene and one of its poses, and she plays it with you as the partner; then move, turn or scale her, him or
 * his cock until they meet. Fits are per preset, not per NPC, and kept in config/velvet/scene_fits.json.
 */
public final class ModelTab implements EditorTab {

    private enum Tool { MOVE, ROTATE, SCALE }

    private enum Section { TRANSFORM, COLOR, TEXTURE, SCENE }

    /**
     * Poses to try on, with the animation each one plays (its full name, "" for her standing idle); a header
     * row names the preset the poses under it come from.
     */
    private record Pose(String key, String animation, boolean header) {
        Pose(String key, String animation) {
            this(key, animation, false);
        }
    }

    /** The usual ones first, in this order, with names of their own; the rest of her preset's after them. */
    private static final List<String> KNOWN = List.of("walk", "fastwalk", "run", "backwards_walk", "crouchwalk", "crouchidle",
            "sit", "ride", "downed", "wave_idle", "fortnite", "fhappy", "attack1", "bowcharge", "throwpearl", "fly", "strip", "payment");
    private static final java.util.Set<String> SKIPPED = java.util.Set.of("null", "idle", "eyes", "blink", "new", "vid");

    private static final int[] SWATCHES = {
            0xFFFFFFFF, 0xFFFFD6C2, 0xFFE8B090, 0xFF8D5A3B, 0xFF2B1B14, 0xFFF4E28A,
            0xFFE05050, 0xFFE89AC0, 0xFF9A6AE0, 0xFF5A8AE8, 0xFF58C8A0, 0xFF6A6A6A};

    private static final int LIST_W = 100, GAP = 5, TOOLBAR = 18, ROW = 10;
    /** Narrower than this, the bone tree starts folded away to leave room for the view. */
    private static final int ROOMY = 440;
    private static final int POSES_W = 84, POSE_ROW = 11;
    private static final int VIEW_BACKGROUND = 0xFF2E3138;
    private static final int HANDLE_LENGTH = 30, HANDLE_SIZE = 6;
    private static final int[] AXIS_COLOURS = {0xFFE05050, 0xFF60D060, 0xFF5080F0};
    private static final float DEFAULT_YAW = 25, DEFAULT_PITCH = 15;

    /** Copied bone, kept while the game runs, to paste onto another bone or another girl. */
    @Nullable private static BoneTweaks.Tweak clipboard;
    /** Whether the bone tree shows; null until the player toggles it (then it depends on the room). */
    @Nullable private static Boolean showTree;

    private Host host;
    private Font font;
    private int x, y, w, h;

    // bones
    private record BoneRow(String name, int depth, boolean hasChildren) {}
    private final List<String> order = new ArrayList<>();
    private final java.util.Map<String, Integer> depths = new java.util.HashMap<>();
    private final java.util.Map<String, String> parents = new java.util.HashMap<>();
    private final Set<String> withChildren = new HashSet<>();
    private final Set<String> folded = new HashSet<>();
    private final List<BoneRow> rows = new ArrayList<>();
    @Nullable private String selected;
    private int listScroll;

    // view
    private float yaw = DEFAULT_YAW, pitch = DEFAULT_PITCH, zoom = 1, panX, panY;
    private boolean turntable;
    private Tool tool = Tool.MOVE;
    private final ModelPicker picker = new ModelPicker();
    private int dragButton = -1;
    private boolean dragged;
    private double pressX, pressY;
    private int dragAxis = -1;
    private int poseScroll;
    @Nullable private String pose;
    private List<Pose> poses = List.of();
    @Nullable private org.velvet.entity.girl.GirlType bonesOf;
    private boolean paused;

    // properties
    private Section section = Section.TRANSFORM;
    private final List<AbstractWidget> transformWidgets = new ArrayList<>(), colorWidgets = new ArrayList<>(),
            textureWidgets = new ArrayList<>(), selectionWidgets = new ArrayList<>();
    private final List<RangeSlider> sliders = new ArrayList<>();
    private EditBox hexBox;
    private Checkbox hiddenBox;
    private Button modeButton;
    private Button undoButton, redoButton;
    private final List<Button> toolButtons = new ArrayList<>();
    private List<Path> skinFiles = List.of();
    private int fileScroll;
    @Nullable private Component textureStatus;
    private int textureStatusColour = MUTED;
    // scene fitting
    private final List<AbstractWidget> sceneWidgets = new ArrayList<>();
    private final List<RangeSlider> fitSliders = new ArrayList<>();
    private Button sceneButton, stageButton;
    private final List<Button> fitBoneButtons = new ArrayList<>();
    private int sceneIndex, stageIndex;
    private String fitBone = "body";
    private boolean fitsChanged;
    /** While the widgets are being set to the state: their callbacks mustn't count as edits. */
    private boolean syncing;

    @Override
    public void init(Host host, int x, int y, int w, int h) {
        this.host = host;
        this.font = host.font();
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        transformWidgets.clear();
        colorWidgets.clear();
        textureWidgets.clear();
        selectionWidgets.clear();
        sliders.clear();
        sceneWidgets.clear();
        fitSliders.clear();
        fitBoneButtons.clear();
        toolButtons.clear();
        if (order.isEmpty() || bonesOf != host.state().preset) collectBones();
        int propsW = propsW();

        // toolbar
        int tx = x;
        host.add(Button.builder(Component.literal("☰"), b -> {
                    showTree = !treeShown();
                    host.rebuild();
                }).bounds(tx, y, 16, 16)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.bone_tree"))).build());
        tx += 22;
        for (Tool t : Tool.values()) {
            String letter = switch (t) { case MOVE -> "✥"; case ROTATE -> "⟳"; case SCALE -> "⤢"; };
            Button b = host.add(Button.builder(Component.literal(letter), btn -> setTool(t)).bounds(tx, y, 16, 16)
                    .tooltip(Tooltip.create(Component.translatable("editor.velvet.tool." + t.name().toLowerCase(Locale.ROOT)))).build());
            toolButtons.add(b);
            tx += 17;
        }
        tx += 5;
        undoButton = host.add(Button.builder(Component.literal("↶"), b -> undo()).bounds(tx, y, 16, 16)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.undo"))).build());
        redoButton = host.add(Button.builder(Component.literal("↷"), b -> redo()).bounds(tx + 17, y, 16, 16)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.redo"))).build());
        tx += 39;
        selectionWidgets.add(host.add(Button.builder(Component.literal("⎘"), b -> copyBone()).bounds(tx, y, 16, 16)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.copy"))).build()));
        selectionWidgets.add(host.add(Button.builder(Component.literal("⎗"), b -> pasteBone()).bounds(tx + 17, y, 16, 16)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.paste"))).build()));
        selectionWidgets.add(host.add(Button.builder(Component.literal("⇋"), b -> mirrorBone()).bounds(tx + 34, y, 16, 16)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.mirror"))).build()));
        // views on the right of the toolbar
        int vx = x + w;
        vx -= 17;
        host.add(Button.builder(Component.literal("⟲"), b -> resetView()).bounds(vx, y, 16, 16)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.reset_view"))).build());
        vx -= 17;
        host.add(Button.builder(Component.literal("◎"), b -> turntable = !turntable).bounds(vx, y, 16, 16)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.turntable"))).build());
        String[] presets = {"top", "back", "side", "front"};
        float[][] angles = {{0, 89}, {180, 0}, {90, 0}, {0, 0}};
        for (int i = 0; i < presets.length; i++) {
            float[] a = angles[i];
            vx -= 17;
            host.add(Button.builder(Component.literal(presets[i].substring(0, 1).toUpperCase(Locale.ROOT)), b -> {
                        yaw = a[0];
                        pitch = a[1];
                        turntable = false;
                    }).bounds(vx, y, 16, 16)
                    .tooltip(Tooltip.create(Component.translatable("editor.velvet.view." + presets[i]))).build());
        }

        // properties
        int px = x + w - propsW, py = y + TOOLBAR + 26;
        int sw = propsW;
        // the sections, two by two
        String[] names = {"transform", "color", "texture", "scene"};
        for (int i = 0; i < 4; i++) {
            Section s = Section.values()[i];
            int bw = (sw - 1) / 2;
            host.add(Button.builder(Component.translatable("editor.velvet.section." + names[i]), b -> showSection(s))
                    .bounds(px + i % 2 * (bw + 1), y + TOOLBAR + i / 2 * 12, bw, 12).build());
        }
        int bottom = y + h;
        int step = Mth.clamp((bottom - py - 32) / 9, 11, 15);
        String[] kinds = {"scale", "offset", "rotation"};
        String[] axes = {"X", "Y", "Z"};
        for (int k = 0; k < 3; k++) {
            for (int a = 0; a < 3; a++) {
                int kind = k, axis = a;
                double min = kind == 0 ? BoneTweaks.MIN_SCALE : kind == 1 ? -BoneTweaks.MAX_OFFSET : -BoneTweaks.MAX_ROTATION;
                double max = kind == 0 ? BoneTweaks.MAX_SCALE : kind == 1 ? BoneTweaks.MAX_OFFSET : BoneTweaks.MAX_ROTATION;
                double stepSize = kind == 0 ? 0.05 : kind == 1 ? 0.25 : 5;
                RangeSlider slider = host.add(new RangeSlider(px, py + (k * 3 + a) * step, sw, step - 1, min, max, stepSize,
                        () -> value(kind, axis), v -> setValue(kind, axis, (float) v, "slider" + kind + axis),
                        v -> Component.translatable("editor.velvet." + kinds[kind], axes[axis], format(kind, v))));
                sliders.add(slider);
                transformWidgets.add(slider);
            }
        }
        int by = py + 9 * step + 2;
        int half = (sw - 2) / 2;
        transformWidgets.add(host.add(Button.builder(Component.translatable("editor.velvet.reset_bone"), b -> resetBone())
                .bounds(px, by, sw, 14).build()));
        transformWidgets.add(host.add(Button.builder(Component.translatable("editor.velvet.reset_all"), b -> resetAll())
                .bounds(px, by + 15, sw, 14).build()));

        // colour
        for (int i = 0; i < SWATCHES.length; i++) {
            int colour = SWATCHES[i];
            colorWidgets.add(host.add(new Swatch(px + i % 6 * 20, py + i / 6 * 20, colour, () -> setTint(colour))));
        }
        hexBox = host.add(new EditBox(font, px, py + 44, 70, 16, Component.literal("#RRGGBB")));
        hexBox.setMaxLength(7);
        hexBox.setResponder(this::hexTyped);
        colorWidgets.add(hexBox);
        colorWidgets.add(host.add(Button.builder(Component.translatable("editor.velvet.no_tint"), b -> setTint(BoneTweaks.NO_TINT))
                .bounds(px + 74, py + 44, sw - 74, 16).build()));
        hiddenBox = host.add(Checkbox.builder(Component.translatable("editor.velvet.hidden"), font).pos(px, py + 66)
                .onValueChange((cb, v) -> setHidden(v)).build());
        colorWidgets.add(hiddenBox);

        // texture
        modeButton = host.add(Button.builder(Component.empty(), b -> cycleSkinMode()).bounds(px, py, sw, 16).build());
        textureWidgets.add(modeButton);
        textureWidgets.add(host.add(Button.builder(Component.translatable("editor.velvet.skin.browse"), b -> browse())
                .bounds(px, py + 18, sw, 16).build()));
        textureWidgets.add(host.add(Button.builder(Component.translatable("editor.velvet.skin.folder"), b -> openFolder())
                .bounds(px, py + 36, half, 16).build()));
        textureWidgets.add(host.add(Button.builder(Component.translatable("editor.velvet.skin.export"), b -> exportDefault())
                .bounds(px + half + 2, py + 36, half, 16).build()));

        initScene(px, py, sw, step);
        refreshFiles();
        syncWidgets();
    }

    // ---- Scene fitting ----

    private void initScene(int px, int py, int sw, int step) {
        int row = Math.max(12, step);
        sceneWidgets.add(host.add(Button.builder(Component.literal("◀"), b -> pickScene(-1)).bounds(px, py, 14, row - 1).build()));
        sceneButton = host.add(Button.builder(Component.empty(), b -> pickScene(1)).bounds(px + 15, py, sw - 30, row - 1).build());
        sceneWidgets.add(sceneButton);
        sceneWidgets.add(host.add(Button.builder(Component.literal("▶"), b -> pickScene(1)).bounds(px + sw - 14, py, 14, row - 1).build()));
        stageButton = host.add(Button.builder(Component.empty(), b -> pickStage(1)).bounds(px, py + row, sw, row - 1).build());
        sceneWidgets.add(stageButton);
        int bw = (sw - 2) / 3;
        for (int i = 0; i < org.velvet.client.scene.SceneFits.BONES.size(); i++) {
            String bone = org.velvet.client.scene.SceneFits.BONES.get(i);
            Button b = host.add(Button.builder(Component.translatable("editor.velvet.fit.bone." + bone), btn -> {
                fitBone = bone;
                syncWidgets();
            }).bounds(px + i * (bw + 1), py + 2 * row, bw, row - 1).build());
            fitBoneButtons.add(b);
            sceneWidgets.add(b);
        }
        String[] kinds = {"scale", "offset", "rotation"};
        String[] axes = {"X", "Y", "Z"};
        int sy = py + 3 * row + 2;
        int sliderStep = Math.max(10, Math.min(step, (y + h - sy - 2 * 14) / 9));
        for (int k = 0; k < 3; k++) {
            for (int a = 0; a < 3; a++) {
                int kind = k, axis = a;
                double min = kind == 0 ? 0.5 : kind == 1 ? -24 : -180;
                double max = kind == 0 ? 2 : kind == 1 ? 24 : 180;
                double stepSize = kind == 0 ? 0.02 : kind == 1 ? 0.25 : 1;
                RangeSlider slider = host.add(new RangeSlider(px, sy + (k * 3 + a) * sliderStep, sw, sliderStep - 1, min, max, stepSize,
                        () -> fitValue(kind, axis), v -> setFitValue(kind, axis, (float) v),
                        v -> Component.translatable("editor.velvet." + kinds[kind], axes[axis], format(kind, v))));
                fitSliders.add(slider);
                sceneWidgets.add(slider);
            }
        }
        int by = sy + 9 * sliderStep + 1;
        int half = (sw - 2) / 2;
        sceneWidgets.add(host.add(Button.builder(Component.translatable("editor.velvet.fit.reset"), b -> {
            resetFit();
        }).bounds(px, by, half, 14).build()));
        sceneWidgets.add(host.add(Button.builder(Component.translatable("editor.velvet.fit.save"), b -> saveFits())
                .bounds(px + half + 2, by, half, 14)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.fit.save.tip"))).build()));
    }

    private List<org.velvet.entity.girl.scene.SceneType> scenes() {
        return org.velvet.entity.girl.scene.SceneType.of(host.state().preset);
    }

    @Nullable
    private org.velvet.entity.girl.scene.SceneType scene() {
        List<org.velvet.entity.girl.scene.SceneType> list = scenes();
        return list.isEmpty() ? null : list.get(Math.floorMod(sceneIndex, list.size()));
    }

    /** The scene's poses in order: waiting for him, then every stage from the first one on. */
    private List<org.velvet.entity.girl.GirlAction> stages() {
        org.velvet.entity.girl.scene.SceneType scene = scene();
        List<org.velvet.entity.girl.GirlAction> out = new ArrayList<>();
        if (scene == null) return out;
        var flow = scene.flow(host.state().preset);
        if (flow.place().waiting() != null) out.add(flow.place().waiting());
        java.util.List<org.velvet.entity.girl.GirlAction> next = new java.util.LinkedList<>();
        next.add(flow.firstStage());
        while (!next.isEmpty()) {
            org.velvet.entity.girl.GirlAction a = next.removeFirst();
            if (a == null || a == org.velvet.entity.girl.GirlAction.NULL || out.contains(a) || a.animation == null) continue;
            out.add(a);
            next.add(a.next());
            next.add(a.finish());
        }
        return out;
    }

    @Nullable
    private org.velvet.entity.girl.GirlAction stage() {
        List<org.velvet.entity.girl.GirlAction> list = stages();
        return list.isEmpty() ? null : list.get(Math.floorMod(stageIndex, list.size()));
    }

    private void pickScene(int by) {
        saveFitsIfChanged();
        sceneIndex += by;
        // start on its first loop: that's where they meet the longest
        List<org.velvet.entity.girl.GirlAction> list = stages();
        stageIndex = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).isPaced()) {
                stageIndex = i;
                break;
            }
        }
        showScene();
    }

    private void pickStage(int by) {
        stageIndex += by;
        showScene();
    }

    /** The stand-in plays the picked pose of the picked scene, with the player as her partner. */
    private void showScene() {
        AbstractGirlEntity girl = host.state().preview;
        if (girl != null) {
            boolean on = section == Section.SCENE && scene() != null && stage() != null;
            girl.clientPreviewScene = on ? scene() : null;
            girl.clientPreviewAnimation = on ? org.velvet.client.animation.GirlAnimationController.stageAnimation(
                    host.state().preset, stage()) : pose;
        }
        syncWidgets();
    }

    private float fitValue(int kind, int axis) {
        org.velvet.entity.girl.scene.SceneType scene = scene();
        if (scene == null) return kind == 0 ? 1 : 0;
        BoneTweaks.Tweak t = org.velvet.client.scene.SceneFits.get(host.state().preset, scene).get(fitBone);
        if (t == null) return kind == 0 ? 1 : 0;
        return (kind == 0 ? t.scale : kind == 1 ? t.offset : t.rotation)[axis];
    }

    private void setFitValue(int kind, int axis, float v) {
        if (syncing) return;
        org.velvet.entity.girl.scene.SceneType scene = scene();
        if (scene == null) return;
        BoneTweaks.Tweak t = org.velvet.client.scene.SceneFits.edit(host.state().preset, scene, fitBone);
        (kind == 0 ? t.scale : kind == 1 ? t.offset : t.rotation)[axis] = v;
        fitsChanged = true;
    }

    void resetFit() {
        org.velvet.entity.girl.scene.SceneType scene = scene();
        if (scene == null) return;
        org.velvet.client.scene.SceneFits.reset(host.state().preset, scene);
        fitsChanged = true;
        syncWidgets();
    }

    private void saveFits() {
        org.velvet.client.scene.SceneFits.save();
        fitsChanged = false;
    }

    private void saveFitsIfChanged() {
        if (fitsChanged) saveFits();
    }

    // ---- Bones ----

    @SuppressWarnings("unchecked")
    private void collectBones() {
        order.clear();
        depths.clear();
        parents.clear();
        withChildren.clear();
        folded.clear();
        bonesOf = host.state().preset;
        poses = posesOf(bonesOf);
        poseScroll = 0;
        AbstractGirlEntity girl = host.state().preview;
        Minecraft mc = Minecraft.getInstance();
        if (girl == null || !(mc.getEntityRenderDispatcher().getRenderer(girl) instanceof AbstractGirlRenderer<?> renderer)) return;
        var model = (software.bernie.geckolib.model.GeoModel<AbstractGirlEntity>) (Object) renderer.getGeoModel();
        BakedGeoModel baked = model.getBakedModel(model.getModelResource(girl));
        for (GeoBone bone : baked.topLevelBones()) addBone(bone, 0, null);
        rebuildRows();
    }

    /**
     * Every pose there is, in one list for any preset (they all share the skeleton): hers first, the usual
     * ones at the top; then the emotes; then every other preset's, under its name.
     */
    private static List<Pose> posesOf(org.velvet.entity.girl.GirlType preset) {
        List<Pose> out = new ArrayList<>();
        out.add(new Pose("idle", ""));
        addPoses(out, preset);
        out.add(new Pose("emotes", "", true));
        for (String e : List.of("joy", "cry", "angry")) out.add(new Pose("emote_" + e, "animation.emotes." + e));
        for (org.velvet.entity.girl.GirlType other : org.velvet.entity.girl.GirlType.values()) {
            if (other == preset) continue;
            out.add(new Pose(other.id(), "", true));
            addPoses(out, other);
        }
        return out;
    }

    private static void addPoses(List<Pose> out, org.velvet.entity.girl.GirlType preset) {
        var baked = software.bernie.geckolib.cache.GeckoLibCache.getBakedAnimations().get(preset.animations());
        if (baked == null) return;
        String prefix = "animation." + preset.animationPrefix() + ".";
        List<String> names = new ArrayList<>();
        for (String full : baked.animations().keySet()) {
            if (!full.startsWith(prefix)) continue;
            String name = full.substring(prefix.length());
            if (!SKIPPED.contains(name)) names.add(name);
        }
        names.sort(java.util.Comparator.comparingInt((String n) -> KNOWN.contains(n) ? KNOWN.indexOf(n) : KNOWN.size())
                .thenComparing(n -> n));
        for (String n : names) out.add(new Pose(n, prefix + n));
    }

    private static Component poseName(Pose p) {
        if (p.header()) {
            if (p.key().equals("emotes")) return Component.translatable("editor.velvet.pose.emotes");
            var type = org.velvet.entity.girl.GirlType.byId(p.key());
            return type != null ? type.displayName() : Component.literal(p.key());
        }
        String key = "editor.velvet.pose." + p.key();
        return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key)
                : Component.literal(p.key().replace('_', ' '));
    }

    private void addBone(GeoBone bone, int depth, @Nullable String parent) {
        String n = bone.getName();
        if (n.equals(AbstractGirlGeoModel.PARTNER_BONE) || n.startsWith("armor")) return;
        order.add(n);
        depths.put(n, depth);
        if (parent != null) parents.put(n, parent);
        if (!bone.getChildBones().isEmpty()) {
            withChildren.add(n);
            if (depth >= 3) folded.add(n);
        }
        for (GeoBone child : bone.getChildBones()) addBone(child, depth + 1, n);
    }

    private void rebuildRows() {
        rows.clear();
        for (String n : order) {
            boolean visible = true;
            for (String p = parents.get(n); p != null; p = parents.get(p)) {
                if (folded.contains(p)) {
                    visible = false;
                    break;
                }
            }
            if (visible) rows.add(new BoneRow(n, depths.get(n), withChildren.contains(n)));
        }
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, rows.size() - visibleRows()));
    }

    private void select(@Nullable String bone) {
        selected = bone;
        if (host.state().preview != null) host.state().preview.clientSelectedBone = bone;
        if (bone != null) {
            // unfold its parents and scroll it into view
            boolean changed = false;
            for (String p = parents.get(bone); p != null; p = parents.get(p)) changed |= folded.remove(p);
            if (changed) rebuildRows();
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).name().equals(bone)) {
                    if (i < listScroll) listScroll = i;
                    else if (i >= listScroll + visibleRows()) listScroll = i - visibleRows() + 1;
                }
            }
        }
        syncWidgets();
    }

    // ---- Editing ----

    @Nullable
    private BoneTweaks.Tweak current() {
        return selected == null ? null : host.state().tweaks.get(selected);
    }

    private float value(int kind, int axis) {
        BoneTweaks.Tweak t = current();
        if (t == null) return kind == 0 ? 1 : 0;
        return (kind == 0 ? t.scale : kind == 1 ? t.offset : t.rotation)[axis];
    }

    private void setValue(int kind, int axis, float v, String what) {
        if (selected == null) return;
        host.state().beforeTweak(selected + what);
        BoneTweaks.Tweak t = host.state().tweak(selected);
        (kind == 0 ? t.scale : kind == 1 ? t.offset : t.rotation)[axis] = v;
        host.state().tweaksChanged();
    }

    private void setTint(int colour) {
        if (selected == null) return;
        host.state().beforeTweak(null);
        host.state().tweak(selected).tint = colour | 0xFF000000;
        host.state().tweaksChanged();
        syncWidgets();
    }

    private void hexTyped(String text) {
        if (syncing || selected == null || !text.matches("#?[0-9a-fA-F]{6}")) return;
        int colour = Integer.parseInt(text.startsWith("#") ? text.substring(1) : text, 16) | 0xFF000000;
        BoneTweaks.Tweak t = current();
        if (t != null && t.tint == colour) return;
        host.state().beforeTweak(selected + "hex");
        host.state().tweak(selected).tint = colour;
        host.state().tweaksChanged();
    }

    private void setHidden(boolean hidden) {
        if (syncing || selected == null) return;
        toggleHidden(selected, hidden);
    }

    private void toggleHidden(String bone, boolean hidden) {
        host.state().beforeTweak(null);
        host.state().tweak(bone).hidden = hidden;
        host.state().tweaksChanged();
        syncWidgets();
    }

    private void resetBone() {
        if (selected == null) return;
        host.state().beforeTweak(null);
        host.state().tweaks.remove(selected);
        host.state().tweaksChanged();
        syncWidgets();
    }

    private void resetAll() {
        host.state().beforeTweak(null);
        host.state().tweaks.clear();
        host.state().tweaksChanged();
        syncWidgets();
    }

    private void copyBone() {
        BoneTweaks.Tweak t = current();
        clipboard = t == null ? BoneTweaks.Tweak.identity() : t.copy();
    }

    private void pasteBone() {
        if (selected == null || clipboard == null) return;
        host.state().beforeTweak(null);
        host.state().tweaks.put(selected, clipboard.copy());
        host.state().tweaksChanged();
        syncWidgets();
    }

    /** The same change on the bone of the other side (…L / …R, left / right), mirrored across X. */
    private void mirrorBone() {
        BoneTweaks.Tweak t = current();
        if (selected == null || t == null) return;
        String other = mirrorName(selected);
        if (other == null || !order.contains(other)) return;
        host.state().beforeTweak(null);
        BoneTweaks.Tweak m = t.copy();
        m.offset[0] = -m.offset[0];
        m.rotation[1] = -m.rotation[1];
        m.rotation[2] = -m.rotation[2];
        host.state().tweaks.put(other, m);
        host.state().tweaksChanged();
    }

    @Nullable
    static String mirrorName(String name) {
        String[][] pairs = {{"Left", "Right"}, {"left", "right"}};
        for (String[] p : pairs) {
            if (name.contains(p[0])) return name.replace(p[0], p[1]);
            if (name.contains(p[1])) return name.replace(p[1], p[0]);
        }
        if (name.endsWith("L")) return name.substring(0, name.length() - 1) + "R";
        if (name.endsWith("R")) return name.substring(0, name.length() - 1) + "L";
        // "bSideL3" and the like: the side letter before a number
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*?)([LR])(\\d+)$").matcher(name);
        if (m.matches()) return m.group(1) + (m.group(2).equals("L") ? "R" : "L") + m.group(3);
        return null;
    }

    private void undo() {
        if (host.state().undo()) syncWidgets();
    }

    private void redo() {
        if (host.state().redo()) syncWidgets();
    }

    private void setTool(Tool t) {
        tool = t;
        syncWidgets();
    }

    private void resetView() {
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;
        zoom = 1;
        panX = panY = 0;
        turntable = false;
    }

    // ---- Texture ----

    private static Path skinsFolder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(Velvet.MODID).resolve("skins");
    }

    private void refreshFiles() {
        try (Stream<Path> files = Files.list(skinsFolder())) {
            skinFiles = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).sorted().toList();
        } catch (IOException e) {
            skinFiles = List.of();
        }
    }

    private void cycleSkinMode() {
        EditorState s = host.state();
        if (s.skin == null) {
            status(Component.translatable("editor.velvet.skin.pick_first"), MUTED);
            return;
        }
        s.setSkin(s.skin.mode() == GirlSkin.Mode.REPLACE ? new GirlSkin(GirlSkin.Mode.OVERLAY, s.skin.hash()) : null);
        syncWidgets();
    }

    private void browse() {
        // the system's file dialog blocks: off the render thread, back on it with the result
        Util.ioPool().execute(() -> {
            String path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                    Component.translatable("editor.velvet.skin.browse").getString(), skinsFolder() + "/", null, null, false);
            if (path != null) Minecraft.getInstance().execute(() -> loadSkin(Path.of(path)));
        });
    }

    private void loadSkin(Path file) {
        try {
            byte[] data = Files.readAllBytes(file);
            String hash = SkinCache.loadLocal(data);
            GirlSkin.Mode mode = host.state().skin != null ? host.state().skin.mode() : GirlSkin.Mode.REPLACE;
            host.state().setSkin(new GirlSkin(mode, hash));
            status(Component.translatable("editor.velvet.skin.uploading", file.getFileName().toString()), MUTED);
            SkinCache.setUploadListener((h, error) -> {
                if (!h.equals(hash)) return;
                status(error.isEmpty() ? Component.translatable("editor.velvet.skin.uploaded")
                        : Component.translatable("editor.velvet.skin.refused", error), error.isEmpty() ? 0xFF2E7D32 : 0xFFB02020);
            });
            syncWidgets();
        } catch (IOException e) {
            status(Component.translatable("editor.velvet.skin.refused", e.getMessage()), 0xFFB02020);
        }
    }

    private void openFolder() {
        try {
            Files.createDirectories(skinsFolder());
        } catch (IOException ignored) {
            // opening shows the problem anyway
        }
        Util.getPlatform().openPath(skinsFolder());
        refreshFiles();
    }

    /** Her own texture as a PNG in the skins folder, to paint over. */
    private void exportDefault() {
        AbstractGirlEntity girl = host.state().preview;
        Minecraft mc = Minecraft.getInstance();
        if (girl == null || !(mc.getEntityRenderDispatcher().getRenderer(girl) instanceof AbstractGirlRenderer<?> renderer)) return;
        ResourceLocation texture = AbstractGirlGeoModel.defaultTexture(girl.getGirlType());
        Path out = skinsFolder().resolve(girl.getGirlType().id() + "_default.png");
        try (InputStream in = mc.getResourceManager().open(texture)) {
            Files.createDirectories(out.getParent());
            Files.write(out, in.readAllBytes());
            status(Component.translatable("editor.velvet.skin.exported", out.getFileName().toString()), 0xFF2E7D32);
            refreshFiles();
        } catch (IOException e) {
            status(Component.literal(e.getMessage()), 0xFFB02020);
        }
    }

    private void status(Component text, int colour) {
        textureStatus = text;
        textureStatusColour = colour;
    }

    // ---- Widgets ----

    private void showSection(Section s) {
        if (section == Section.SCENE && s != Section.SCENE) saveFitsIfChanged();
        section = s;
        if (s == Section.TEXTURE) refreshFiles();
        showScene();
    }

    @Override
    public void onShow() {
        if (host == null) return;
        if (bonesOf != host.state().preset) {
            collectBones();
            select(null);
            setPose(new Pose("idle", ""));
        }
        if (host.state().preview != null) {
            host.state().preview.clientSelectedBone = selected;
            // the animation tab plays its own on the same stand-in: back to the pose picked here
            host.state().preview.clientPreviewAnimation = pose;
        }
        showScene();
    }

    @Override
    public void onHide() {
        if (host == null) return;
        if (host.state().preview != null) {
            host.state().preview.clientSelectedBone = null;
            host.state().preview.clientPreviewScene = null;
        }
        saveFitsIfChanged();
    }

    /** Values, availability and which section's widgets show. */
    private void syncWidgets() {
        if (sliders.isEmpty()) return;
        syncing = true;
        try {
            syncWidgetsNow();
        } finally {
            syncing = false;
        }
    }

    private void syncWidgetsNow() {
        boolean picked = selected != null;
        for (RangeSlider s : sliders) {
            s.sync();
            s.active = picked;
        }
        transformWidgets.forEach(wd -> wd.visible = section == Section.TRANSFORM);
        colorWidgets.forEach(wd -> {
            wd.visible = section == Section.COLOR;
            wd.active = picked;
        });
        textureWidgets.forEach(wd -> wd.visible = section == Section.TEXTURE);
        sceneWidgets.forEach(wd -> wd.visible = section == Section.SCENE);
        if (sceneButton != null) {
            var scene = scene();
            var stage = stage();
            sceneButton.setMessage(scene == null ? Component.literal("-") : Component.translatable(scene.translationKey()));
            stageButton.setMessage(Component.translatable("editor.velvet.fit.pose",
                    stage == null ? "-" : stage.name().toLowerCase(Locale.ROOT).replace('_', ' ')));
            for (int i = 0; i < fitBoneButtons.size(); i++) {
                String bone = org.velvet.client.scene.SceneFits.BONES.get(i);
                fitBoneButtons.get(i).setMessage(Component.translatable("editor.velvet.fit.bone." + bone)
                        .withStyle(bone.equals(fitBone) ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
            }
            for (RangeSlider fs : fitSliders) {
                fs.sync();
                fs.active = scene != null;
            }
        }
        selectionWidgets.forEach(wd -> wd.active = picked);
        BoneTweaks.Tweak t = current();
        int tint = t == null ? BoneTweaks.NO_TINT : t.tint;
        String hex = String.format("#%06X", tint & 0xFFFFFF);
        if (!hexBox.getValue().equalsIgnoreCase(hex)) hexBox.setValue(hex);
        if (hiddenBox.selected() != (t != null && t.hidden)) hiddenBox.onPress();
        GirlSkin skin = host.state().skin;
        modeButton.setMessage(Component.translatable("editor.velvet.skin.mode",
                Component.translatable("editor.velvet.skin.mode." + (skin == null ? "own" : skin.mode().id()))));
        undoButton.active = host.state().canUndo();
        redoButton.active = host.state().canRedo();
        for (int i = 0; i < toolButtons.size(); i++) {
            Button b = toolButtons.get(i);
            String letter = switch (Tool.values()[i]) { case MOVE -> "✥"; case ROTATE -> "⟳"; case SCALE -> "⤢"; };
            b.setMessage(Component.literal(letter).withStyle(Tool.values()[i] == tool ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
        }
    }

    /** A colour to click. */
    private static final class Swatch extends AbstractWidget {
        private final int colour;
        private final Runnable onPress;

        Swatch(int x, int y, int colour, Runnable onPress) {
            super(x, y, 18, 18, Component.empty());
            this.colour = colour;
            this.onPress = onPress;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() && active ? 0xFFFFFFFF : 0xFF000000);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, active ? colour : 0xFF808080);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            onPress.run();
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {}
    }

    private static String format(int kind, double v) {
        return kind == 0 ? String.format("%.2f", v) : kind == 1 ? String.format("%+.2f", v) : String.format("%+.0f°", v);
    }

    // ---- Layout ----

    private boolean treeShown() {
        return showTree != null ? showTree : w >= ROOMY;
    }

    private int propsW() {
        return w >= ROOMY ? 124 : 112;
    }

    private int[] boneList() {
        return treeShown() ? new int[]{x, y + TOOLBAR, x + LIST_W, y + h} : new int[]{x, y + TOOLBAR, x, y + h};
    }

    private int[] viewport() {
        int x0 = treeShown() ? x + LIST_W + GAP : x;
        return new int[]{x0, y + TOOLBAR, x + w - propsW() - GAP, y + h};
    }

    private int[] poseList() {
        int[] v = viewport();
        return new int[]{v[2] - POSES_W, v[1] + 14, v[2], v[3] - 12};
    }

    private int visibleRows() {
        return Math.max(1, (h - TOOLBAR - 4) / ROW);
    }

    private static boolean inside(double px, double py, int[] r) {
        return px >= r[0] && px < r[2] && py >= r[1] && py < r[3];
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && clickTextureList(mx, my)) return true;
        int[] list = boneList();
        if (inside(mx, my, list)) {
            if (button != 0) return true;
            int i = (int) ((my - list[1] - 2) / ROW) + listScroll;
            if (i < 0 || i >= rows.size()) return true;
            BoneRow row = rows.get(i);
            int indent = list[0] + 2 + Math.min(row.depth(), 8) * 5;
            if (row.hasChildren() && mx >= indent && mx < indent + 7) {
                if (!folded.remove(row.name())) folded.add(row.name());
                rebuildRows();
            } else if (mx >= list[2] - 10) {
                BoneTweaks.Tweak t = host.state().tweaks.get(row.name());
                toggleHidden(row.name(), t == null || !t.hidden);
            } else {
                select(row.name().equals(selected) ? null : row.name());
            }
            return true;
        }
        int[] poseBox = poseList();
        if (inside(mx, my, poseBox) && button == 0) {
            int i = (int) ((my - poseBox[1]) / POSE_ROW) + poseScroll;
            if (i >= 0 && i < poses.size() + 1) {
                if (i == 0) paused = !paused;
                else if (!poses.get(i - 1).header()) setPose(poses.get(i - 1));
            }
            return true;
        }
        if (inside(mx, my, viewport())) {
            dragButton = button;
            dragged = false;
            pressX = mx;
            pressY = my;
            dragAxis = button == 0 ? handleAt(mx, my) : -1;
            return true;
        }
        return false;
    }

    private void setPose(Pose p) {
        pose = p.animation().isEmpty() ? null : p.animation();
        if (host.state().preview != null) host.state().preview.clientPreviewAnimation = pose;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragButton < 0 || button != dragButton) return false;
        if (Math.abs(mx - pressX) + Math.abs(my - pressY) > 3) dragged = true;
        if (dragAxis >= 0 && selected != null) {
            dragHandle(dragAxis, dx, dy);
        } else if (button == 0) {
            yaw += (float) dx * 0.8F;
            pitch = Mth.clamp(pitch + (float) dy * 0.8F, -89, 89);
            turntable = false;
        } else {
            panX += (float) dx;
            panY += (float) dy;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragButton < 0 || button != dragButton) return false;
        if (button == 0 && !dragged && dragAxis < 0) {
            String bone = picker.pick(mx, my);
            if (bone != null && !order.contains(bone)) bone = null;
            select(bone);
        }
        dragButton = -1;
        dragAxis = -1;
        syncWidgets();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scroll) {
        if (inside(mx, my, poseList())) {
            poseScroll = Mth.clamp(poseScroll - (int) Math.signum(scroll) * 2, 0, Math.max(0, poses.size() + 1 - poseRows()));
            return true;
        }
        if (inside(mx, my, viewport())) {
            zoom = Mth.clamp(zoom * (float) Math.pow(1.12, scroll), 0.3F, 8);
            return true;
        }
        if (inside(mx, my, boneList())) {
            listScroll = Mth.clamp(listScroll - (int) Math.signum(scroll) * 3, 0, Math.max(0, rows.size() - visibleRows()));
            return true;
        }
        if (section == Section.TEXTURE && mx >= x + w - propsW()) {
            fileScroll = Mth.clamp(fileScroll - (int) Math.signum(scroll), 0, Math.max(0, skinFiles.size() - 1));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        boolean ctrl = Screen.hasControlDown();
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if (Screen.hasShiftDown()) redo();
            else undo();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            copyBone();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            pasteBone();
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_G -> setTool(Tool.MOVE);
            case GLFW.GLFW_KEY_R -> setTool(Tool.ROTATE);
            case GLFW.GLFW_KEY_S -> setTool(Tool.SCALE);
            case GLFW.GLFW_KEY_H -> {
                if (selected != null) toggleHidden(selected, current() == null || !current().hidden);
            }
            case GLFW.GLFW_KEY_DELETE -> resetBone();
            case GLFW.GLFW_KEY_ESCAPE -> {
                return false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void tick() {
        AbstractGirlEntity girl = host.state().preview;
        if (girl != null && !paused) girl.tickCount++;
        if (turntable) yaw += 2;
    }

    // ---- Handles ----

    /** The three handles of the current tool: screen start, end and the screen length of one unit. */
    private record Handle(float x0, float y0, float x1, float y1, float unit) {}

    @Nullable
    private Handle[] handles() {
        if (selected == null) return null;
        ModelPicker.Pivot p = tool == Tool.MOVE ? picker.parentPivot(selected) : picker.pivot(selected);
        if (p == null) return null;
        Handle[] out = new Handle[3];
        for (int i = 0; i < 3; i++) {
            float ax = p.axisX()[i], ay = p.axisY()[i];
            // GeckoLib moves bones by -x: the x handle points where a bigger value moves her
            if (tool == Tool.MOVE && i == 0) {
                ax = -ax;
                ay = -ay;
            }
            float len = Mth.sqrt(ax * ax + ay * ay);
            if (len < 0.05F) continue;
            out[i] = new Handle(p.x(), p.y(), p.x() + ax / len * HANDLE_LENGTH, p.y() + ay / len * HANDLE_LENGTH, len);
        }
        return out;
    }

    private int handleAt(double mx, double my) {
        Handle[] hs = handles();
        if (hs == null) return -1;
        for (int i = 2; i >= 0; i--) {
            Handle hd = hs[i];
            if (hd != null && Math.abs(mx - hd.x1()) <= HANDLE_SIZE && Math.abs(my - hd.y1()) <= HANDLE_SIZE) return i;
        }
        return -1;
    }

    private void dragHandle(int axis, double dx, double dy) {
        Handle[] hs = handles();
        if (hs == null || hs[axis] == null || selected == null) return;
        Handle hd = hs[axis];
        float ux = (hd.x1() - hd.x0()) / HANDLE_LENGTH, uy = (hd.y1() - hd.y0()) / HANDLE_LENGTH;
        float along = (float) (dx * ux + dy * uy);
        BoneTweaks.Tweak t = host.state().tweaks.get(selected);
        switch (tool) {
            case MOVE -> setValue(1, axis, Mth.clamp(value(1, axis) + along / hd.unit(), -BoneTweaks.MAX_OFFSET, BoneTweaks.MAX_OFFSET), "handle" + axis);
            case ROTATE -> setValue(2, axis, Mth.clamp(value(2, axis) + along, -BoneTweaks.MAX_ROTATION, BoneTweaks.MAX_ROTATION), "handle" + axis);
            case SCALE -> setValue(0, axis, Mth.clamp(value(0, axis) * (1 + along * 0.01F), BoneTweaks.MIN_SCALE, BoneTweaks.MAX_SCALE), "handle" + axis);
        }
        for (RangeSlider s : sliders) s.sync();
    }

    // ---- Rendering ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBoneList(g, mouseX, mouseY);

        int[] v = viewport();
        drawInset(g, v[0], v[1], v[2] - v[0], v[3] - v[1], VIEW_BACKGROUND);
        AbstractGirlEntity girl = host.state().preview;
        g.enableScissor(v[0], v[1], v[2], v[3]);
        if (girl != null) {
            ModelView.render(g, girl, v[0], v[1], v[2] - POSES_W, v[3], yaw, pitch, zoom, panX, panY, 0.5F, 0.62F, true,
                    picker, paused ? 0 : partialTick);
            renderHandles(g, mouseX, mouseY);
        }
        g.disableScissor();
        renderPoses(g, mouseX, mouseY);

        Component bone = selected == null ? Component.translatable("editor.velvet.pick_bone") : Component.literal(selected);
        g.drawString(font, font.plainSubstrByWidth(bone.getString(), v[2] - v[0] - POSES_W - 6), v[0] + 3, v[1] + 4,
                selected == null ? 0xC0FFFFFF : 0xFFFF9A9A, false);
        Component hint = Component.translatable("editor.velvet.view_hint");
        g.drawString(font, font.plainSubstrByWidth(hint.getString(), v[2] - v[0] - 6), v[0] + 3, v[3] - 10, 0x90FFFFFF, false);

        // properties
        int px = x + w - propsW(), py = y + TOOLBAR + 26;
        if (section == Section.TEXTURE) renderTexture(g, px, py + 56, mouseX, mouseY);
        else if (section == Section.COLOR && selected == null || section == Section.TRANSFORM && selected == null) {
            g.drawString(font, Component.translatable("editor.velvet.pick_bone").withStyle(ChatFormatting.ITALIC),
                    px, y + h - 10, MUTED, false);
        }
    }

    private void renderBoneList(GuiGraphics g, int mouseX, int mouseY) {
        if (!treeShown()) return;
        int[] list = boneList();
        drawInset(g, list[0], list[1], list[2] - list[0], list[3] - list[1], 0xFF8B8B8B);
        int visible = visibleRows();
        for (int i = 0; i < visible && i + listScroll < rows.size(); i++) {
            BoneRow row = rows.get(i + listScroll);
            int ry = list[1] + 2 + i * ROW;
            boolean isSelected = row.name().equals(selected);
            BoneTweaks.Tweak t = host.state().tweaks.get(row.name());
            boolean edited = t != null && !t.isIdentity();
            boolean hidden = t != null && t.hidden;
            if (isSelected) g.fill(list[0], ry - 1, list[2], ry + ROW - 1, 0xFF5A5A9A);
            else if (mouseX >= list[0] && mouseX < list[2] && mouseY >= ry - 1 && mouseY < ry + ROW - 1) {
                g.fill(list[0], ry - 1, list[2], ry + ROW - 1, 0xFF9C9C9C);
            }
            int indent = list[0] + 2 + Math.min(row.depth(), 8) * 5;
            if (row.hasChildren()) g.drawString(font, folded.contains(row.name()) ? "▸" : "▾", indent, ry, 0xFF303030, false);
            int textX = indent + 7;
            int colour = isSelected ? 0xFFFFFFFF : hidden ? 0xFF6A6A6A : edited ? 0xFF1A3E8C : 0xFF202020;
            g.drawString(font, font.plainSubstrByWidth(row.name(), list[2] - textX - 11), textX, ry, colour, false);
            g.drawString(font, hidden ? "◌" : "◉", list[2] - 9, ry, hidden ? 0xFF6A6A6A : 0xFF303030, false);
        }
        if (rows.size() > visible) {
            int hgt = list[3] - list[1];
            int barH = Math.max(8, hgt * visible / rows.size());
            int barY = list[1] + (hgt - barH) * listScroll / Math.max(1, rows.size() - visible);
            g.fill(list[2] - 2, barY, list[2], barY + barH, 0xFF505050);
        }
    }

    private void renderHandles(GuiGraphics g, int mouseX, int mouseY) {
        Handle[] hs = handles();
        if (hs == null) return;
        int hovered = dragAxis >= 0 ? dragAxis : handleAt(mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(0, 0, 900);
        for (int i = 0; i < 3; i++) {
            Handle hd = hs[i];
            if (hd == null) continue;
            int colour = i == hovered ? 0xFFFFFF60 : AXIS_COLOURS[i];
            ModelView.screenLine(g, hd.x0(), hd.y0(), hd.x1(), hd.y1(), i == hovered ? 2.5F : 1.6F, colour);
            int cx = Math.round(hd.x1()), cy = Math.round(hd.y1());
            int r = HANDLE_SIZE / 2;
            if (tool == Tool.ROTATE) {
                g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF000000);
                g.fill(cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1, colour);
            } else {
                g.fill(cx - r, cy - r, cx + r, cy + r, colour);
            }
        }
        for (Handle hd : hs) {
            if (hd == null) continue;
            // the pivot
            int px = Math.round(hd.x0()), py = Math.round(hd.y0());
            g.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFFFFF);
            break;
        }
        g.flush();
        g.pose().popPose();
    }

    private int poseRows() {
        int[] p = poseList();
        return Math.max(1, (p[3] - p[1]) / POSE_ROW);
    }

    private void renderPoses(GuiGraphics g, int mouseX, int mouseY) {
        int[] p = poseList();
        g.fill(p[0], p[1] - 13, p[2], p[3], 0x60000000);
        g.drawString(font, Component.translatable("editor.velvet.poses"), p[0] + 3, p[1] - 11, 0xFFE0E0E0, false);
        int rowsShown = poseRows();
        for (int i = 0; i < rowsShown && i + poseScroll < poses.size() + 1; i++) {
            int idx = i + poseScroll;
            int ry = p[1] + i * POSE_ROW;
            boolean hover = mouseX >= p[0] && mouseX < p[2] && mouseY >= ry && mouseY < ry + POSE_ROW;
            Component label;
            boolean active;
            if (idx == 0) {
                label = Component.translatable(paused ? "editor.velvet.pose.play" : "editor.velvet.pose.pause");
                active = paused;
            } else {
                Pose ps = poses.get(idx - 1);
                if (ps.header()) {
                    g.fill(p[0], ry, p[2], ry + POSE_ROW, 0x70000000);
                    g.drawString(font, font.plainSubstrByWidth(poseName(ps).getString(), POSES_W - 5), p[0] + 3, ry + 2, 0xFF9FD0FF, false);
                    continue;
                }
                label = poseName(ps);
                active = ps.animation().isEmpty() ? pose == null : ps.animation().equals(pose);
            }
            if (active) g.fill(p[0], ry, p[2], ry + POSE_ROW, 0xA05A5A9A);
            else if (hover) g.fill(p[0], ry, p[2], ry + POSE_ROW, 0x50FFFFFF);
            g.drawString(font, font.plainSubstrByWidth(label.getString(), POSES_W - 5), p[0] + 3, ry + 2,
                    idx == 0 ? 0xFFFFE080 : 0xFFFFFFFF, false);
        }
    }

    private void renderTexture(GuiGraphics g, int px, int py, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("editor.velvet.skin.files"), px, py, LABEL, false);
        int listY = py + 10;
        int listBottom = y + h - (textureStatus != null ? 22 : 2);
        drawInset(g, px, listY, propsW(), listBottom - listY, 0xFF8B8B8B);
        if (skinFiles.isEmpty()) {
            for (var line : font.split(Component.translatable("editor.velvet.skin.no_files"), propsW() - 4)) {
                g.drawString(font, line, px + 2, listY + 2, 0xFF303030, false);
                listY += 9;
            }
        } else {
            int rowsShown = (listBottom - listY - 2) / ROW;
            for (int i = 0; i < rowsShown && i + fileScroll < skinFiles.size(); i++) {
                Path file = skinFiles.get(i + fileScroll);
                int ry = listY + 2 + i * ROW;
                if (mouseX >= px && mouseX < px + propsW() && mouseY >= ry - 1 && mouseY < ry + ROW - 1) {
                    g.fill(px, ry - 1, px + propsW(), ry + ROW - 1, 0xFF9C9C9C);
                }
                g.drawString(font, font.plainSubstrByWidth(file.getFileName().toString(), propsW() - 4), px + 2, ry, 0xFF202020, false);
            }
        }
        if (textureStatus != null) {
            int sy = y + h - 19;
            for (var line : font.split(textureStatus, propsW())) {
                g.drawString(font, line, px, sy, textureStatusColour, false);
                sy += 9;
            }
        }
    }

    /** Clicks on the texture file list (not a widget). */
    private boolean clickTextureList(double mx, double my) {
        if (section != Section.TEXTURE) return false;
        int px = x + w - propsW(), listY = y + TOOLBAR + 26 + 56 + 10;
        int listBottom = y + h - (textureStatus != null ? 22 : 2);
        if (mx < px || mx >= px + propsW() || my < listY || my >= listBottom) return false;
        int i = (int) ((my - listY - 2) / ROW) + fileScroll;
        if (i >= 0 && i < skinFiles.size()) loadSkin(skinFiles.get(i));
        return true;
    }

    @Override
    public void renderOver(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int[] list = boneList();
        if (treeShown() && inside(mouseX, mouseY, list) && mouseX >= list[2] - 10) {
            g.renderTooltip(font, Component.translatable("editor.velvet.hide_bone"), mouseX, mouseY);
        }
    }
}

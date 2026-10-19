package org.velvet.client.gui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.velvet.client.animation.ScrubAnimationController;
import org.velvet.client.gui.FileDialogs;
import org.velvet.client.gui.RangeSlider;
import org.velvet.client.preset.PresetEditing;
import org.velvet.client.render.AbstractGirlRenderer;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.velvet.client.gui.PanelArt.*;

/**
 * The animation tab: a custom preset's own animations, made in the game. On the left the list of them (a new
 * one, a copy of any animation of any preset, delete, rename, how long and whether it loops); in the middle
 * her model playing it, or held at the moment under the playhead, and the timeline with the keyframes of the
 * picked bone (turn, move, scale); on the right her bones and the picked keyframe's values and easing. A key
 * goes in at the playhead with the bone's value there. Every change shows at once; "Save" sends them to the
 * server. A built-in preset's animations can't be changed: an editable copy's can.
 */
public final class AnimationTab implements EditorTab {

    private static final int LIST_W = 112, SIDE_W = 112, TIMELINE_H = 52, ROW = 11;
    private static final String[] CHANNELS = {"rotation", "position", "scale"};
    private static final int[] CHANNEL_COLOURS = {0xFFE05050, 0xFF60D060, 0xFF5080F0};
    private static final List<String> EASINGS = List.of("linear", "step", "easeInOutSine", "easeInSine", "easeOutSine",
            "easeInOutQuad", "easeInOutCubic", "easeOutBack", "easeInOutBack", "catmullrom");

    private Host host;
    private Font font;
    private int x, y, w, h;
    @Nullable private GirlType loadedFor;
    private JsonObject file = new JsonObject();
    @Nullable private String selected;
    @Nullable private String bone;
    private int channel;
    private double playhead;
    private boolean playing = true;
    private long playStart;
    @Nullable private Double selectedKey;
    private int listScroll, boneScroll;
    private final List<String> bones = new ArrayList<>();
    /** Picking an animation to copy instead of the preset's list: every animation there is, with a filter. */
    private boolean copying;
    private String filter = "";
    private final List<String> allAnimations = new ArrayList<>();
    private float yaw = 25, pitch = 10;
    private boolean draggingPlayhead, turning;
    private final EditBox[] values = new EditBox[3];
    @Nullable private Component status;

    @Override
    public void init(Host host, int x, int y, int w, int h) {
        this.host = host;
        this.font = host.font();
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        GirlType type = host.state().preset;
        if (!type.isCustom()) {
            host.add(Button.builder(Component.translatable("editor.velvet.copy_preset"), b -> PresetEditing.copyOf(type, made -> {
                host.state().setPreset(made);
                host.rebuild();
            })).bounds(x + (w - 180) / 2, y + h / 2, 180, 20).build());
            return;
        }
        if (loadedFor != type) {
            loadedFor = type;
            file = PresetEditing.animations(type);
            selected = names().isEmpty() ? null : names().getFirst();
            collectBones();
        }
        int lx = x, by = y + h - 14 * 4 - 6;
        if (copying) {
            EditBox search = host.add(new EditBox(font, lx, y, LIST_W, 14, Component.translatable("editor.velvet.anim.filter")));
            search.setValue(filter);
            search.setHint(Component.translatable("editor.velvet.anim.filter").withStyle(ChatFormatting.DARK_GRAY));
            search.setResponder(v -> {
                filter = v;
                listScroll = 0;
            });
            host.add(Button.builder(Component.translatable("gui.cancel"), b -> {
                copying = false;
                host.rebuild();
            }).bounds(lx, y + h - 14, LIST_W, 14).build());
        } else {
            host.add(Button.builder(Component.translatable("editor.velvet.anim.new"), b -> newAnimation()).bounds(lx, by, LIST_W / 2 - 1, 14).build());
            host.add(Button.builder(Component.translatable("editor.velvet.anim.copy"), b -> {
                copying = true;
                collectAll();
                host.rebuild();
            }).bounds(lx + LIST_W / 2 + 1, by, LIST_W / 2 - 1, 14)
                    .tooltip(Tooltip.create(Component.translatable("editor.velvet.anim.copy.tooltip"))).build());
            host.add(Button.builder(Component.translatable("editor.velvet.import"), b -> importFile()).bounds(lx, by + 15, LIST_W / 2 - 1, 14)
                    .tooltip(Tooltip.create(Component.translatable("editor.velvet.anim.import"))).build());
            host.add(Button.builder(Component.translatable("editor.velvet.anim.delete"), b -> deleteAnimation())
                    .bounds(lx + LIST_W / 2 + 1, by + 15, LIST_W / 2 - 1, 14).build());
            JsonObject anim = animation();
            if (anim != null) {
                EditBox name = host.add(new EditBox(font, lx, by + 30, LIST_W, 14, Component.translatable("editor.velvet.anim.name")));
                name.setValue(shortName(selected));
                name.setResponder(this::rename);
                host.add(new RangeSlider(lx, by + 45, LIST_W - 30, 14, 0.1, 30, 0.05, this::length, v -> {
                    anim.addProperty("animation_length", Math.round(v * 100) / 100.0);
                    changed();
                }, v -> Component.translatable("editor.velvet.anim.length", String.format(Locale.ROOT, "%.2f", v))));
                host.add(Button.builder(Component.literal(loops() ? "⟳" : "→"), b -> {
                    anim.addProperty("loop", !loops());
                    changed();
                    host.rebuild();
                }).bounds(lx + LIST_W - 28, by + 45, 28, 14)
                        .tooltip(Tooltip.create(Component.translatable("editor.velvet.anim.loop"))).build());
            }
        }

        // the picked key: channel, values, easing
        int sx = x + w - SIDE_W, sy = y + h - 14 * 5 - 4;
        host.add(Button.builder(Component.translatable("editor.velvet.anim.channel." + CHANNELS[channel]), b -> {
            channel = (channel + 1) % 3;
            selectedKey = null;
            host.rebuild();
        }).bounds(sx, sy, SIDE_W, 14).build());
        String[] axes = {"X", "Y", "Z"};
        int vw = (SIDE_W - 4) / 3;
        double[] key = keyValues();
        for (int i = 0; i < 3; i++) {
            int axis = i;
            values[i] = host.add(new EditBox(font, sx + i * (vw + 2), sy + 15, vw, 14, Component.literal(axes[i])));
            values[i].setValue(key == null ? "" : trim(key[i]));
            values[i].active = key != null;
            values[i].setResponder(v -> setValue(axis, v));
        }
        host.add(Button.builder(Component.literal(easing()), b -> cycleEasing()).bounds(sx, sy + 30, SIDE_W, 14)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.anim.easing"))).build());
        host.add(Button.builder(Component.translatable("editor.velvet.anim.key"), b -> addKey()).bounds(sx, sy + 45, SIDE_W / 2 - 1, 14)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.anim.key.tooltip"))).build());
        host.add(Button.builder(Component.translatable("editor.velvet.anim.unkey"), b -> deleteKey()).bounds(sx + SIDE_W / 2 + 1, sy + 45, SIDE_W / 2 - 1, 14).build());
        host.add(Button.builder(Component.literal(playing ? "⏸" : "▶"), b -> {
            playing = !playing;
            playStart = System.currentTimeMillis() - (long) (playhead * 1000);
            host.rebuild();
        }).bounds(sx, sy + 60, SIDE_W, 14).build());
    }

    // ---- The file ----

    private JsonObject anims() {
        if (!file.has("animations") || !file.get("animations").isJsonObject()) file.add("animations", new JsonObject());
        return file.getAsJsonObject("animations");
    }

    private List<String> names() {
        return new ArrayList<>(anims().keySet());
    }

    @Nullable
    private JsonObject animation() {
        return selected != null && anims().has(selected) ? anims().getAsJsonObject(selected) : null;
    }

    private String prefix() {
        return "animation." + host.state().preset.animationPrefix() + ".";
    }

    private String shortName(@Nullable String full) {
        return full == null ? "" : full.startsWith(prefix()) ? full.substring(prefix().length()) : full;
    }

    private double length() {
        JsonObject a = animation();
        return a != null && a.has("animation_length") ? a.get("animation_length").getAsDouble() : 1;
    }

    private boolean loops() {
        JsonObject a = animation();
        if (a == null || !a.has("loop")) return false;
        JsonElement l = a.get("loop");
        return l.isJsonPrimitive() && (l.getAsJsonPrimitive().isBoolean() ? l.getAsBoolean() : "true".equals(l.getAsString()));
    }

    private void changed() {
        PresetEditing.setAnimations(host.state().preset, file);
        host.state().dirty = true;
    }

    private String uniqueName(String base) {
        String name = prefix() + base;
        for (int n = 2; anims().has(name); n++) name = prefix() + base + "_" + n;
        return name;
    }

    private void newAnimation() {
        JsonObject a = new JsonObject();
        a.addProperty("loop", true);
        a.addProperty("animation_length", 2.0);
        a.add("bones", new JsonObject());
        selected = uniqueName("new");
        anims().add(selected, a);
        changed();
        host.rebuild();
    }

    private void copyAnimation(String from) {
        JsonObject a = PresetEditing.animationJson(from);
        if (a == null) return;
        selected = uniqueName(from.substring(from.lastIndexOf('.') + 1));
        anims().add(selected, a);
        copying = false;
        changed();
        host.rebuild();
    }

    private void deleteAnimation() {
        if (selected == null) return;
        anims().remove(selected);
        selected = names().isEmpty() ? null : names().getFirst();
        changed();
        host.rebuild();
    }

    private void rename(String name) {
        String clean = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (selected == null || clean.isEmpty() || anims().has(prefix() + clean)) return;
        JsonObject a = anims().remove(selected).getAsJsonObject();
        selected = prefix() + clean;
        anims().add(selected, a);
        changed();
    }

    /** Animations from a Blockbench file: renamed to this preset's ("animation.<id>.<name>"). */
    private void importFile() {
        FileDialogs.open(Component.translatable("editor.velvet.anim.import").getString(), List.of("*.json"), "Animations (.json)", false, paths -> {
            try {
                JsonObject in = PresetEditing.parse(Files.readAllBytes(paths.getFirst()));
                JsonObject list = in.getAsJsonObject("animations");
                int n = 0;
                for (Map.Entry<String, JsonElement> e : list.entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    String base = e.getKey().substring(e.getKey().lastIndexOf('.') + 1);
                    String name = uniqueName(base.isEmpty() ? "imported" : base);
                    anims().add(name, e.getValue());
                    selected = name;
                    n++;
                }
                status = Component.translatable("editor.velvet.anim.imported", n);
                changed();
                host.rebuild();
            } catch (Exception e) {
                status = Component.translatable("editor.velvet.import.failed", String.valueOf(e.getMessage())).withStyle(ChatFormatting.RED);
            }
        });
    }

    // ---- Keyframes ----

    /** The picked bone's channel as a time → keyframe map (it's made one if it was a single value). */
    @Nullable
    private JsonObject channel(boolean create) {
        JsonObject a = animation();
        if (a == null || bone == null) return null;
        if (!a.has("bones")) a.add("bones", new JsonObject());
        JsonObject bones = a.getAsJsonObject("bones");
        if (!bones.has(bone)) {
            if (!create) return null;
            bones.add(bone, new JsonObject());
        }
        JsonObject b = bones.getAsJsonObject(bone);
        String c = CHANNELS[channel];
        if (!b.has(c)) {
            if (!create) return null;
            b.add(c, new JsonObject());
        }
        JsonElement ch = b.get(c);
        // a value for the whole animation: a key at 0
        if (ch.isJsonArray() || ch.isJsonPrimitive() || ch.isJsonObject() && ch.getAsJsonObject().has("vector")) {
            JsonObject frames = new JsonObject();
            JsonObject frame = new JsonObject();
            frame.add("vector", ch.isJsonObject() ? ch.getAsJsonObject().get("vector") : ch.isJsonArray() ? ch : array(ch.getAsDouble()));
            frames.add("0.0", frame);
            b.add(c, frames);
            return frames;
        }
        return ch.getAsJsonObject();
    }

    private static JsonArray array(double v) {
        JsonArray a = new JsonArray();
        a.add(v);
        a.add(v);
        a.add(v);
        return a;
    }

    /** The keys of the picked bone in a channel, by time. */
    private TreeMap<Double, JsonObject> keys(int c) {
        TreeMap<Double, JsonObject> out = new TreeMap<>();
        int was = channel;
        channel = c;
        JsonObject frames = channel(false);
        channel = was;
        if (frames == null) return out;
        for (Map.Entry<String, JsonElement> e : frames.entrySet()) {
            try {
                JsonElement v = e.getValue();
                JsonObject frame = v.isJsonObject() ? v.getAsJsonObject() : new JsonObject();
                if (!v.isJsonObject()) frame.add("vector", v);
                out.put(Double.parseDouble(e.getKey()), frame);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    @Nullable
    private double[] keyValues() {
        if (selectedKey == null) return null;
        JsonObject frame = keys(channel).get(selectedKey);
        if (frame == null) return null;
        JsonElement v = frame.has("vector") ? frame.get("vector") : frame.has("post") ? frame.get("post") : null;
        if (v != null && v.isJsonObject() && v.getAsJsonObject().has("vector")) v = v.getAsJsonObject().get("vector");
        if (v == null || !v.isJsonArray() || v.getAsJsonArray().size() < 3) return null;
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            try {
                out[i] = v.getAsJsonArray().get(i).getAsDouble();
            } catch (RuntimeException e) {
                out[i] = 0; // molang: shown as 0, kept until changed
            }
        }
        return out;
    }

    private String easing() {
        if (selectedKey == null) return Component.translatable("editor.velvet.anim.no_key").getString();
        JsonObject frame = keys(channel).get(selectedKey);
        return frame != null && frame.has("easing") ? frame.get("easing").getAsString() : "linear";
    }

    private void cycleEasing() {
        JsonObject frames = channel(false);
        if (frames == null || selectedKey == null) return;
        String key = timeKey(frames, selectedKey);
        if (key == null) return;
        JsonObject frame = normalized(frames, key);
        frame.addProperty("easing", EASINGS.get((EASINGS.indexOf(easing()) + 1) % EASINGS.size()));
        changed();
        host.rebuild();
    }

    private void setValue(int axis, String text) {
        JsonObject frames = channel(false);
        if (frames == null || selectedKey == null) return;
        double v;
        try {
            v = Double.parseDouble(text.replace(',', '.'));
        } catch (NumberFormatException e) {
            return;
        }
        String key = timeKey(frames, selectedKey);
        if (key == null) return;
        JsonObject frame = normalized(frames, key);
        JsonArray vec = frame.getAsJsonArray("vector");
        vec.set(axis, new com.google.gson.JsonPrimitive(v));
        changed();
    }

    /** The keyframe under {@code key} as {"vector": [x, y, z], ...}, whatever form it had. */
    private static JsonObject normalized(JsonObject frames, String key) {
        JsonElement v = frames.get(key);
        JsonObject frame;
        if (v.isJsonObject()) {
            frame = v.getAsJsonObject();
            if (!frame.has("vector")) {
                JsonElement post = frame.has("post") ? frame.get("post") : frame.get("pre");
                if (post != null && post.isJsonObject()) post = post.getAsJsonObject().get("vector");
                frame.add("vector", post != null && post.isJsonArray() ? post.deepCopy() : array(0));
                frame.remove("post");
                frame.remove("pre");
            }
        } else {
            frame = new JsonObject();
            frame.add("vector", v.isJsonArray() ? v : array(0));
            frames.add(key, frame);
        }
        JsonArray vec = frame.getAsJsonArray("vector");
        for (int i = 0; i < 3; i++) {
            if (i >= vec.size()) vec.add(0);
            else if (!vec.get(i).isJsonPrimitive() || !vec.get(i).getAsJsonPrimitive().isNumber()) vec.set(i, new com.google.gson.JsonPrimitive(0));
        }
        return frame;
    }

    @Nullable
    private static String timeKey(JsonObject frames, double time) {
        for (String k : frames.keySet()) {
            try {
                if (Math.abs(Double.parseDouble(k) - time) < 1.0E-4) return k;
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /** A key at the playhead, with the value the animation has there (the previous key's). */
    private void addKey() {
        JsonObject frames = channel(true);
        if (frames == null) return;
        double t = Math.round(playhead * 100) / 100.0;
        if (timeKey(frames, t) != null) {
            selectedKey = t;
            host.rebuild();
            return;
        }
        TreeMap<Double, JsonObject> keys = keys(channel);
        var before = keys.floorEntry(t);
        JsonObject frame = new JsonObject();
        double[] base = channel == 2 ? new double[]{1, 1, 1} : new double[3];
        if (before != null) {
            selectedKey = before.getKey();
            double[] v = keyValues();
            if (v != null) base = v;
        }
        JsonArray vec = new JsonArray();
        for (double d : base) vec.add(d);
        frame.add("vector", vec);
        frame.addProperty("easing", "easeInOutSine");
        frames.add(String.valueOf(t), frame);
        selectedKey = t;
        changed();
        host.rebuild();
    }

    private void deleteKey() {
        JsonObject frames = channel(false);
        if (frames == null || selectedKey == null) return;
        String key = timeKey(frames, selectedKey);
        if (key != null) frames.remove(key);
        selectedKey = null;
        changed();
        host.rebuild();
    }

    // ---- Bones, the list to copy from ----

    private void collectBones() {
        bones.clear();
        AbstractGirlEntity girl = host.state().preview;
        Minecraft mc = Minecraft.getInstance();
        if (girl == null || !(mc.getEntityRenderDispatcher().getRenderer(girl) instanceof AbstractGirlRenderer<?> renderer)) return;
        @SuppressWarnings("unchecked")
        var model = (software.bernie.geckolib.model.GeoModel<AbstractGirlEntity>) (Object) renderer.getGeoModel();
        BakedGeoModel baked = model.getBakedModel(model.getModelResource(girl));
        for (GeoBone b : baked.topLevelBones()) addBone(b);
        if (bone == null || !bones.contains(bone)) bone = bones.contains("body") ? "body" : bones.isEmpty() ? null : bones.getFirst();
    }

    private void addBone(GeoBone b) {
        if (b.getName().equals("steve") || b.getName().startsWith("armor")) return;
        bones.add(b.getName());
        for (GeoBone child : b.getChildBones()) addBone(child);
    }

    private void collectAll() {
        allAnimations.clear();
        for (GirlType t : GirlType.values()) {
            var baked = software.bernie.geckolib.cache.GeckoLibCache.getBakedAnimations().get(t.animations());
            if (baked != null) allAnimations.addAll(baked.animations().keySet());
        }
        var emotes = software.bernie.geckolib.cache.GeckoLibCache.getBakedAnimations().get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(org.velvet.Velvet.MODID, "animations/emotes/emotes.animation.json"));
        if (emotes != null) allAnimations.addAll(emotes.animations().keySet());
        allAnimations.sort(String::compareTo);
    }

    private List<String> listed() {
        if (!copying) return names();
        List<String> out = new ArrayList<>();
        String f = filter.toLowerCase(Locale.ROOT);
        for (String a : allAnimations) if (f.isEmpty() || a.contains(f)) out.add(a);
        return out;
    }

    // ---- Input ----

    private int[] listBox() {
        return copying ? new int[]{x, y + 16, x + LIST_W, y + h - 16} : new int[]{x, y, x + LIST_W, y + h - 14 * 4 - 8};
    }

    private int[] boneBox() {
        return new int[]{x + w - SIDE_W, y, x + w, y + h - 14 * 5 - 6};
    }

    private int[] viewBox() {
        return new int[]{x + LIST_W + 4, y, x + w - SIDE_W - 4, y + h - TIMELINE_H - 2};
    }

    private int[] timelineBox() {
        return new int[]{x + LIST_W + 4, y + h - TIMELINE_H, x + w - SIDE_W - 4, y + h};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!host.state().preset.isCustom()) return false;
        int[] l = listBox();
        if (inside(l, mx, my)) {
            int i = (int) ((my - l[1]) / ROW) + listScroll;
            List<String> list = listed();
            if (i >= 0 && i < list.size()) {
                if (copying) copyAnimation(list.get(i));
                else {
                    selected = list.get(i);
                    selectedKey = null;
                    playhead = 0;
                    host.rebuild();
                }
            }
            return true;
        }
        int[] b = boneBox();
        if (inside(b, mx, my)) {
            int i = (int) ((my - b[1]) / ROW) + boneScroll;
            if (i >= 0 && i < bones.size()) {
                bone = bones.get(i);
                selectedKey = null;
                host.rebuild();
            }
            return true;
        }
        int[] t = timelineBox();
        if (inside(t, mx, my)) {
            // a key under the mouse is picked, else the playhead moves there
            double time = timeAt(mx);
            int row = (int) ((my - t[1] - 12) / 12);
            if (row >= 0 && row < 3) {
                for (double k : keys(row).keySet()) {
                    if (Math.abs(xOf(k) - mx) <= 3) {
                        channel = row;
                        selectedKey = k;
                        playhead = k;
                        playing = false;
                        host.rebuild();
                        return true;
                    }
                }
            }
            playing = false;
            draggingPlayhead = true;
            playhead = time;
            return true;
        }
        if (inside(viewBox(), mx, my)) {
            turning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingPlayhead) {
            playhead = timeAt(mx);
            return true;
        }
        if (turning) {
            yaw += (float) dx * 0.8F;
            pitch = Mth.clamp(pitch + (float) dy * 0.8F, -80, 80);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = draggingPlayhead || turning;
        if (draggingPlayhead) host.rebuild();
        draggingPlayhead = false;
        turning = false;
        return was;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (inside(listBox(), mx, my)) {
            listScroll = Math.max(0, listScroll - (int) Math.signum(amount) * 2);
            return true;
        }
        if (inside(boneBox(), mx, my)) {
            boneScroll = Math.max(0, boneScroll - (int) Math.signum(amount) * 2);
            return true;
        }
        return false;
    }

    private static boolean inside(int[] b, double mx, double my) {
        return mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
    }

    private double timeAt(double mx) {
        int[] t = timelineBox();
        return Mth.clamp((mx - t[0] - 4) / (t[2] - t[0] - 8) * length(), 0, length());
    }

    private int xOf(double time) {
        int[] t = timelineBox();
        return (int) Math.round(t[0] + 4 + time / length() * (t[2] - t[0] - 8));
    }

    @Override
    public void onHide() {
        AbstractGirlEntity girl = host.state().preview;
        if (girl != null) {
            ScrubAnimationController.SCRUB.remove(girl);
            girl.clientPreviewAnimation = null;
        }
    }

    // ---- Drawing ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GirlType type = host.state().preset;
        if (!type.isCustom()) {
            int ty = y + 10;
            for (var line : font.split(Component.translatable("editor.velvet.built_in_preset", type.displayName()), w)) {
                g.drawCenteredString(font, line, x + w / 2, ty, LABEL);
                ty += 10;
            }
            return;
        }
        AbstractGirlEntity girl = host.state().preview;
        if (playing && animation() != null) {
            double len = length();
            double t = (System.currentTimeMillis() - playStart) / 1000.0;
            playhead = loops() ? t % len : Math.min(t, len);
        }
        if (girl != null) {
            girl.clientPreviewAnimation = selected;
            if (playing) ScrubAnimationController.SCRUB.remove(girl);
            else ScrubAnimationController.SCRUB.put(girl, playhead);
        }

        // the list
        int[] l = listBox();
        drawInset(g, l[0], l[1], l[2] - l[0], l[3] - l[1], 0xFF8B8B8B);
        List<String> list = listed();
        g.enableScissor(l[0], l[1], l[2], l[3]);
        for (int i = listScroll; i < list.size() && (i - listScroll) * ROW < l[3] - l[1]; i++) {
            int ry = l[1] + (i - listScroll) * ROW;
            String n = list.get(i);
            boolean sel = !copying && n.equals(selected);
            if (sel) g.fill(l[0], ry, l[2], ry + ROW, 0xFF5A5A9A);
            else if (mouseX >= l[0] && mouseX < l[2] && mouseY >= ry && mouseY < ry + ROW) g.fill(l[0], ry, l[2], ry + ROW, 0x40FFFFFF);
            String label = copying ? n.replace("animation.", "") : shortName(n);
            g.drawString(font, font.plainSubstrByWidth(label, LIST_W - 4), l[0] + 2, ry + 2, 0xFFFFFFFF, false);
        }
        g.disableScissor();
        if (list.isEmpty()) {
            g.drawString(font, Component.translatable(copying ? "editor.velvet.anim.none_found" : "editor.velvet.anim.none"), l[0] + 3, l[1] + 3, 0xFFE0E0E0, false);
        }

        // her, playing it
        int[] v = viewBox();
        drawInset(g, v[0], v[1], v[2] - v[0], v[3] - v[1], 0xFF2E3138);
        if (girl != null) {
            g.enableScissor(v[0], v[1], v[2], v[3]);
            girl.clientSelectedBone = bone;
            ModelView.render(g, girl, v[0], v[1], v[2], v[3], yaw, pitch, 1, 0, 0, 0.5F, 0.85F, true, null, partialTick);
            g.disableScissor();
        }
        if (status != null) g.drawString(font, font.plainSubstrByWidth(status.getString(), v[2] - v[0] - 6), v[0] + 3, v[1] + 3, 0xFFFFFFFF, true);

        // the timeline
        int[] t = timelineBox();
        drawInset(g, t[0], t[1], t[2] - t[0], t[3] - t[1], 0xFF3A3D44);
        double len = length();
        int ticks = (int) Math.ceil(len * 4);
        for (int i = 0; i <= ticks; i++) {
            double time = i / 4.0;
            if (time > len) break;
            int tx = xOf(time);
            g.fill(tx, t[1], tx + 1, t[1] + (i % 4 == 0 ? 8 : 4), 0xFF9A9A9A);
            if (i % 4 == 0) g.drawString(font, (int) time + "s", tx + 2, t[1] + 1, 0xFF9A9A9A, false);
        }
        for (int c = 0; c < 3; c++) {
            int ry = t[1] + 12 + c * 12;
            g.fill(t[0] + 1, ry + 5, t[2] - 1, ry + 6, 0x40FFFFFF);
            g.drawString(font, CHANNELS[c].substring(0, 1).toUpperCase(Locale.ROOT), t[0] + 1, ry + 1, CHANNEL_COLOURS[c], false);
            for (double k : keys(c).keySet()) {
                int kx = xOf(k);
                boolean sel = c == channel && selectedKey != null && Math.abs(k - selectedKey) < 1.0E-4;
                int col = sel ? 0xFFFFFFFF : CHANNEL_COLOURS[c];
                g.fill(kx - 2, ry + 3, kx + 3, ry + 8, 0xFF000000);
                g.fill(kx - 1, ry + 4, kx + 2, ry + 7, col);
            }
        }
        int px = xOf(playhead);
        g.fill(px, t[1], px + 1, t[3], 0xFFFFE080);
        g.drawString(font, String.format(Locale.ROOT, "%.2fs", playhead), Math.min(px + 3, t[2] - 30), t[3] - 10, 0xFFFFE080, false);

        // her bones
        int[] b = boneBox();
        drawInset(g, b[0], b[1], b[2] - b[0], b[3] - b[1], 0xFF8B8B8B);
        g.enableScissor(b[0], b[1], b[2], b[3]);
        for (int i = boneScroll; i < bones.size() && (i - boneScroll) * ROW < b[3] - b[1]; i++) {
            int ry = b[1] + (i - boneScroll) * ROW;
            String n = bones.get(i);
            if (n.equals(bone)) g.fill(b[0], ry, b[2], ry + ROW, 0xFF5A5A9A);
            boolean keyed = animation() != null && animation().has("bones") && animation().getAsJsonObject("bones").has(n);
            g.drawString(font, font.plainSubstrByWidth(n, SIDE_W - 10), b[0] + 2, ry + 2, keyed ? 0xFFFFE080 : 0xFFFFFFFF, false);
        }
        g.disableScissor();
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}

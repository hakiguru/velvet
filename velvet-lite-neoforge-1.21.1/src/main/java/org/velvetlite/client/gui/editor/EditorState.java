package org.velvetlite.client.gui.editor;

import org.jetbrains.annotations.Nullable;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.BoneTweaks;
import org.velvetlite.entity.girl.GirlBehavior;
import org.velvetlite.skin.GirlSkin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything the editor changes before "Save", shared by its tabs: name, bone changes (with undo and redo),
 * skin and behavior. The stand-in model in the editor always shows the current state.
 */
public final class EditorState {

    private static final int UNDO_LIMIT = 100;
    /** Changes to the same thing this close together are one undo step (a slider being dragged). */
    private static final long MERGE_MS = 600;

    public String name;
    public final Map<String, BoneTweaks.Tweak> tweaks;
    @Nullable public GirlSkin skin;
    public GirlBehavior behavior;
    public boolean dirty;

    /** The stand-in drawn in the model view, or null without a girl. */
    @Nullable public final AbstractGirlEntity preview;

    private final Deque<Map<String, BoneTweaks.Tweak>> undo = new ArrayDeque<>();
    private final Deque<Map<String, BoneTweaks.Tweak>> redo = new ArrayDeque<>();
    @Nullable private String lastChange;
    private long lastChangeAt;

    public EditorState(String name, BoneTweaks tweaks, @Nullable GirlSkin skin, GirlBehavior behavior,
                       @Nullable AbstractGirlEntity preview) {
        this.name = name;
        this.tweaks = tweaks.toMutableMap();
        this.skin = skin;
        this.behavior = behavior;
        this.preview = preview;
        updatePreview();
    }

    /** Call before changing bones; {@code what} groups quick repeated changes (null: always a new step). */
    public void beforeTweak(@Nullable String what) {
        long now = System.currentTimeMillis();
        if (what == null || !what.equals(lastChange) || now - lastChangeAt > MERGE_MS) {
            undo.push(copy(tweaks));
            while (undo.size() > UNDO_LIMIT) undo.removeLast();
            redo.clear();
        }
        lastChange = what;
        lastChangeAt = now;
    }

    public void tweaksChanged() {
        dirty = true;
        updatePreview();
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(copy(tweaks));
        restore(undo.pop());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(copy(tweaks));
        restore(redo.pop());
        return true;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    private void restore(Map<String, BoneTweaks.Tweak> state) {
        tweaks.clear();
        tweaks.putAll(state);
        lastChange = null;
        tweaksChanged();
    }

    public BoneTweaks.Tweak tweak(String bone) {
        return tweaks.computeIfAbsent(bone, b -> BoneTweaks.Tweak.identity());
    }

    public void setSkin(@Nullable GirlSkin skin) {
        this.skin = skin;
        dirty = true;
        updatePreview();
    }

    public void updatePreview() {
        if (preview == null) return;
        preview.clientPreviewTweaks = BoneTweaks.of(tweaks);
        preview.setSkin(skin);
        preview.setCustomNameString(name);
    }

    private static Map<String, BoneTweaks.Tweak> copy(Map<String, BoneTweaks.Tweak> map) {
        Map<String, BoneTweaks.Tweak> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(k, v.copy()));
        return out;
    }
}

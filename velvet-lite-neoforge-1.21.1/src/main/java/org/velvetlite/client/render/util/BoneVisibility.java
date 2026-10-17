package org.velvetlite.client.render.util;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Temporarily limits a render pass to some bones (with their sub-bones) and restores the previous
 * visibility afterwards. Other bones are hidden but still traversed, so animated bones deep in the
 * hierarchy (armor on the hips, the partner's head) get their transforms.
 */
public final class BoneVisibility {

    private final List<GeoBone> bones = new ArrayList<>();
    private final List<boolean[]> saved = new ArrayList<>();

    private BoneVisibility() {}

    /** Shows only bones matching {@code wanted} and everything below them. Call {@link #restore()} after rendering. */
    public static BoneVisibility showOnly(BakedGeoModel model, Predicate<GeoBone> wanted) {
        BoneVisibility state = new BoneVisibility();
        for (GeoBone root : model.topLevelBones()) state.apply(root, wanted, false);
        return state;
    }

    private void apply(GeoBone bone, Predicate<GeoBone> wanted, boolean parentWanted) {
        bones.add(bone);
        saved.add(new boolean[]{bone.isHidden(), bone.isHidingChildren()});
        boolean show = parentWanted || wanted.test(bone);
        bone.setHidden(!show);
        bone.setChildrenHidden(false);
        for (GeoBone child : bone.getChildBones()) apply(child, wanted, show);
    }

    /** Hides one bone's own cubes in this pass (its children stay as they are). */
    public BoneVisibility hideCubes(BakedGeoModel model, String name) {
        model.getBone(name).ifPresent(b -> {
            b.setHidden(true);
            b.setChildrenHidden(false);
        });
        return this;
    }

    /** Hides a bone together with everything below it in this pass. */
    public BoneVisibility hideTree(BakedGeoModel model, String name) {
        model.getBone(name).ifPresent(b -> b.setHidden(true));
        return this;
    }

    public void restore() {
        for (int i = 0; i < bones.size(); i++) {
            GeoBone bone = bones.get(i);
            bone.setHidden(saved.get(i)[0]);
            bone.setChildrenHidden(saved.get(i)[1]);
        }
    }
}

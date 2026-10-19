package org.velvet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlManager;

/**
 * Camera views during a scene, switched with a key: the partner's own eyes (in the model's head), and two
 * side views looking at the point between both bodies. A side view starts at its preset angle, then the mouse
 * orbits it freely around that point, like third person does around the player. The side views move the
 * vanilla camera (CameraMixin).
 */
public final class SceneCamera {

    public enum View {
        /** Through the eyes of the model's "steve" head; the vanilla first/third person setting still applies. */
        EYES(0, 0, 0),
        SIDE(2.4, 0.5, 0.15),
        /** The other side, close up and a little lower. */
        OTHER_SIDE(-1.9, 0.45, 0.35);

        /** Sideways distance from the focus (to the girl's right when positive). */
        final double side;
        final double up;
        /** Towards the girl's front, so the view isn't exactly square-on. */
        final double forward;

        View(double side, double up, double forward) {
            this.side = side;
            this.up = up;
            this.forward = forward;
        }

        public Component label() {
            return Component.translatable("gui.velvet.camera." + name().toLowerCase());
        }
    }

    /** Height of the focus above the girl's feet: between hips and heads, so both stay in the frame. */
    private static final double FOCUS_HEIGHT = 1.05;
    /** How far down the partner looks when put into a scene. */
    private static final float START_PITCH = 25.0F;
    /** Keeps the camera this far in front of a wall it would otherwise end up in. */
    private static final double WALL_MARGIN = 0.25;

    /** Keeps the orbit from flipping over the top or going under the floor. */
    private static final float MAX_ORBIT_PITCH = 80.0F;

    private static View view = View.EYES;
    @Nullable private static AbstractGirlEntity girl;
    /** Where the player looked when the side view was picked: turning away from it orbits the camera. */
    private static float refYaw;
    private static float refPitch;

    private SceneCamera() {}

    /** Called every client tick: a new scene starts in the partner's eyes again. */
    static void tick(Minecraft mc) {
        AbstractGirlEntity current = mc.player == null ? null : GirlManager.attachedTo(mc.player);
        if (current != girl) {
            view = View.EYES;
            if (current != null && girl == null) lookAlongPartnerBody(mc, current);
        }
        girl = current;
    }

    /**
     * When the player is put into the model's body, his view turns the way that body faces (towards her;
     * in doggy he stands behind her, facing the same way she does), looking a little down.
     */
    private static void lookAlongPartnerBody(Minecraft mc, AbstractGirlEntity g) {
        boolean behindHer = org.velvet.entity.girl.scene.GirlSceneController.partnerBehind(g.getCurrentAction());
        float yaw = g.getYRot() + (behindHer ? 0 : 180);
        mc.player.setYRot(yaw);
        mc.player.setYHeadRot(yaw);
        mc.player.setXRot(START_PITCH);
        mc.player.yRotO = yaw;
        mc.player.xRotO = START_PITCH;
    }

    static void next() {
        if (girl == null) return;
        view = View.values()[(view.ordinal() + 1) % View.values().length];
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            refYaw = mc.player.getYRot();
            refPitch = mc.player.getXRot();
        }
    }

    /** Whether the partner's head turns where the local player looks: in the side views the mouse moves the camera. */
    public static boolean headFollowsLook() {
        return view == View.EYES;
    }

    public static View view() {
        return view;
    }

    /** Whether the local player sees the scene from the model's head right now. */
    public static boolean isLookingThroughPartnerEyes() {
        return girl != null && view == View.EYES && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    /** Position and rotation of the camera when a side view replaces the vanilla one. */
    public record Pose(Vec3 position, float yaw, float pitch) {}

    /** The side view camera for this frame, or null to keep the vanilla camera. */
    @Nullable
    public static Pose sideView(Entity cameraEntity, float partialTick) {
        AbstractGirlEntity g = girl;
        if (g == null || view == View.EYES || cameraEntity != Minecraft.getInstance().player || g.isRemoved()) return null;

        float yaw = g.getViewYRot(partialTick);
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 focus = g.clientSceneFocus;
        if (focus == null) {
            // not rendered yet: between her and the partner, who stands right behind her in doggy, in front otherwise
            double towardsPartner = org.velvet.entity.girl.scene.GirlSceneController.partnerBehind(g.getCurrentAction()) ? 0.2 : 0.6;
            focus = g.getPosition(partialTick).add(forward.scale(towardsPartner)).add(0, FOCUS_HEIGHT, 0);
        }

        // the preset angle, turned by however far the player has turned since picking the view
        Vec3 preset = right.scale(view.side).add(forward.scale(view.forward)).add(0, view.up, 0);
        double distance = preset.length();
        // looking back from the preset spot towards the focus
        float baseYaw = (float) (Mth.atan2(preset.x, -preset.z) * Mth.RAD_TO_DEG);
        float basePitch = (float) (Mth.atan2(preset.y, preset.horizontalDistance()) * Mth.RAD_TO_DEG);
        Entity player = cameraEntity;
        float camYaw = baseYaw + player.getViewYRot(partialTick) - refYaw;
        float camPitch = Mth.clamp(basePitch + player.getViewXRot(partialTick) - refPitch, -MAX_ORBIT_PITCH, MAX_ORBIT_PITCH);

        Vec3 wanted = focus.subtract(Vec3.directionFromRotation(camPitch, camYaw).scale(distance));
        return new Pose(clipToWalls(g, focus, wanted), camYaw, camPitch);
    }

    private static Vec3 clipToWalls(AbstractGirlEntity g, Vec3 focus, Vec3 wanted) {
        HitResult hit = g.level().clip(new ClipContext(focus, wanted, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, g));
        if (hit.getType() == HitResult.Type.MISS) return wanted;
        Vec3 dir = wanted.subtract(focus).normalize();
        Vec3 pos = hit.getLocation().subtract(dir.scale(WALL_MARGIN));
        // never closer than half a block, even with a wall right next to the scene
        return pos.distanceTo(focus) < 0.5 ? focus.add(dir.scale(0.5)) : pos;
    }
}

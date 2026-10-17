package org.velvetlite.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.GirlManager;

/**
 * Camera views during a scene, switched with a key: the partner's own eyes (in the model's head), and two
 * fixed side views looking at where the action is. The side views move the vanilla camera (CameraMixin).
 */
public final class SceneCamera {

    public enum View {
        /** Through the eyes of the model's "steve" head; the vanilla first/third person setting still applies. */
        EYES(0, 0, 0),
        SIDE(2.6, 0.5, 0.15),
        /** The other side, closer and a little lower. */
        OTHER_SIDE(-2.0, 0.3, 0.35);

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
            return Component.translatable("gui.velvet_lite.camera." + name().toLowerCase());
        }
    }

    /** Height of the focus above the girl's feet: between hips and heads, so both stay in the frame. */
    private static final double FOCUS_HEIGHT = 1.05;
    /** How far down the partner looks when put into a scene. */
    private static final float START_PITCH = 25.0F;
    /** Keeps the camera this far in front of a wall it would otherwise end up in. */
    private static final double WALL_MARGIN = 0.25;

    private static View view = View.EYES;
    @Nullable private static AbstractGirlEntity girl;

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
        boolean behindHer = g.getCurrentAction().name().startsWith("DOGGY");
        float yaw = g.getYRot() + (behindHer ? 0 : 180);
        mc.player.setYRot(yaw);
        mc.player.setYHeadRot(yaw);
        mc.player.setXRot(START_PITCH);
        mc.player.yRotO = yaw;
        mc.player.xRotO = START_PITCH;
    }

    static void next() {
        if (girl != null) view = View.values()[(view.ordinal() + 1) % View.values().length];
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
        // between her and the partner: he stands right behind her in doggy, in front of her otherwise
        double towardsPartner = g.getCurrentAction().name().startsWith("DOGGY") ? 0.2 : 0.6;
        Vec3 focus = g.getPosition(partialTick).add(forward.scale(towardsPartner)).add(0, FOCUS_HEIGHT, 0);

        Vec3 wanted = focus.add(right.scale(view.side)).add(forward.scale(view.forward)).add(0, view.up, 0);
        Vec3 position = clipToWalls(g, focus, wanted);

        Vec3 look = focus.subtract(position);
        float camYaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
        float camPitch = (float) -(Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG);
        return new Pose(position, camYaw, camPitch);
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

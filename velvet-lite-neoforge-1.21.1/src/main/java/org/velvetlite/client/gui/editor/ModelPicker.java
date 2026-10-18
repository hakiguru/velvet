package org.velvetlite.client.gui.editor;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where each bone of the model ended up on screen while the editor drew it: the faces of its cubes, to pick
 * a bone by clicking on it, and its pivot with its three axes, for the move/turn/scale handles. Filled by the
 * renderer while {@link #active} is set (the editor's view is drawn straight into GUI coordinates, so the
 * transformed positions are screen pixels, with a bigger z nearer to the viewer).
 */
public final class ModelPicker {

    /** Set by the editor around drawing its view. */
    @Nullable public static ModelPicker active;

    private record Face(String bone, float[] x, float[] y, float z) {}

    /** A bone's pivot on screen and where its x, y and z axes point (screen pixels per model pixel). */
    public record Pivot(float x, float y, float[] axisX, float[] axisY) {}

    private final List<Face> faces = new ArrayList<>();
    private final Map<String, Pivot> pivots = new HashMap<>();
    private final Map<String, Pivot> parentPivots = new HashMap<>();

    public void clear() {
        faces.clear();
        pivots.clear();
        parentPivots.clear();
    }

    /**
     * Called with the bone's matrix in place. {@code parentPose} is the matrix the bone's own movement
     * happens in (for the move handles, which shift it in its parent's space).
     */
    public void capture(PoseStack poseStack, GeoBone bone, @Nullable Matrix4f parentPose) {
        Matrix4f pose = poseStack.last().pose();
        float px = bone.getPivotX() / 16, py = bone.getPivotY() / 16, pz = bone.getPivotZ() / 16;
        pivots.put(bone.getName(), pivot(pose, px, py, pz));
        if (parentPose != null) parentPivots.put(bone.getName(), pivot(parentPose, px, py, pz));
        for (GeoCube cube : bone.getCubes()) {
            poseStack.pushPose();
            RenderUtil.translateToPivotPoint(poseStack, cube);
            RenderUtil.rotateMatrixAroundCube(poseStack, cube);
            RenderUtil.translateAwayFromPivotPoint(poseStack, cube);
            Matrix4f m = poseStack.last().pose();
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) continue;
                GeoVertex[] v = quad.vertices();
                float[] xs = new float[v.length], ys = new float[v.length];
                float z = 0;
                for (int i = 0; i < v.length; i++) {
                    Vector4f p = m.transform(new Vector4f(v[i].position().x(), v[i].position().y(), v[i].position().z(), 1));
                    xs[i] = p.x;
                    ys[i] = p.y;
                    z += p.z / v.length;
                }
                faces.add(new Face(bone.getName(), xs, ys, z));
            }
            poseStack.popPose();
        }
    }

    private static Pivot pivot(Matrix4f m, float px, float py, float pz) {
        Vector4f o = m.transform(new Vector4f(px, py, pz, 1));
        float unit = 1 / 16F;
        Vector4f ax = m.transform(new Vector4f(px + unit, py, pz, 1));
        Vector4f ay = m.transform(new Vector4f(px, py + unit, pz, 1));
        Vector4f az = m.transform(new Vector4f(px, py, pz + unit, 1));
        return new Pivot(o.x, o.y, new float[]{ax.x - o.x, ay.x - o.x, az.x - o.x}, new float[]{ax.y - o.y, ay.y - o.y, az.y - o.y});
    }

    /** The bone whose face is nearest to the viewer under the point, or null. */
    @Nullable
    public String pick(double x, double y) {
        Face best = null;
        for (Face f : faces) {
            if ((best == null || f.z > best.z) && contains(f, x, y)) best = f;
        }
        return best == null ? null : best.bone;
    }

    @Nullable
    public Pivot pivot(String bone) {
        return pivots.get(bone);
    }

    /** The bone's pivot with the axes of the space its offset moves it in. */
    @Nullable
    public Pivot parentPivot(String bone) {
        Pivot p = parentPivots.get(bone);
        return p != null ? p : pivots.get(bone);
    }

    private static boolean contains(Face f, double x, double y) {
        boolean inside = false;
        int n = f.x.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            if ((f.y[i] > y) != (f.y[j] > y)
                    && x < (f.x[j] - f.x[i]) * (y - f.y[i]) / (f.y[j] - f.y[i] + 1.0E-9) + f.x[i]) {
                inside = !inside;
            }
        }
        return inside;
    }
}

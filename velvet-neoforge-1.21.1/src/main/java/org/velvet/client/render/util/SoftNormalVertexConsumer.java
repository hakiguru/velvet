package org.velvet.client.render.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Blends every normal towards the best lit direction before passing it on: world up in the world, the
 * middle of the two inventory lights in a GUI preview. Neighbouring faces of differently rotated cubes end
 * up with similar brightness and the seams between them stop standing out.
 */
public final class SoftNormalVertexConsumer implements VertexConsumer {

    public static final Vector3fc WORLD_UP = new Vector3f(0, 1, 0);
    /**
     * Halfway between vanilla's two inventory lights (Lighting.INVENTORY_DIFFUSE_LIGHT_0/1), in the space the
     * GUI preview's normals are in: a face turned this way gets both lights fully.
     */
    public static final Vector3fc INVENTORY_LIGHT = new Vector3f(0.2F, -1.0F, 1.0F).normalize()
            .add(new Vector3f(-0.2F, -1.0F, 0.0F).normalize()).normalize();

    private VertexConsumer delegate;
    private float softness;
    private Vector3fc target = WORLD_UP;

    public SoftNormalVertexConsumer wrap(VertexConsumer delegate, float softness, Vector3fc target) {
        this.delegate = delegate;
        this.softness = softness;
        this.target = target;
        return this;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
        float nx = normalX * (1 - softness) + target.x() * softness;
        float ny = normalY * (1 - softness) + target.y() * softness;
        float nz = normalZ * (1 - softness) + target.z() * softness;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-4F) {
            nx = target.x();
            ny = target.y();
            nz = target.z();
        } else {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        delegate.setNormal(nx, ny, nz);
        return this;
    }

    // BufferBuilder overrides this bulk method for speed; route it through setNormal's blending
    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v,
                          int packedOverlay, int packedLight, float normalX, float normalY, float normalZ) {
        addVertex(x, y, z);
        setColor(color);
        setUv(u, v);
        setOverlay(packedOverlay);
        setLight(packedLight);
        setNormal(normalX, normalY, normalZ);
    }
}

package org.velvetlite.client.render.util;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Blends every normal towards world up before passing it on. Entity lighting is evaluated in world space,
 * so neighbouring faces of differently rotated cubes end up with similar brightness and the seams between
 * them stop standing out.
 */
public final class SoftNormalVertexConsumer implements VertexConsumer {

    private VertexConsumer delegate;
    private float softness;

    public SoftNormalVertexConsumer wrap(VertexConsumer delegate, float softness) {
        this.delegate = delegate;
        this.softness = softness;
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
        float nx = normalX * (1 - softness);
        float ny = normalY * (1 - softness) + softness;
        float nz = normalZ * (1 - softness);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-4F) {
            nx = 0;
            ny = 1;
            nz = 0;
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

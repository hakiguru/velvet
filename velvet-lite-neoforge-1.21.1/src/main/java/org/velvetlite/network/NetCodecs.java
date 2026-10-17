package org.velvetlite.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

public final class NetCodecs {
    private NetCodecs() {}

    public static final StreamCodec<FriendlyByteBuf, Vec3> VEC3_CODEC =
            new StreamCodec<>() {
                @Override
                public Vec3 decode(FriendlyByteBuf buf) {
                    return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                }

                @Override
                public void encode(FriendlyByteBuf buf, Vec3 v) {
                    buf.writeDouble(v.x);
                    buf.writeDouble(v.y);
                    buf.writeDouble(v.z);
                }
            };
}

package org.velvet.skin;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Checks on skin files shared by both sides: size limits, the PNG header, and the hash they're known by. */
public final class SkinFiles {

    private SkinFiles() {}

    /** Bigger files are refused: a 2048x2048 texture fits easily, the 4096 one of the model compressed too. */
    public static final int MAX_BYTES = 6 * 1024 * 1024;
    public static final int MAX_SIDE = 4096;
    /** Upload and download pieces: under the 32 KiB a client may send in one packet. */
    public static final int CHUNK = 30000;

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Null if it looks like a PNG of an allowed size, else what's wrong with it. */
    @Nullable
    public static String problem(byte[] data) {
        if (data.length > MAX_BYTES) return "file too big (max " + MAX_BYTES / 1024 / 1024 + " MB)";
        if (data.length < 33) return "not a PNG image";
        for (int i = 0; i < SIGNATURE.length; i++) if (data[i] != SIGNATURE[i]) return "not a PNG image";
        ByteBuffer header = ByteBuffer.wrap(data, 16, 8);
        int width = header.getInt(), height = header.getInt();
        if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE) {
            return "image must be at most " + MAX_SIDE + "x" + MAX_SIDE;
        }
        if (width != height) return "image must be square, like her texture";
        return null;
    }
}

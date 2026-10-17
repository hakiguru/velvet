package org.velvetlite.entity.girl;

import org.jetbrains.annotations.Nullable;

public enum GirlType {

    //         id         spawnEggColor  editorId
    ELSIE    ("elsie",     0x8A00B5,     0);

    private final String id;
    private final int spawnEggColor;
    private final int editorId;
    private final String assetFolder;

    GirlType(String id, int spawnEggColor, int editorId) {
        this(id, spawnEggColor, editorId, id);
    }

    GirlType(String id, int spawnEggColor, int editorId, String assetFolder) {
        this.id = id;
        this.spawnEggColor = spawnEggColor;
        this.editorId = editorId;
        this.assetFolder = assetFolder;
    }

    public String id()                  { return id; }
    public int    spawnEggColor()       { return spawnEggColor; }
    public int    editorId()            { return editorId; }
    /** Folder under geo/, textures/entity/ and animations/; also the animation name prefix. */
    public String assetFolder()         { return assetFolder; }

    @Nullable
    public static GirlType byId(String id) {
        for (GirlType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    public String registryName() {
        return id;
    }
}
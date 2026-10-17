package org.velvetlite.clothing;

import net.minecraft.resources.ResourceLocation;
import org.velvetlite.entity.girl.GirlType;

import java.util.HashSet;
import java.util.Set;

public class ClothingData {

    private String id;
    private String boneName;
    private ClothingSlot slot = ClothingSlot.CUSTOM_BONE;
    private Set<GirlType> allowedGirls = new HashSet<>();
    private Set<String> hiddenBones = new HashSet<>();
    private boolean enabled = true;
    private ClothingLighting lighting = ClothingLighting.DEFAULT;
    private float offsetX, offsetY;
    private ResourceLocation geoModel;
    private ResourceLocation texture;
    private float scale = 1.0F;

    public String getId()                { return id; }
    public void setId(String id)         { this.id = id; }
    public String getBoneName()          { return boneName; }
    public void setBoneName(String b)    { this.boneName = b; }
    public ClothingSlot getSlot()        { return slot; }
    public void setSlot(ClothingSlot s)  { this.slot = s; }
    public Set<GirlType> getAllowedGirls() { return allowedGirls; }
    public Set<String> getHiddenBones()  { return hiddenBones; }
    public boolean isEnabled()           { return enabled; }
    public void setEnabled(boolean b)    { this.enabled = b; }
    public ClothingLighting getLighting(){ return lighting; }
    public void setLighting(ClothingLighting l) { this.lighting = l; }
    public float getOffsetX()            { return offsetX; }
    public void setOffsetX(float x)      { this.offsetX = x; }
    public float getOffsetY()            { return offsetY; }
    public void setOffsetY(float y)      { this.offsetY = y; }
    public ResourceLocation getGeoModel(){ return geoModel; }
    public void setGeoModel(ResourceLocation r) { this.geoModel = r; }
    public ResourceLocation getTexture() { return texture; }
    public void setTexture(ResourceLocation r) { this.texture = r; }
    public float getScale()              { return scale; }
    public void setScale(float s)        { this.scale = s; }
}
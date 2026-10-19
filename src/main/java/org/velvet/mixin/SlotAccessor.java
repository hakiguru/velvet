package org.velvet.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a slot is drawn: the NPC's menu lays its slots out again when its window is resized. */
@Mixin(Slot.class)
public interface SlotAccessor {

    @Mutable
    @Accessor("x")
    void velvet$setX(int x);

    @Mutable
    @Accessor("y")
    void velvet$setY(int y);
}

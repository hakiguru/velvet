package org.velvet.entity.girl.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.velvet.entity.girl.AbstractGirlEntity;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a girl's situation is while she decides what to keep: her mood, and where she is. Priorities
 * ({@link LootPriority}) read this to move items up or down.
 */
public record LootContext(AbstractGirlEntity girl, Mood mood, Set<Place> places) {

    public enum Mood { CALM, HUNGRY, STARVING, IN_DANGER }

    public enum Place { SURFACE, CAVE, NETHER, END, UNDERWATER, NIGHT }

    /** Hungrier than this (food points), food is worth more to her. */
    public static final int HUNGRY_BELOW = 14;
    /** At or below this, food is worth more than almost anything. */
    public static final int STARVING_AT = 6;

    public static LootContext of(AbstractGirlEntity girl) {
        Mood mood = girl.isInDanger() ? Mood.IN_DANGER
                : girl.getFood() <= STARVING_AT ? Mood.STARVING
                : girl.getFood() < HUNGRY_BELOW ? Mood.HUNGRY : Mood.CALM;
        return new LootContext(girl, mood, places(girl));
    }

    private static Set<Place> places(AbstractGirlEntity girl) {
        Level level = girl.level();
        Set<Place> places = EnumSet.noneOf(Place.class);
        if (level.dimension() == Level.NETHER) places.add(Place.NETHER);
        else if (level.dimension() == Level.END) places.add(Place.END);
        else {
            BlockPos pos = girl.blockPosition();
            boolean covered = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) > pos.getY() + 4;
            places.add(covered && !level.canSeeSky(pos) && pos.getY() < level.getSeaLevel() ? Place.CAVE : Place.SURFACE);
            if (level.isNight()) places.add(Place.NIGHT);
        }
        if (girl.isUnderWater()) places.add(Place.UNDERWATER);
        return places;
    }

    public boolean is(Place place) {
        return places.contains(place);
    }
}

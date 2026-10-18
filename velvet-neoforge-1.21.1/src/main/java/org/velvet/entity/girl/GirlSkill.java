package org.velvet.entity.girl;

/**
 * Skills a girl levels up by doing things with the player. Levels go from 0 to {@link #MAX_LEVEL}: each one
 * fills its own bar, which then starts over for the next. Experience is synced to clients for the menu and
 * saved by name, so the order here is only the order in the menu.
 * <ul>
 *   <li>ENDURANCE: damage she survives, running and finished scenes. +2 max health per level; longer scenes,
 *       longer runs and longer without food.</li>
 *   <li>AFFECTION: finished scenes and time spent near her owner; groundwork for relationships.</li>
 *   <li>LUST: finished scenes.</li>
 *   <li>CAPACITY: finished scenes. Her inventory: 1 slot at level 0, up to 27 at level 10. Later also how
 *       many players a group scene takes and which toys she can use.</li>
 *   <li>AGILITY: distance walked; +3% movement speed per level.</li>
 *   <li>WEAPONS: hits she lands on monsters.</li>
 *   <li>The rest have no way to grow yet: they come with the activities they're about (fighting, building,
 *       crafting...).</li>
 * </ul>
 */
public enum GirlSkill {
    ENDURANCE(0xFFD04848, true),
    AFFECTION(0xFFE0609A, true),
    LUST(0xFFB04CD8, true),
    CAPACITY(0xFFC8963C, true),
    KINDNESS(0xFF5CC878, false),
    AGGRESSION(0xFF8C2A2A, false),
    BUILDING(0xFF9C7A54, false),
    WEAPONS(0xFF9AA4B4, true),
    TOOLS(0xFF4C8CA8, false),
    AGILITY(0xFF48A8E0, true);

    public static final int MAX_LEVEL = 10;

    /** Bar colour in the menu. */
    public final int color;
    /** Whether anything gives experience for it yet; the menu says so for those that don't. */
    public final boolean grows;

    GirlSkill(int color, boolean grows) {
        this.color = color;
        this.grows = grows;
    }

    /** Total experience needed to reach {@code level}: level 1 needs 50, each next one 50 more than the last. */
    public static int xpForLevel(int level) {
        return 25 * level * (level + 1);
    }

    public static int levelFor(int xp) {
        int level = 0;
        while (level < MAX_LEVEL && xp >= xpForLevel(level + 1)) level++;
        return level;
    }

    public String translationKey() {
        return "skill.velvet." + name().toLowerCase();
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }
}

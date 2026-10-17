package org.velvetlite.entity.girl;

/**
 * Skills a girl levels up by doing things with the player. Experience is synced to clients for the menu.
 * <ul>
 *   <li>AFFECTION: finished scenes and time spent near her owner; groundwork for relationships.</li>
 *   <li>ENDURANCE: damage she survives; +2 max health per level.</li>
 *   <li>AGILITY: distance walked; +3% movement speed per level.</li>
 * </ul>
 */
public enum GirlSkill {
    AFFECTION(0xFFE0609A),
    ENDURANCE(0xFFD04848),
    AGILITY(0xFF48A8E0);

    public static final int MAX_LEVEL = 10;

    /** Bar colour in the menu. */
    public final int color;

    GirlSkill(int color) {
        this.color = color;
    }

    /** Total experience needed to reach {@code level} (level 1 needs none, 2 needs 50, 10 needs 2250). */
    public static int xpForLevel(int level) {
        return 25 * level * (level - 1);
    }

    public static int levelFor(int xp) {
        int level = 1;
        while (level < MAX_LEVEL && xp >= xpForLevel(level + 1)) level++;
        return level;
    }

    public String translationKey() {
        return "skill.velvet_lite." + name().toLowerCase();
    }
}

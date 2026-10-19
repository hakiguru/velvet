package org.velvet.entity.girl.psyche;

import java.util.Locale;

/**
 * A trait of her character, from 0.01 (hardly at all) to 1.00 (through and through), two decimals; 0 never
 * happens. Each new NPC rolls all of them (see {@link Personality#random}), which gives far more different
 * characters than there will ever be NPCs. They decide how strongly she reacts to what happens to her (see
 * {@link Psyche}) and how she comes across to the language models.
 * <p>
 * They're rolled from the five big factors psychologists describe people with (how outgoing, how even-tempered,
 * how agreeable, how conscientious and how open someone is), so they go together like in real people: an
 * anxious girl tends to be touchy too, a warm one empathic; each trait still has a good deal of its own.
 */
public enum Trait {
    // how outgoing
    SOCIABILITY(Factor.EXTRAVERSION, 0.8),
    PLAYFULNESS(Factor.EXTRAVERSION, 0.6),
    SHYNESS(Factor.EXTRAVERSION, -0.7),
    IMPULSIVENESS(Factor.EXTRAVERSION, 0.4),
    // how even-tempered (these go up with how unsteady she is)
    ANXIOUSNESS(Factor.NEUROTICISM, 0.8),
    MELANCHOLY(Factor.NEUROTICISM, 0.6),
    SENSITIVITY(Factor.NEUROTICISM, 0.7),
    TEMPER(Factor.NEUROTICISM, 0.5),
    JEALOUSY(Factor.NEUROTICISM, 0.4),
    OPTIMISM(Factor.NEUROTICISM, -0.6),
    RESILIENCE(Factor.NEUROTICISM, -0.7),
    COURAGE(Factor.NEUROTICISM, -0.5),
    // how agreeable
    WARMTH(Factor.AGREEABLENESS, 0.8),
    EMPATHY(Factor.AGREEABLENESS, 0.7),
    TRUST(Factor.AGREEABLENESS, 0.5),
    PATIENCE(Factor.AGREEABLENESS, 0.5),
    LOYALTY(Factor.AGREEABLENESS, 0.4),
    STUBBORNNESS(Factor.AGREEABLENESS, -0.5),
    SARCASM(Factor.AGREEABLENESS, -0.4),
    PRIDE(Factor.AGREEABLENESS, -0.3),
    // how conscientious
    DILIGENCE(Factor.CONSCIENTIOUSNESS, 0.8),
    TIDINESS(Factor.CONSCIENTIOUSNESS, 0.6),
    MODESTY(Factor.CONSCIENTIOUSNESS, 0.4),
    SQUEAMISHNESS(Factor.CONSCIENTIOUSNESS, 0.3),
    // how open
    CURIOSITY(Factor.OPENNESS, 0.8),
    ROMANTICISM(Factor.OPENNESS, 0.5),
    INDEPENDENCE(Factor.OPENNESS, 0.4),
    GREED(Factor.OPENNESS, -0.3);

    public enum Factor { EXTRAVERSION, NEUROTICISM, AGREEABLENESS, CONSCIENTIOUSNESS, OPENNESS }

    /** The factor it comes from, and how much of it is that factor (negative: the opposite). */
    public final Factor factor;
    public final double loading;

    Trait(Factor factor, double loading) {
        this.factor = factor;
        this.loading = loading;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "trait.velvet." + id();
    }

    public static Trait byId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

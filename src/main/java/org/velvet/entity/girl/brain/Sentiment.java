package org.velvet.entity.girl.brain;

import java.util.List;
import java.util.Locale;

/**
 * How warm a chat message is, from -1 (mean) to 1 (sweet), by the words in it (English and Russian). The
 * internal brain's way of telling a kind word from an insult; a language model judges that itself and says
 * how she feels with a /feel line.
 */
public final class Sentiment {

    private Sentiment() {}

    private static final List<String> WARM = List.of(
            "love you", "i love", "you're cute", "you are cute", "cute", "pretty", "beautiful", "gorgeous", "good girl",
            "well done", "good job", "great job", "thank you", "thanks", "proud of you", "you're the best", "best girl",
            "hug", "kiss", "sweetie", "darling", "honey", "don't be sad", "dont be sad", "it's okay", "it's ok", "i'm here",
            "люблю", "любимая", "милая", "милашка", "красивая", "красотка", "прекрасная", "умница", "молодец", "хорошая девочка",
            "спасибо", "благодарю", "горжусь", "лучшая", "обнимаю", "обнять", "целую", "солнышко", "зайка", "котик",
            "не грусти", "не плачь", "всё хорошо", "все хорошо", "я рядом", "я с тобой");
    private static final List<String> MEAN = List.of(
            "stupid", "idiot", "dumb", "ugly", "hate you", "shut up", "useless", "worthless", "bitch", "slut", "go away",
            "loser", "annoying", "disgusting",
            "дура", "дурочка", "тупая", "идиотка", "уродина", "ненавижу", "заткнись", "бесполезная", "сука", "шлюха",
            "отвали", "пошла вон", "бесишь", "мерзкая", "противная", "тварь");

    public static float of(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        int warm = 0, mean = 0;
        for (String w : WARM) if (t.contains(w)) warm++;
        for (String w : MEAN) if (t.contains(w)) mean++;
        if (warm == 0 && mean == 0) return 0;
        return Math.max(-1, Math.min(1, (warm - mean * 1.5F) / 2.0F));
    }
}

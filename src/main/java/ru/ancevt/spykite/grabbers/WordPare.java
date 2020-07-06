package ru.ancevt.spykite.grabbers;

import ru.ancevt.util.string.ToStringBuilder;

/**
 *
 * @author ancevt
 */
public class WordPare {

    public final String word;
    public final boolean skip;

    public WordPare(boolean skip, String word) {
        this.word = word;
        this.skip = skip;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("skip", skip)
            .append("word", word)
            .build();
    }

}

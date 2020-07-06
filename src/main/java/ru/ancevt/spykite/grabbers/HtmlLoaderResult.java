package ru.ancevt.spykite.grabbers;

import ru.ancevt.util.string.ToStringBuilder;

/**
 *
 * @author ancevt
 */
public class HtmlLoaderResult {

    private final HLState hlState;
    
    public HtmlLoaderResult() {
        this(new HLState());
    }

    public HtmlLoaderResult(HLState hlState) {
        this.hlState = hlState;
    }

    public boolean isSkip() {
        return hlState.skip;
    }
    
    public String getFailWord() {
        return hlState.failWordString;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("skip")
            .build();
    }

}

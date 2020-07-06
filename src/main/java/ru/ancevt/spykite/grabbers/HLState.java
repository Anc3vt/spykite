package ru.ancevt.spykite.grabbers;

/**
 *
 * @author ancevt
 */
public class HLState {

    public boolean byTimeout;
    public boolean byException;
    public boolean byFailedState;
    public boolean byUnknownError;
    public boolean byEarlyBrowserExiting;
    public boolean byFailWords;
    public boolean byProcessDead;
    
    public String failWordString;

    public boolean succeeded;
    public boolean foundByIncludingWords;

    public Throwable exception;
    public String html;
    
    public boolean skip;

    public boolean htmlReturned;

    public boolean isError() {
        return byTimeout
            || byException
            || byFailedState
            || byUnknownError
            || byEarlyBrowserExiting
            || byFailWords;
    }
}

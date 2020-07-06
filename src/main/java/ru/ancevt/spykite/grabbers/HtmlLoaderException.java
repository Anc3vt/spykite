package ru.ancevt.spykite.grabbers;

/**
 *
 * @author ancevt
 */
public class HtmlLoaderException extends Exception {
    
    private final HtmlLoaderResult result;
    
    public HtmlLoaderException(HtmlLoaderResult result) {
        super();
        this.result = result;
    }
    
    public HtmlLoaderException(String message, HtmlLoaderResult result) {
        super(message);
        this.result = result;
    }
    
    public HtmlLoaderException(String message) {
        super(message);
        result = new HtmlLoaderResult();
    }
    
    public HtmlLoaderException(Throwable cause, HtmlLoaderResult result) {
        super(cause);
        this.result = result;
    }
    
    public HtmlLoaderException(String message, Throwable cause, HtmlLoaderResult result) {
        super(message, cause);
        this.result = result;
    }

    public HtmlLoaderResult getResult() {
        return result;
    }
}

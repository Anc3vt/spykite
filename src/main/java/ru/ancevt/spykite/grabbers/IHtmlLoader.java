package ru.ancevt.spykite.grabbers;

/**
 *
 * @author ancevt
 */
public interface IHtmlLoader {

    void setCacheDisabled(boolean cacheDisabled);
    
    boolean isCacheDisabled();
    
    void setUserAgent(String userAgent);
    
    String getUserAgent();
    
    void setPort(int port) throws HtmlLoaderException;

    int getPort();

    void setQuiet(boolean value);

    boolean isQuiet();

    void setUrl(String url) throws HtmlLoaderException;

    String getUrl();

    void setContent(String html) throws HtmlLoaderException;

    String getContent();

    void addIncludeWords(String... words);

    void addFailWords(boolean skip, String... words);

    int getRequestInterval();

    void setRequestInterval(int requestInterval);

    int getTimeout();

    void setTimeout(int timeout);

    boolean isSupressJavaErorrLogs();

    void setSupressJavaErrorLogs(boolean value);

    String loadHtml() throws HtmlLoaderException;

    boolean isHtmlLoaded();
    
    void shutdown() throws HtmlLoaderException;
    
    void prepareResources() throws HtmlLoaderException;
    
    String executeJavaScript(String javaScript) throws HtmlLoaderException;
    
    String getCurrentHtml();
    
    void setDebug(Object debug);
    
    Object getDebug();
}

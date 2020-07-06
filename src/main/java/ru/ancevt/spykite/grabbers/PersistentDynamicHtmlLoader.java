package ru.ancevt.spykite.grabbers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ru.ancevt.spykite.Log;

/**
 * @author ancevt
 */
public final class PersistentDynamicHtmlLoader implements IHtmlLoader {

    public static final int DEFAULT_ATTEMPTS = 10;

    private int port;
    private boolean quiet;
    private String url;
    private String content;
    private final List<String> includeWords;
    private final List<WordPare> failWords;
    private int requestInterval;
    private int timeout;
    private boolean supressJavaErrrorLogs;

    private int attempts;
    private int currentAttempts;

    public Object debug;
    private AutoDynamicHtmlLoader loader;
    private String userAgent;
    private boolean cacheDisabled;

    public PersistentDynamicHtmlLoader() {
        port = AutoDynamicHtmlLoader.DEFAULT_PORT;
        quiet = AutoDynamicHtmlLoader.DEFAULT_QUIET;
        timeout = AutoDynamicHtmlLoader.DEFAULT_TIMEOUT;
        requestInterval = AutoDynamicHtmlLoader.DEFAULT_HTML_REQUEST_INTERVAL;
        supressJavaErrrorLogs = AutoDynamicHtmlLoader.DEFAULT_SUPRESS_JAVA_ERROR_LOGS;
        attempts = DEFAULT_ATTEMPTS;

        currentAttempts = attempts;

        includeWords = new ArrayList<>();
        failWords = new ArrayList<>();

        loader = new AutoDynamicHtmlLoader();
    }

    public PersistentDynamicHtmlLoader(int port) {
        this();
        setPort(port);
    }

    public PersistentDynamicHtmlLoader(int port, boolean quiet) {
        this();
        setPort(port);
        setQuiet(quiet);
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = currentAttempts = attempts;
    }

    @Override
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void setQuiet(boolean value) {
        this.quiet = value;
    }

    @Override
    public boolean isQuiet() {
        return quiet;
    }

    @Override
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public void setContent(String html) {
        this.content = html;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public void addIncludeWords(String... words) {
        this.includeWords.addAll(Arrays.asList(words));
    }

    @Override
    public void addFailWords(boolean skip, String... words) {
        for (int i = 0; i < words.length; i++) {
            final WordPare pare = new WordPare(skip, words[i]);
            failWords.add(pare);
        }
    }

    @Override
    public int getRequestInterval() {
        return requestInterval;
    }

    @Override
    public void setRequestInterval(int requestInterval) {
        this.requestInterval = requestInterval;
    }

    @Override
    public int getTimeout() {
        return timeout;
    }

    @Override
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public boolean isSupressJavaErorrLogs() {
        return supressJavaErrrorLogs;
    }

    @Override
    public void setSupressJavaErrorLogs(boolean value) {
        supressJavaErrrorLogs = value;
    }

    @Override
    public boolean isHtmlLoaded() {
        return loader.isHtmlLoaded();
    }

    @Override
    public String getCurrentHtml() {
        return loader.getCurrentHtml();
    }

    @Override
    public String loadHtml() throws HtmlLoaderException {
        if (url != null && content != null) {
            throw new HtmlLoaderException("setUrl and setContent are both set", new HtmlLoaderResult(new HLState()));
        }

        loader = new AutoDynamicHtmlLoader();
        loader.setUserAgent(userAgent);
        loader.setCacheDisabled(cacheDisabled);
        loader.setDebug(debug);
        loader.setPort(port);
        loader.setQuiet(quiet);
        loader.setUrl(url);
        loader.setContent(content);
        loader.setRequestInterval(requestInterval);
        loader.setTimeout(timeout);
        loader.setSupressJavaErrorLogs(supressJavaErrrorLogs);

        includeWords.stream().forEach((word) -> {
            loader.addIncludeWords(word);
        });

        failWords.stream().forEach((pare) -> {
            loader.addFailWords(pare.skip, pare.word);
        });

        try {
            if (Log.hl.isInfoEnabled()) {
                Log.hl.info("Attempts left " + currentAttempts);
            }
            return loader.loadHtml();
        } catch (HtmlLoaderException ex) {
            currentAttempts--;

            if (currentAttempts > 0) {
                return loadHtml();
            } else {
                throw new HtmlLoaderException(ex, ex.getResult());
            }
        }
    }

    @Override
    public void shutdown() {
        // Not supported
    }

    @Override
    public void prepareResources() {
        // Not supported
    }

    @Override
    public String executeJavaScript(String javaScript) {
        // Not supported
        return null;
    }

    @Override
    public void setDebug(Object debug) {
        this.debug = debug;
    }

    @Override
    public Object getDebug() {
        return debug;
    }

    @Override
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public String getUserAgent() {
        return userAgent;
    }
    
    @Override
    public void setCacheDisabled(boolean cacheDisabled) {
        this.cacheDisabled = cacheDisabled;
    }

    @Override
    public boolean isCacheDisabled() {
        return cacheDisabled;
    }
}

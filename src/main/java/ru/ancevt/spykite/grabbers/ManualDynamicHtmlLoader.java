package ru.ancevt.spykite.grabbers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import ru.ancevt.spykite.GlobalInfo;
import ru.ancevt.spykite.Log;
import ru.ancevt.spykite.skfx.SpykiteClient;
import ru.ancevt.spykite.skfx.SpykiteClientListener;
import ru.ancevt.spykite.skfx.SpykiteJarPath;
import ru.ancevt.spykite.skfx.SpykiteMessage;
import ru.ancevt.util.string.StringUtil;
import ru.ancevt.util.system.UnixDisplay;

/**
 *
 * @author ancevt
 */
public final class ManualDynamicHtmlLoader implements IHtmlLoader, SpykiteClientListener {

    public static void main(String[] args) throws HtmlLoaderException, InterruptedException {
        //FrontendHtmlExtractor.setBrowserJarPath("/home/ancevt/Software/WebBrowserFX/browser.jar");
        final IHtmlLoader loader = new ManualDynamicHtmlLoader(7000, false);

        loader.setTimeout(60000);
        loader.addIncludeWords("_025a50318d--title--2hdC7");

        loader.prepareResources();
        loader.setUrl("https://cian.ru");
        System.out.println("html length: " + loader.loadHtml().length());

        System.out.println("program done");
        Thread.sleep(20000000);

        loader.shutdown();
    }

    public static final int DEFAULT_TIMEOUT = 60000; // ms
    public static final int DEFAULT_HTML_REQUEST_INTERVAL = 1000;
    public static final int DEFAULT_PORT = 7777;
    public static final boolean DEFAULT_QUIET = true;
    public static final int MAX_WAITING_FOR_SERVER_ATTEMPTS = 20;
    public static final boolean DEFAULT_SUPRESS_JAVA_ERROR_LOGS = true;

    private static int idCounter;

    public Object debug;

    private final Object waitInitLock = new Object();
    private final Object waitSetUrlLock = new Object();
    private final Object waitHtmlLock = new Object();
    private final Object waitJSLock = new Object();
    private final Object waitShutdownLock = new Object();

    private final int id;
    private int port;
    private String url;
    private String content;
    private boolean quiet;

    private final HLState hlState;
    private SpykiteClient spykiteClient;
    private final List<String> includeWords;
    private final List<WordPare> failWords;
    private Process process;
    private Thread timeoutThread;
    private volatile boolean done;
    private int timeout;
    private int htmlRequestInterval;
    private boolean browserClientInited;
    private int waitingForServerCounter;
    private String currentHtml;
    private final long startTime;

    private boolean supressJavaErrorLogs;
    private Object javaScriptResult;

    private final ErrorSimulator errorSimulator;
    private String userAgent;
    private boolean cacheDisabled;

    public ManualDynamicHtmlLoader() {

        errorSimulator = new ErrorSimulator();

        startTime = System.currentTimeMillis();

        this.id = ++idCounter;
        this.port = DEFAULT_PORT;
        this.quiet = DEFAULT_QUIET;
        this.includeWords = new ArrayList<>();
        this.failWords = new ArrayList<>();
        this.timeout = DEFAULT_TIMEOUT;
        this.htmlRequestInterval = DEFAULT_HTML_REQUEST_INTERVAL;
        this.supressJavaErrorLogs = DEFAULT_SUPRESS_JAVA_ERROR_LOGS;

        hlState = new HLState();

        waitingForServerCounter = 0;

        if (Log.hl.isInfoEnabled()) {
            Log.hl.info("\n\n\n\n" + id);
        }
    }

    public ManualDynamicHtmlLoader(int port) {
        this();
        setPort(port);
    }

    public ManualDynamicHtmlLoader(int port, boolean quiet) {
        this();
        setPort(port);
        setQuiet(quiet);
    }

    public ErrorSimulator getErrorSimulator() {
        return errorSimulator;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public boolean isQuiet() {
        return quiet;
    }

    @Override
    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public void setUrl(String url) throws HtmlLoaderException {
        Log.hl.debug(UnixDisplay.format(id + " {c}setUrl{} " + url));
        this.url = url;

        setTimeoutThread();

        errorSimulator.checkSetUrl(() -> {
            process.destroy();
            try {
                Log.hl.trace(id + " process.waitFor() {");
                process.waitFor();
                Log.hl.trace(id + " process.waitFor() }");
            } catch (InterruptedException ex) {

            }
        });

        spykiteClient.setUrl(url);

        synchronized (waitSetUrlLock) {
            try {
                Log.hl.trace(id + " waitSetUrlLock");
                waitSetUrlLock.wait();
            } catch (InterruptedException ex) {
            }
        }
        timeoutThread.interrupt();
        checkHLState();
    }

    @Override
    public void setContent(String content) throws HtmlLoaderException {
        Log.hl.debug(UnixDisplay.format(id + " {c}setContent{}"));
        this.content = content;
        spykiteClient.setContent(content);

        setTimeoutThread();
        synchronized (waitSetUrlLock) {
            try {
                Log.hl.trace(id + " waitSetUrlLock");
                waitSetUrlLock.wait();
            } catch (InterruptedException ex) {
            }
        }
        timeoutThread.interrupt();
        checkHLState();
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public int getRequestInterval() {
        return htmlRequestInterval;
    }

    @Override
    public void setRequestInterval(int requestInterval) {
        this.htmlRequestInterval = requestInterval;
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
    public boolean isHtmlLoaded() {
        return hlState.html != null;
    }

    @Override
    public void prepareResources() throws HtmlLoaderException {
        Log.hl.debug(UnixDisplay.format(id + " {c}prepareResources{}"));

        if (url != null && content != null) {
            throw new HtmlLoaderException(id + " setUrl and setContent are both set", new HtmlLoaderResult(hlState));
        }

        if (SpykiteJarPath.getBrowserJarPath() == null) {
            throw new NullPointerException(id + " skfx jar file path must be specified");
        }

        final File bFile = new File(SpykiteJarPath.getBrowserJarPath());
        if (Log.spykite.isDebugEnabled()) {
            Log.spykite.debug(id + " browserFX jar: " + bFile.getAbsolutePath());
        }

        if (!bFile.exists()) {
            throw new HtmlLoaderException(id + " skfx jar file by specified path not found (" + SpykiteJarPath.getBrowserJarPath() + ")", new HtmlLoaderResult(hlState));
        }

        if (Log.hl.isDebugEnabled()) {
            Log.hl.debug(id + " debug: " + debug);
        }

        setTimeoutThread();

        try {
            process = SpykiteClient.executeSpykiteBrowesrServer(SpykiteJarPath.getBrowserJarPath(),
                port,
                quiet,
                UnixDisplay.isEnabled(),
                cacheDisabled,
                userAgent,
                0
            );

            spykiteClient = new SpykiteClient();
            spykiteClient.addSpykiteClientListener(this);
            spykiteClient.connect(port);

            pause1WaitingInit();
            timeoutThread.interrupt();

        } catch (IOException | InterruptedException ex) {
            throw new HtmlLoaderException(ex, new HtmlLoaderResult(hlState));
        }
    }

    @Override
    public String executeJavaScript(String javaScript) throws HtmlLoaderException {

        Log.hl.info(UnixDisplay.format(id + " {c}executeJavaScript: " + StringUtil.cut(javaScript, 50) + "{}"));

        spykiteClient.sendSpykiteMessage(SpykiteMessage.CMD_JAVASCRIPT, javaScript);
        setTimeoutThread();
        synchronized (waitJSLock) {
            try {
                Log.hl.trace(id + " waitJSLock");
                waitJSLock.wait();
            } catch (InterruptedException ex) {
            }
        }
        checkHLState();
        timeoutThread.interrupt();

        return "" + javaScriptResult;
    }

    @Override
    public String loadHtml() throws HtmlLoaderException {
        Log.hl.debug(UnixDisplay.format(id + " {c}loadHtml{}"));
        spykiteClient.requestHtml();

        setTimeoutThread();

        synchronized (waitHtmlLock) {
            try {
                Log.hl.trace(id + " waitHtmlLock");
                waitHtmlLock.wait();
            } catch (InterruptedException ex) {
            }
        }

        checkHLState();
        checkFailWords();

        hlState.htmlReturned = true;
        hlState.html = currentHtml;

        if (hlState.html != null) {
            hlState.htmlReturned = true;

            if (Log.hl.isInfoEnabled()) {
                final long time = System.currentTimeMillis() - startTime;
                Log.hl.info(UnixDisplay.format(
                    id + " {g}Html loaded{}, length: "
                    + hlState.html.length() + "b, time: " + time + "ms"));
            }

            supressJavaErrorLogs();
            timeoutThread.interrupt();
            return hlState.html;
        } else {
            checkHLState();
            return null;
        }
    }

    @Override
    public void shutdown() throws HtmlLoaderException {
        if (Log.hl.isDebugEnabled()) {
            Log.hl.debug(UnixDisplay.format(id + " {c}shutdown{}"));
        }

        setTimeoutThread();
        spykiteClient.sendExit();
        synchronized (waitShutdownLock) {
            try {
                Log.hl.trace(id + " waitShutdownLock");
                waitShutdownLock.wait();
            } catch (InterruptedException ex) {
            }
        }
        checkHLState();

        process.destroy();
        while (process.isAlive()) {
            process.destroy();
        }
        try {
            process.waitFor();
        } catch (InterruptedException ex) {

        }

        timeoutThread.interrupt();
    }

    private boolean checkFailWords() {
        for (WordPare pare : failWords) {
            final String word = pare.word;
            final boolean skip = pare.skip;

            if (currentHtml != null && currentHtml.contains(word)) {

                if (!hlState.byFailWords) {
                    hlState.failWordString = word;
                    hlState.byFailWords = true;
                    hlState.skip = skip;
                    if (Log.hl.isInfoEnabled()) {
                        Log.hl.info(UnixDisplay.format(id + " {r}Found fail words: \"" + word + "\"{}, skip: " + skip));
                    }
                    unlockAll();
                    return true;
                }
            }
        }
        return false;
    }

    private void supressJavaErrorLogs() {
        if (supressJavaErrorLogs) {
            final File currentDirectory = new File(".");
            final File[] files = currentDirectory.listFiles();
            for (final File f : files) {
                if (f.getName().startsWith("hs_err_pid")) {
                    if (Log.hl.isTraceEnabled()) {
                        Log.hl.trace(UnixDisplay.format(id + " {c}delete " + f.getAbsolutePath() + "{}"));
                    }
                    f.delete();
                }
            }
        }
    }

    private void checkHLState() throws HtmlLoaderException {
        Log.hl.trace(" into checkFHEState()?");
        if (!hlState.isError() && process.isAlive()) {
            return;
        }

        if (!process.isAlive()) {
            hlState.byProcessDead = true;
        }

        supressJavaErrorLogs();

        spykiteClient.removeSpykiteClientListener(this);

        if (Log.hl.isDebugEnabled()) {
            final long time = System.currentTimeMillis() - startTime;
            Log.hl.debug(UnixDisplay.format(id + " {R}checkFHEState: destroy all{}, time: " + time + "ms"));
        }

        if (spykiteClient != null) {
            spykiteClient.hardShutdownClient();
        }

        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (Log.hl.isDebugEnabled()) {
                    Log.hl.debug("waifFor {");
                }
                process.waitFor();
                if (Log.hl.isDebugEnabled()) {
                    Log.hl.debug("waifFor }");
                }
            } catch (InterruptedException ex) {
                Log.hl.error("waitFor " + ex);
            }
        }

        timeoutThread.interrupt();

        if (hlState.byFailWords) {
            throw new HtmlLoaderException("fail words found", new HtmlLoaderResult(hlState));
        }

        if (hlState.byProcessDead) {
            throw new HtmlLoaderException("skfx process dead", new HtmlLoaderResult(hlState));
        }

        if (hlState.byTimeout) {
            throw new HtmlLoaderException(new TimeoutException(), new HtmlLoaderResult(hlState));
        }
        if (hlState.byException) {
            throw new HtmlLoaderException(hlState.exception, new HtmlLoaderResult(hlState));
        }
        if (hlState.byFailedState) {
            throw new HtmlLoaderException(id + " web engine FAILED state", new HtmlLoaderResult(hlState));
        }

        if (hlState.html == null) {
            throw new HtmlLoaderException(id + " html is null", new HtmlLoaderResult(hlState));
        }

        if (hlState.byUnknownError) {
            throw new HtmlLoaderException(id + " by unknown error", new HtmlLoaderResult(hlState));
        }

        if (hlState.byEarlyBrowserExiting) {
            throw new HtmlLoaderException(id + " by early browser exiting", new HtmlLoaderResult(hlState));
        }
    }

    private void pause1WaitingInit() throws HtmlLoaderException {
        synchronized (waitInitLock) {
            try {
                Log.hl.trace(id + " waitInitLock");
                waitInitLock.wait();
            } catch (InterruptedException ex) {

            }
        }
        checkHLState();
    }

    private void go1WaitingIniting() {
        synchronized (waitInitLock) {
            waitInitLock.notifyAll();
        }
    }

    private void setTimeoutThread() {
        if (timeoutThread != null) {
            timeoutThread.interrupt();
        }

        timeoutThread = new Thread(() -> {
            try {
                if (Log.hl.isTraceEnabled()) {
                    Log.hl.trace(id + " set timeout " + timeout + "ms");
                }
                Thread.sleep(timeout);
                hlState.byTimeout = true;
                Log.hl.debug("> TIMEOUT");
                unlockAll();
            } catch (InterruptedException ex) {

            }
        }, "timeoutThread-" + id);
        timeoutThread.start();
    }

    @Override
    public void spykiteClientInit(String serverVersion) {
        if (browserClientInited) {
            return;
        }

        GlobalInfo.lastSKFXVersionInfo = serverVersion;

        if (Log.hl.isInfoEnabled()) {
            Log.hl.info(id + " browserClientInit " + serverVersion);
        }

        browserClientInited = true;

        go1WaitingIniting();
    }

    @Override
    public void spykiteClientError(Throwable exception) {
        if (browserClientInited) {
            hlState.exception = exception;
            hlState.byException = true;
            Log.hl.error(UnixDisplay.format(id + " {r}" + exception + "{}"), exception);
            //browserClient.removeBrowserClientListener(this);
            unlockAll();
        } else {
            waitingForServerCounter++;
            if (waitingForServerCounter > MAX_WAITING_FOR_SERVER_ATTEMPTS) {
                hlState.exception = new IOException(id + " waiting for server too long time (invalid browser jar?)\n");
                hlState.byException = true;
                go1WaitingIniting();
            }
        }
    }

    @Override
    public void spykiteStateChange(String oldValue, String value) {

        if (Log.hl.isInfoEnabled()) {
            switch (value) {
                case "SUCCEEDED":
                    Log.hl.info(id + " browserStateChange " + UnixDisplay.GREEN + value + UnixDisplay.RESET);
                    break;
                case "FAILED":
                    Log.hl.info(id + " browserStateChange " + UnixDisplay.RED + value + UnixDisplay.RESET);
                    break;
                default:
                    Log.hl.info(id + " browserStateChange " + value);
                    break;
            }
        }

        if (null != value) {
            switch (value) {
                case "SUCCEEDED":
                    hlState.succeeded = true;
                    try {
                        loadHtml();
                    } catch (HtmlLoaderException ex) {
                        hlState.byException = true;
                        hlState.exception = ex;
                        Log.err(ex);
                    }
                    unlockAll();
                    break;
                case "FAILED":
                    hlState.byFailedState = true;
                    timeoutThread.interrupt();
                    unlockAll();
                    break;
            }
        }

    }

    @Override
    public void spykiteExiting() {
        if (hlState.html == null) {
            hlState.byEarlyBrowserExiting = true;
        }
        if (Log.hl.isInfoEnabled()) {
            Log.hl.info(id + " browserExiting");
        }
        done = true;
        unlockAll();
        timeoutThread.interrupt();

        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {

            }
            unlockAll();
        }, "unlock-thread").start();

    }

    private void unlockAll() {
        Log.hl.debug(UnixDisplay.format(id + " {c}unlockAll{}"));

        go1WaitingIniting();
        synchronized (waitSetUrlLock) {
            waitSetUrlLock.notifyAll();
        }
        synchronized (waitHtmlLock) {
            waitHtmlLock.notifyAll();
        }
        synchronized (waitJSLock) {
            waitJSLock.notifyAll();
        }
        synchronized (waitShutdownLock) {
            waitShutdownLock.notifyAll();
        }
    }

    @Override
    public void spykiteIncomingMessage(SpykiteMessage browserMessage) {
//        if (waitingSucceededHtml && browserMessage.getCommand() == BrowserMessage.CMD_HTML) {
//            if (!browserClient.isExiting() && fheState.html == null) {
//                fheState.html = currentHtml = browserMessage.getParams();
//                if (Log.fhedev.isTraceEnabled()) {
//                    Log.fhedev.trace("received waiting succeeded html, then go1");
//                }
//                checkFailWords();
//                go1WaitingIniting();
//            }
//        }

        if (browserMessage.getCommand() == SpykiteMessage.CMD_HTML) {
            hlState.html = currentHtml = browserMessage.getParams();
            checkFailWords();
            synchronized (waitHtmlLock) {
                waitHtmlLock.notifyAll();
            }
        }

        if (browserMessage.getCommand() == SpykiteMessage.CMD_JAVASCRIPT
            && browserMessage.isAnswer()) {
            javaScriptResult = browserMessage.getParams();
            synchronized (waitJSLock) {
                waitJSLock.notifyAll();
            }
        }
    }

    @Override
    public boolean isSupressJavaErorrLogs() {
        return supressJavaErrorLogs;
    }

    @Override
    public void setSupressJavaErrorLogs(boolean value) {
        supressJavaErrorLogs = value;
    }

    @Override
    public String getCurrentHtml() {
        return currentHtml;
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

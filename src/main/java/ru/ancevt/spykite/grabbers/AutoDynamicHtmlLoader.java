package ru.ancevt.spykite.grabbers;

import ru.ancevt.spykite.skfx.SpykiteJarPath;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import ru.ancevt.spykite.Log;
import ru.ancevt.spykite.skfx.SpykiteClient;
import ru.ancevt.spykite.skfx.SpykiteClientListener;
import ru.ancevt.spykite.skfx.SpykiteMessage;
import ru.ancevt.util.fs.SimpleFileReader;
import ru.ancevt.util.system.UnixDisplay;

/**
 *
 * @author ancevt
 */
public final class AutoDynamicHtmlLoader implements IHtmlLoader, SpykiteClientListener {

    public static void main(String[] args) throws IOException, InterruptedException, HtmlLoaderException {
        //FrontendHtmlExtractor.setBrowserJarPath("/home/ancevt/Software/WebBrowserFX/browser.jar");
        final AutoDynamicHtmlLoader loader = new AutoDynamicHtmlLoader(7000, false);

        final String content = SimpleFileReader.readUtf8(AutoDynamicHtmlLoader.class.getClassLoader().getResourceAsStream("html2.html")
        );
        loader.setTimeout(60000);
        loader.addIncludeWords("_025a50318d--title--2hdC7");

        loader.prepareResources();
        loader.setUrl("https://cian.ru");
        System.out.println("html length: " + loader.loadHtml().length());

        System.out.println("program done");
        //Thread.sleep(Integer.MAX_VALUE);
    }

    public static final int DEFAULT_TIMEOUT = 60000; // ms
    public static final int DEFAULT_HTML_REQUEST_INTERVAL = 1000;
    public static final int DEFAULT_PORT = 7777;
    public static final boolean DEFAULT_QUIET = true;
    public static final int MAX_WAITING_FOR_SERVER_ATTEMPTS = 10;
    public static final boolean DEFAULT_SUPRESS_JAVA_ERROR_LOGS = true;

    private static int idCounter;

    public Object debug;

    private final ErrorSimulator errorSimulator;

    private final Object lock1 = new Object();
    private final Object lock2 = new Object();

    private final int id;
    private int port;
    private String url;
    private String content;
    private boolean quiet;

    private final HLState hlState;
    private SpykiteClient browserClient;
    private final List<String> includeWords;
    private final List<WordPare> failWords;
    private Process process;
    private Thread searchingWordsThread;
    private Thread timeoutThread;
    private volatile boolean searchingWords;
    private volatile boolean done;
    private int timeout;
    private int htmlRequestInterval;
    private boolean browserClientInited;
    private int waitingForServerCounter;
    private boolean waitingSucceededHtml;
    private String currentHtml;
    private final long startTime;

    private boolean supressJavaErrorLogs;
    private Object waitShutdownLock = new Object();

    private String userAgent;
    private boolean cacheDisabled;

    public AutoDynamicHtmlLoader() {
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

    public AutoDynamicHtmlLoader(int port) {
        this();
        setPort(port);
    }

    public AutoDynamicHtmlLoader(int port, boolean quiet) {
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
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public void setContent(String content) {
        this.content = content;
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
    public String getCurrentHtml() {
        return currentHtml;
    }

    @Override
    public String loadHtml() throws HtmlLoaderException {

        if (PortsHelper.contains(port)) {
            throw new HtmlLoaderException("port " + port + " already used", new HtmlLoaderResult(hlState));
        }

        PortsHelper.addPort(port);

        if (url != null && content != null) {
            throw new HtmlLoaderException("setUrl and setContent are both set", new HtmlLoaderResult(hlState));
        }

        if (SpykiteJarPath.getBrowserJarPath() == null) {
            throw new NullPointerException("browserfx jar file path must be specified");
        }

        final File bFile = new File(SpykiteJarPath.getBrowserJarPath());
        if (Log.spykite.isDebugEnabled()) {
            Log.spykite.debug("browserFX jar: " + bFile.getAbsolutePath());
        }

        if (!bFile.exists()) {
            throw new RuntimeException("browserfx jar file by specified path not found (" + SpykiteJarPath.getBrowserJarPath() + ")");
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

            browserClient = new SpykiteClient();
            browserClient.addSpykiteClientListener(this);
            browserClient.connect(port);

            pause1();

            checkFailWords();

            searchingWords = false;

            if (!done && !hlState.isError() && browserClient.isOpened()) {

                if (Log.hl.isDebugEnabled()) {
                    Log.hl.debug(id + " browserClient.sendExit();");
                }

                shutdown();

                //browserClient.hardShutdownClient();
            }

            checkHLState();

            if (!done && !hlState.isError() && browserClient.isOpened()) {
                pause2();
            }

            checkHLState();

            if (Log.hl.isTraceEnabled()) {
                Log.hl.trace(id + " process.destroy();");
            }

            process.destroy();

            checkHLState();

            if (hlState.html != null) {
                hlState.htmlReturned = true;

                if (Log.hl.isInfoEnabled()) {
                    final long time = System.currentTimeMillis() - startTime;
                    Log.hl.info(UnixDisplay.format(
                        id + " {g}Success!{}, length: "
                        + (hlState.html.length() / 1024) + "k") + ", time: " + time + "ms");
                }
                browserClient.removeSpykiteClientListener(this);

                PortsHelper.removePort(port);
                //System.out.println("???? " + PortsHelper.contains(port));

                supressJavaErrorLogs();
                timeoutThread.interrupt();
                return hlState.html;
            } else {
                PortsHelper.removePort(port);
                checkHLState();
                return null;
            }
        } catch (Exception ex) {
            PortsHelper.removePort(port);
            throw new HtmlLoaderException(ex, new HtmlLoaderResult(hlState));
        }

    }

    private boolean checkFailWords() {
        for (WordPare pare : failWords) {
            final String word = pare.word;
            final boolean skip = pare.skip;

            if (currentHtml != null && currentHtml.contains(word)) {

                if (!hlState.byFailWords) {
                    searchingWords = false;
                    hlState.byFailWords = true;
                    hlState.skip = skip;
                    if (Log.hl.isInfoEnabled()) {
                        Log.hl.info(UnixDisplay.format(id + " {r}Found fail words{}, skip: " + skip));
                    }
                    go1();
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
                        Log.hl.trace(UnixDisplay.format("{c}delete " + f.getAbsolutePath() + "{}"));
                    }
                    f.delete();
                }
            }
        }
    }

    private void checkHLState() throws HtmlLoaderException {
        // if (!fheState.isError() || fheState.html != null) { // My be return back
        if (!hlState.isError()) {
            return;
        }

        supressJavaErrorLogs();

        Log.hl.trace("into checkHLState()");

        browserClient.removeSpykiteClientListener(this);

        if (Log.hl.isDebugEnabled()) {
            final long time = System.currentTimeMillis() - startTime;
            Log.hl.debug(UnixDisplay.format(id + " {R}checkHLState: destroy all{}, time: " + time + "ms"));
        }
        searchingWords = false;

        PortsHelper.removePort(port);

        if (browserClient != null) {
            browserClient.hardShutdownClient();
        }

        while (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (Log.hl.isDebugEnabled()) {
                    Log.hl.debug("process.waifFor {");
                }
                process.waitFor();
                if (Log.hl.isDebugEnabled()) {
                    Log.hl.debug("process.waifFor }");
                }
            } catch (InterruptedException ex) {
                Log.hl.error("waitFor " + ex);
            }
        }

        if (searchingWordsThread != null) {
            searchingWordsThread.interrupt();
        }

        if (timeoutThread != null && timeoutThread.isAlive()) {
            timeoutThread.interrupt();
        }

        if (hlState.byFailWords) {
            throw new HtmlLoaderException("fail words found", new HtmlLoaderResult(hlState));
        }

        if (hlState.byTimeout) {
            throw new HtmlLoaderException(new TimeoutException(), new HtmlLoaderResult(hlState));
        }
        if (hlState.byException) {
            throw new HtmlLoaderException(hlState.exception, new HtmlLoaderResult(hlState));
        }
        if (hlState.byFailedState) {
            throw new HtmlLoaderException("web engine FAILED state", new HtmlLoaderResult(hlState));
        }

        if (hlState.html == null) {
            throw new HtmlLoaderException("html is null", new HtmlLoaderResult(hlState));
        }

        if (hlState.byUnknownError) {
            throw new HtmlLoaderException("by unknown error", new HtmlLoaderResult(hlState));
        }

        if (hlState.byEarlyBrowserExiting) {
            throw new HtmlLoaderException("by early browser exiting", new HtmlLoaderResult(hlState));
        }
    }

    private void pause1() {
        Log.hl.debug(id + " > pause1");
        synchronized (lock1) {
            try {
                lock1.wait();
            } catch (InterruptedException ex) {

            }
        }
        if (Log.hl.isDebugEnabled()) {
            Log.hl.debug(id + " > pause1 passed");
        }
    }

    private void go1() {
        if (Log.hl.isDebugEnabled()) {
            Log.hl.debug(id + " > go1");
        }
        synchronized (lock1) {
            lock1.notifyAll();
        }
        synchronized (waitShutdownLock) {
            waitShutdownLock.notifyAll();
        }
    }

    private void pause2() {
        if (Log.hl.isDebugEnabled()) {
            Log.hl.debug(id + " > pause2");
        }
        synchronized (lock2) {
            try {
                lock2.wait();
            } catch (InterruptedException ex) {

            }
        }
        if (Log.hl.isDebugEnabled()) {
            Log.hl.debug(id + " > pause2 passed");
        }
    }

    private void go2() {
        if (Log.hl.isDebugEnabled()) {
            Log.hl.debug(id + " > go2");
        }
        synchronized (lock2) {
            lock2.notifyAll();
        }
        synchronized (waitShutdownLock) {
            waitShutdownLock.notifyAll();
        }
    }

    private void setTimeoutThread() {
        if (timeoutThread != null && timeoutThread.isAlive()) {
            timeoutThread.interrupt();
        }

        timeoutThread = new Thread(() -> {
            try {
                if (Log.hl.isTraceEnabled()) {
                    Log.hl.trace(id + " Timeout = " + timeout);
                }
                Thread.sleep(timeout);
                if (!hlState.succeeded) {
                    searchingWords = false;
                    if (searchingWordsThread != null) {
                        searchingWordsThread.interrupt();
                    }
                    hlState.byTimeout = true;
                    Log.hl.debug("> TIMEOUT");
                    go1();
                    go2();
                }
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

        if (Log.hl.isInfoEnabled()) {
            Log.hl.info(id + " browserClientInit " + serverVersion);
        }

        browserClientInited = true;

        //final String url1 = Math.random() > 0.50 ? "fake" : url;
        if (url != null) {
            final String url1 = url;

            if (Log.hl.isInfoEnabled()) {
                Log.hl.info(id + " url: " + url1);
            }
            browserClient.setUrl(url1);
        } else if (content != null) {
            if (Log.hl.isInfoEnabled()) {
                Log.hl.info("content length: " + content.getBytes().length);
            }
            browserClient.setContent(content);
        } else {
            hlState.exception = new NullPointerException();
            hlState.byException = true;
            go1();
            return;
        }

        if (!includeWords.isEmpty() || !failWords.isEmpty()) {
            searchingWords = true;

            searchingWordsThread = new Thread(() -> {
                while (searchingWords && !hlState.isError() && !hlState.succeeded) {

                    if (!browserClient.isOpened()) {
                        continue;
                    }
                    try {
                        Thread.sleep(htmlRequestInterval);

                        if (hlState.html == null && searchingWords) {
                            browserClient.requestHtml();
                        }
                        if (currentHtml == null) {
                            continue;
                        }

                        for (String word : includeWords) {
                            if (currentHtml != null && currentHtml.contains(word)) {

                                if (!hlState.succeeded && !hlState.byFailWords) {
                                    hlState.html = currentHtml;
                                    searchingWords = false;
                                    hlState.foundByIncludingWords = true;
                                    if (Log.hl.isInfoEnabled()) {
                                        Log.hl.info(UnixDisplay.format(id + " {y}Found by including words{}"));
                                    }
                                    go1();
                                    break;
                                }
                            }
                        }

                        if (checkFailWords()) {
                            searchingWords = false;
                        }

                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }, "searchingWordsThread-" + id);
            searchingWordsThread.start();
        }
    }

    @Override
    public void spykiteClientError(Throwable exception) {
        if (browserClientInited) {
            hlState.exception = exception;
            hlState.byException = true;
            Log.hl.error(UnixDisplay.format("{r}" + exception + "{}"), exception);
            //browserClient.removeBrowserClientListener(this);
            go1();
        } else {
            waitingForServerCounter++;
            if (waitingForServerCounter > MAX_WAITING_FOR_SERVER_ATTEMPTS) {
                hlState.exception = new IOException("waiting for server too long time (invalid browser jar?)\n");
                hlState.byException = true;
                go1();
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

        if ("SUCCEEDED".equals(value) && !hlState.foundByIncludingWords) {
            searchingWords = false;
            hlState.succeeded = true;
            waitingSucceededHtml = true;
            browserClient.requestHtml();
        } else if ("FAILED".equals(value)) {
            hlState.byFailedState = true;
            searchingWords = false;
            timeoutThread.interrupt();
            go1();
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
        go1();
        go2();

        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {

            }
            go1();
            go2();
        }, "unlock-thread").start();
    }

    @Override
    public void spykiteIncomingMessage(SpykiteMessage browserMessage) {
        if (waitingSucceededHtml && browserMessage.getCommand() == SpykiteMessage.CMD_HTML) {
            if (!browserClient.isExiting() && hlState.html == null) {
                hlState.html = currentHtml = browserMessage.getParams();
                if (Log.hl.isTraceEnabled()) {
                    Log.hl.trace(id + " received waiting succeeded html, then go1");
                }
                checkFailWords();
                go1();
            }
        }

        if (browserMessage.getCommand() == SpykiteMessage.CMD_HTML) {
            currentHtml = browserMessage.getParams();
        }

        if (browserMessage.getCommand() == SpykiteMessage.CMD_EXIT) {

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
    public void shutdown() throws HtmlLoaderException {
        if (Log.hl.isDebugEnabled()) {
            Log.hl.debug(UnixDisplay.format(id + " {c}shutdown{}"));
        }

        setTimeoutThread();
        browserClient.sendExit();
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

    @Override
    public void prepareResources() {
        // Not supported
    }

    @Override
    public String executeJavaScript(String javaScript) {
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

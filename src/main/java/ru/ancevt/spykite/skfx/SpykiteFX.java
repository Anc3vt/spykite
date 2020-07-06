package ru.ancevt.spykite.skfx;

import java.io.IOException;
import java.io.StringWriter;
import java.net.BindException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import ru.ancevt.net.messaging.server.MessagingServerAdapter;
import ru.ancevt.spykite.Log;
import ru.ancevt.util.args.Args;
import ru.ancevt.util.fs.SimpleFileReader;
import ru.ancevt.util.project.ProjectProperties;
import ru.ancevt.util.repl.ReplInterpreter;
import ru.ancevt.util.system.UnixDisplay;

/**
 * @author ancevt
 */
public class SpykiteFX extends Application {

    private static final int DEFAULT_SHUTDOWN_TIME = 1 * 60 * 60 * 1000; 

    private static final String DEFAULT_UERSAGENT = "Opera/9.80 (Windows NT 6.2; WOW64) Presto/2.12.388 Version/12.17";

    private static Args a;
    private static int port;
    private static String startPage;
    private static boolean quiet;
    private static String version;
    private static int shutdownTime;

    static final Object lock = new Object();

    public static void main(String[] args) throws IOException {

        a = new Args(args);

        shutdownTime = a.getInt("--shutdown-timeout", DEFAULT_SHUTDOWN_TIME);

        if (a.contains("--kill")) {
            final int killPort = a.getInt("--kill", SpykiteServer.DEFAULT_PORT);
            SpykiteKiller.kill(killPort);
            System.exit(0);
        }

        try {
            if (a.contains("--disable-cache")) {
                SpykiteCacheCleaner.clear();
            }
        } catch (Exception ex) {
            Log.spykite.error("EXCEPTION ON CLEAN CACHE");
        }

        UnixDisplay.setEnabled(a.contains("--colored-logs"));

        final String build = SimpleFileReader.readUtf8(SpykiteFX.class.getClassLoader().getResourceAsStream("build.txt")
        ).trim();

        version = ProjectProperties.getNameVersion() + "." + build;

        if (a.contains("--version")) {
            System.out.println(ProjectProperties.getNameVersion() + "." + build + '\n' + ProjectProperties.getDescription());
            System.exit(0);
        }

        startPage = a.getString(new String[]{"--start-page", "-p"}, "http://ancevt.ru");

        port = a.getInt("--port", "-p");
        if (port == 0) {
            port = SpykiteServer.DEFAULT_PORT;
        }

        quiet = a.contains("--quiet", "-q");
        launch(args);
    }

    private WebView webView;
    private WebEngine webEngine;
    private Stage stage;
    private String currentUrl;
    private SpykiteServer browserServer;
    private Object javaScriptReturnedValue;

    @Override
    public void start(Stage stage) throws Exception {

        this.stage = stage;
        this.webView = new WebView();
        this.webEngine = webView.getEngine();

        System.out.println(webEngine.getUserAgent());

        webEngine.setUserAgent(a.getString(new String[]{"--user-agent"}, DEFAULT_UERSAGENT));

        final StackPane root = new StackPane();
        root.setPadding(new Insets(0));
        root.getChildren().addAll(webView);

        final Scene scene = new Scene(root);

        stage.setScene(scene);
        stage.setWidth(1000 + (Math.random() * 100));
        stage.setHeight(800 + (Math.random() * 100));

        if (!quiet) {
            stage.show();
        }

        stage.setX(Math.random() * 500);
        stage.setY(Math.random() * 300);

        browserServer = new SpykiteServer(this, shutdownTime);
        browserServer.addMessagingServerListener(new MessagingServerAdapter() {
            @Override
            public void serverShutdown() {
                System.exit(0);
            }
        });

        new Thread(() -> {
            try {
                browserServer.addMessagingServerListener(new MessagingServerAdapter() {
                    @Override
                    public void messagingServerStarted() {
                        synchronized (SpykiteFX.lock) {
                            SpykiteFX.lock.notifyAll();
                        }
                    }
                });
                browserServer.start(port);
            } catch (IOException ex) {
                Log.spykite.error(ex, ex);

                if (ex instanceof BindException) {
                    Log.spykite.error("System exit");
                    System.exit(1);
                }
            }
        }, "Browser-server-starter").start();

        webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {

            @Override
            public void changed(
                ObservableValue<? extends Worker.State> observable,
                Worker.State oldValue,
                Worker.State newValue) {

                System.out.println("???? " + newValue);

                try {
                    browserServer.workerStateChanged(oldValue.toString(), newValue.toString());
                } catch (IOException ex) {
                    Log.spykite.error(ex);
                }
            }
        });

        webEngine.setOnStatusChanged(new EventHandler<WebEvent<String>>() {
            @Override
            public void handle(WebEvent<String> e) {

//                try {
//                    browserServer.statusChanged(e.getData());
//                } catch (IOException ex) {
//                    Log.logger.error(ex);
//                }
            }
        });

        stage.setOnCloseRequest((WindowEvent event) -> {
            try {
                stage.close();
            } catch (Exception ex) {
                Log.spykite.error(ex, ex);
            }
        });

        final String content = SimpleFileReader.readUtf8(
            getClass().getClassLoader().getResourceAsStream("html2.html")
        );

        //loadContent(content);
        final ReplInterpreter ri = new ReplInterpreter("> ");

        ri.addCommand("load", (a) -> {
            setAlwaysOnTop(true);
            load(a.getString(0));
        });
        ri.addCommand("js", (a) -> {
            final Object val = executeJavaScript(a.getString(0));
            System.out.println("val: " + val);
        });

        new Thread(() -> ri.start()).start();
    }

    public final String getHtml() {
        return getStringFromDocument(webEngine.getDocument());
    }

    public final void setLocation(int x, int y) {
        stage.setX(x);
        stage.setY(y);
    }

    public final void setSize(int w, int h) {
        stage.setWidth(w);
        stage.setHeight(h);
    }

    public final void setWidth(int value) {
        stage.setWidth(value);
    }

    public final void setHeight(int value) {
        stage.setHeight(value);
    }

    public final int getWidth() {
        return (int) stage.getWidth();
    }

    public final int getHeight() {
        return (int) stage.getHeight();
    }

    public final void setX(int value) {
        stage.setX(value);
    }

    public final void setY(int value) {
        stage.setY(value);
    }

    public final int getX() {
        return (int) stage.getX();
    }

    public final int getY() {
        return (int) stage.getY();
    }

    public final void load(String url) {
        currentUrl = url;
        Platform.runLater(() -> webEngine.load(currentUrl));
    }

    public void loadContent(String html) {
        Platform.runLater(() -> webEngine.loadContent(html));
    }

    public final void refresh() {
        load(currentUrl);
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public static final String getStringFromDocument(Document doc) {
        try {
            final DOMSource domSource = new DOMSource(doc);
            final StringWriter writer = new StringWriter();
            final StreamResult result = new StreamResult(writer);
            final TransformerFactory tf = TransformerFactory.newInstance();
            final Transformer transformer = tf.newTransformer();
            transformer.transform(domSource, result);
            return writer.toString();
        } catch (TransformerException ex) {
            Log.err(ex, ex);
            return null;
        }
    }

    public void show() {
        Platform.runLater(() -> {
            stage.show();
        });

    }

    public Object getJavaScriptReturnedValue() {
        return javaScriptReturnedValue;
    }

    public Object executeJavaScript(String javaScript) {
        final Object l = new Object();

        Platform.runLater(() -> {
            javaScriptReturnedValue = webEngine.executeScript(javaScript);
            synchronized (l) {
                l.notifyAll();
            }
        });

        synchronized (l) {
            try {
                l.wait();
            } catch (InterruptedException ex) {
            }
        }

        return javaScriptReturnedValue;
    }

    public void shutdown() {
        Log.spykite.info("Normal exit");
        System.exit(0);
    }

    public void hide() {
        Platform.runLater(() -> {
            stage.hide();
        });
    }

    public void setAlwaysOnTop(final boolean value) {
        Platform.runLater(() -> {
            stage.setAlwaysOnTop(value);
        });
    }

    public boolean isAlwaysOnTop() {
        return stage.isAlwaysOnTop();
    }

    public static String getVersion() {
        return version;
    }

}

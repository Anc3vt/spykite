package ru.ancevt.spykite.skfx;

import java.io.IOException;
import java.net.SocketException;
import ru.ancevt.net.messaging.MessagingConnection;
import ru.ancevt.net.messaging.MessagingConnectionListener;
import ru.ancevt.net.messaging.message.MessageData;
import ru.ancevt.net.messaging.server.MessagingServer;
import ru.ancevt.net.messaging.server.MessagingServerListener;
import ru.ancevt.spykite.Log;
import ru.ancevt.util.args.Args;
import ru.ancevt.util.string.StringUtil;
import ru.ancevt.util.system.UnixDisplay;

/**
 *
 * @author ancevt
 */
public class SpykiteServer extends MessagingServer implements MessagingServerListener, MessagingConnectionListener {

    public static final int DEFAULT_PORT = 7000;
    public static final String DEFAULT_HOST = "localhost";

    private final SpykiteFX browser;
    private boolean exiting;

    private Thread shutdownThread;
    private final int shutdownTime;
    private MessagingConnection connection;

    public SpykiteServer(SpykiteFX browser, int shutdownTime) {
        this.browser = browser;
        this.shutdownTime = shutdownTime;
        addMessagingServerListener(this);
    }

    @Override
    public void messagingServerStarted() {
        Log.spykite.info(UnixDisplay.format("{y}Browser server started at port " + getPort() + "{}"));
        updateShutdowThread();
        Log.spykite.info("Starting timeout shutdown thread: " + shutdownTime + "ms");
    }

    @Override
    public void acceptMessagingConnection(MessagingConnection connection) {
        this.connection = connection;
        connection.addMessagingConnectionListener(this);
    }

    @Override
    public void closeMessagingConnection(MessagingConnection connection, Throwable ex) {
        connection.removeMessagingConnectionListener(this);
    }

    @Override
    public void serverShutdown() {
        shutdownThread.interrupt();
        Log.spykite.info(UnixDisplay.format(getPort() + " {r}Server shutdown{}"));
    }

    private void sendInit() {
        sendMessageToClient(new SpykiteMessage(SpykiteMessage.CMD_INIT, SpykiteFX.getVersion()));
    }

    public synchronized void sendMessageToClient(SpykiteMessage message) {
        if (exiting && message.getCommand() != SpykiteMessage.CMD_EXIT) {
            return;
        }

        message.prepare();

        for (int i = 0; i < getConnectionCount(); i++) {
            try {
                final MessagingConnection connection = getConnection(i);
                Log.spykite.info(UnixDisplay.format(getPort() + " {c}Server ->: " + message.toString() + "{}"));
                connection.send(message);
            } catch (IOException ex) {
                if (ex instanceof SocketException) {

                } else {
                    Log.spykite.error(ex, ex);
                }
            }
        }
        
        updateShutdowThread();
    }

    private void sendAnswer(int requestId) {
        final SpykiteMessage bm = new SpykiteMessage(SpykiteMessage.INFO_OK, StringUtil.EMPTY);
        bm.setAnswer(true);
        bm.setRequestId(requestId);
        sendMessageToClient(bm);
    }

    /// MessagingConnectionListener:
    @Override
    public void connectionOpened(MessagingConnection connection) {
        sendInit();
    }

    private void updateShutdowThread() {
        Log.spykite.trace("shutdown timer reset");
        
        if (shutdownThread != null) {
            shutdownThread.interrupt();
        }

        if (!exiting) {
            shutdownThread = new Thread(() -> {

                try {
                    Thread.sleep(shutdownTime);
                    if (!exiting) {
                        Log.spykite.trace("SHUTDOWN");
                        shutdown();
                    }
                    
                } catch (InterruptedException ex) {

                } catch (IOException ex) {
                    Log.spykite.error("server shutdown exception: " + ex, ex);
                }

            }, "shutdownThread");
            
            shutdownThread.start();
        }
    }

    @Override
    public void incomingMessageData(MessagingConnection connection, MessageData messageData) {
        
        updateShutdowThread();

        try {
            final SpykiteMessage browserMessage = new SpykiteMessage(messageData);

            Log.spykite.info(UnixDisplay.format(getPort() + " {g}Server <-: " + browserMessage + "{}"));

            final int requestId = browserMessage.getRequestId();
            final int command = browserMessage.getCommand();

            final Args p = command == SpykiteMessage.CMD_HTML ? null : new Args(browserMessage.getParams());

            switch (command) {
                case SpykiteMessage.CMD_LOCATION:
                    if (p.contains("-x") && p.contains("-y")) {
                        browser.setLocation(p.getShort("-x"), p.getShort("-y"));
                        sendAnswer(requestId);
                    } else if (p.contains("-x")) {
                        browser.setX(p.getShort("-x"));
                        sendAnswer(requestId);
                    } else if (p.contains("-y")) {
                        browser.setY(p.getShort("-y"));
                        sendAnswer(requestId);
                    } else {
                        final Args args = new Args();
                        args.append("-x", (int) browser.getX());
                        args.append("-y", (int) browser.getY());
                        sendMessageToClient(new SpykiteMessage(SpykiteMessage.CMD_LOCATION, args.toString(), true));
                    }
                    break;
                case SpykiteMessage.CMD_SIZE:
                    if (p.contains("-w") && p.contains("-h")) {
                        browser.setSize(p.getShort("-w"), p.getShort("-h"));
                        sendAnswer(requestId);
                    } else if (p.contains("-w")) {
                        browser.setWidth(p.getShort("-w"));
                        sendAnswer(requestId);
                    } else if (p.contains("-h")) {
                        browser.setHeight(p.getShort("-h"));
                        sendAnswer(requestId);
                    } else {
                        final Args args = new Args();
                        args.append("-w", browser.getWidth());
                        args.append("-h", browser.getHeight());
                        sendMessageToClient(new SpykiteMessage(SpykiteMessage.CMD_SIZE, args.toString(), true));
                    }
                case SpykiteMessage.CMD_CONTENT:
                    browser.loadContent(browserMessage.getParams());
                    sendAnswer(requestId);
                    break;

                case SpykiteMessage.CMD_URL:
                    if (p.contains("-url")) {
                        browser.load(p.getString("-url"));
                        sendMessageToClient(new SpykiteMessage(SpykiteMessage.CMD_URL, p.getString("-url"), true));
                    } else {
                        final Args args = new Args();
                        args.append("-url", browser.getCurrentUrl());
                        sendMessageToClient(new SpykiteMessage(SpykiteMessage.CMD_URL, args.toString(), true));
                    }
                    break;
                case SpykiteMessage.CMD_REFRESH:
                    browser.refresh();
                    sendAnswer(requestId);
                    break;

                case SpykiteMessage.CMD_HTML:
                    final String html = browser.getHtml();
                    final SpykiteMessage bm = new SpykiteMessage(SpykiteMessage.CMD_HTML, html, true);
                    bm.setRequestId(requestId);
                    sendMessageToClient(bm);
                    break;

                case SpykiteMessage.CMD_SHOW:
                    browser.show();
                    sendAnswer(requestId);
                    break;

                case SpykiteMessage.CMD_HIDE:
                    browser.hide();
                    sendAnswer(requestId);
                    break;

                case SpykiteMessage.CMD_EXIT:
                    sendMessageToClient(new SpykiteMessage(SpykiteMessage.CMD_EXIT, StringUtil.EMPTY, true));
                    exiting = true;
                    break;

                case SpykiteMessage.CMD_SHUTDOWN:
                    shutdown();
                    break;

                case SpykiteMessage.CMD_ALWAYS_ON_TOP:
                    if (p != null && !p.isEmpty()) {
                        browser.setAlwaysOnTop(p.getBoolean(0));
                        sendAnswer(requestId);
                    } else {
                        sendMessageToClient(new SpykiteMessage(SpykiteMessage.CMD_ALWAYS_ON_TOP, "" + browser.isAlwaysOnTop(), true));
                    }
                    break;
                case SpykiteMessage.CMD_JAVASCRIPT:
                    final Object result = browser.executeJavaScript(browserMessage.getParams());
                    final SpykiteMessage answer = new SpykiteMessage(SpykiteMessage.CMD_JAVASCRIPT, "" + result, true);
                    answer.setRequestId(requestId);
                    sendMessageToClient(answer);
                    break;

            }
        } catch (IOException ex) {
            Log.err(ex, ex);
        }
    }

    @Override
    public void connectionClosed(MessagingConnection connection, Throwable exception) {

    }

    ////-----------------------
    public void workerStateChanged(String oldValue, String newValue) throws IOException {
        final SpykiteMessage browserMessage = new SpykiteMessage();
        browserMessage.setCommand(SpykiteMessage.INFO_WORKER_STATE_CHANGE);
        browserMessage.setParams("-oldValue " + oldValue + " -newValue " + newValue);
        sendMessageToClient(browserMessage);
    }

    public void statusChanged(String data) throws IOException {
        final SpykiteMessage browserMessage = new SpykiteMessage();
        browserMessage.setCommand(SpykiteMessage.INFO_STATUS_CHANGE);
        browserMessage.setParams("-data " + data);
        sendMessageToClient(browserMessage);
    }

}

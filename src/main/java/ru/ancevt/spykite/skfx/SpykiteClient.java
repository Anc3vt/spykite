package ru.ancevt.spykite.skfx;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import ru.ancevt.net.messaging.MessagingClientAdapter;
import ru.ancevt.net.messaging.MessagingConnection;
import ru.ancevt.net.messaging.MessagingConnectionListener;
import ru.ancevt.net.messaging.client.MessagingClient;
import ru.ancevt.net.messaging.client.MessagingClientListener;
import ru.ancevt.net.messaging.message.MessageData;
import ru.ancevt.spykite.Log;
import ru.ancevt.spykite.grabbers.AutoDynamicHtmlLoader;
import ru.ancevt.util.args.Args;
import ru.ancevt.util.string.StringUtil;
import ru.ancevt.util.system.UnixDisplay;

/**
 * @author ancevt
 */
public class SpykiteClient implements MessagingClientListener, MessagingConnectionListener, Closeable {
    
    private static final int RECONNECT_INTERVAL = 500;
    
    public static Queue<Process> processes = new ConcurrentLinkedQueue<>();
    
    private static int idCounter;
    
    private final Object lock = new Object();
    
    private MessagingConnection connection;
    private MessagingClient client;
    private int port;
    private String host;
    private boolean closed;
    private final List<SpykiteClientListener> spykiteClientListeners;
    private boolean reconnectEnabled;
    private boolean exiting;
    private int id;
    
    private final MessagingConnectionListener unlockListener = new MessagingClientAdapter() {
        
        @Override
        public void incomingMessageData(MessagingConnection connection, MessageData messageData) {
            try {
                final SpykiteMessage m = new SpykiteMessage(messageData);
                
                if (m.isAnswer()) {
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }
            } catch (IOException ex) {
                Log.err(ex, ex);
            }
        }
    };
    
    public SpykiteClient() {
        this.id = ++idCounter;
        spykiteClientListeners = new CopyOnWriteArrayList<>();
        reconnectEnabled = true;
    }
    
    public int getId() {
        return id;
    }
    
    public boolean isReconnectEnabled() {
        return reconnectEnabled;
    }
    
    public void setReconnectEnabled(boolean reconnectEnabled) {
        this.reconnectEnabled = reconnectEnabled;
    }
    
    public void addSpykiteClientListener(SpykiteClientListener listener) {
        spykiteClientListeners.add(listener);
    }
    
    public void removeSpykiteClientListener(SpykiteClientListener listener) {
        spykiteClientListeners.remove(listener);
    }
    
    public void dispatchIncomingSpykiteMessage(SpykiteMessage browserMessage) {
        new Thread(() -> {
            spykiteClientListeners.stream().forEach((spykiteClientListener) -> {
                spykiteClientListener.spykiteIncomingMessage(browserMessage);
            });
        }, "SK-dispatchSpykiteClientInit").start();
    }
    
    public void dispatchSpykiteClientInit(String serverVersion) {
        new Thread(() -> {
            spykiteClientListeners.stream().forEach((spykiteClientListener) -> {
                spykiteClientListener.spykiteClientInit(serverVersion);
            });
        }, "SK-dispatchSpykiteClientInit").start();
    }
    
    public void dispatchSpykiteClientError(Throwable ex) {
        new Thread(() -> {
            spykiteClientListeners.stream().forEach((spykiteClientListener) -> {
                spykiteClientListener.spykiteClientError(ex);
            });
        }, "SK-dispatchSpykiteClientError").start();
    }
    
    public void dispatchSpykiteStateChange(String oldValue, String value) {
        new Thread(() -> {
            spykiteClientListeners.stream().forEach((spykiteClientListener) -> {
                spykiteClientListener.spykiteStateChange(oldValue, value);
            });
        }, "SK-dispatchSpykiteStateChange").start();
    }
    
    public void dispatchSpykiteExiting() {
        new Thread(() -> {
            spykiteClientListeners.stream().forEach((spykiteClientListener) -> {
                spykiteClientListener.spykiteExiting();
            });
        }, "SK-dispatchSpykiteExiting").start();
    }
    
    public boolean isOpened() {
        return !closed;
    }
    
    public void connect() throws IOException {
        connect(SpykiteServer.DEFAULT_HOST, SpykiteServer.DEFAULT_PORT);
    }
    
    public void connect(int port) throws IOException {
        connect(SpykiteServer.DEFAULT_HOST, port);
    }
    
    public void connect(String host) throws IOException {
        connect(host, AutoDynamicHtmlLoader.DEFAULT_PORT);
    }
    
    public void connect(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        
        if (connection != null && connection.isOpened()) {
            try {
                closed = true;
                connection.close();
            } catch (IOException ex) {
                Log.err(ex, ex);
            }
        }
        
        client = new MessagingClient();
        client.addMessagingClientListener(this);
        client.connect(host, port);
    }
    
    public int sendJavaScript(String javaScript) {
        return sendSpykiteMessage(SpykiteMessage.CMD_JAVASCRIPT, javaScript);
    }
    
    @Override
    public void clientMessagingConnectionOpened(MessagingConnection connection) {
        this.connection = connection;
        this.connection.addMessagingConnectionListener(this);
        
    }
    
    @Override
    public void incomingMessageData(MessagingConnection connection, MessageData messageData) {
        
        try {
            final SpykiteMessage browserMessage = new SpykiteMessage(messageData);
            
            Log.spykite.info(UnixDisplay.format(id + " {g}Client <-: " + browserMessage.toString() + "{}"));
            
            if (exiting) {
                sendSpykiteMessageToServer(SpykiteMessage.CMD_SHUTDOWN, StringUtil.EMPTY);
                return;
            }
            
            final int command = browserMessage.getCommand();
            
            switch (command) {
                case SpykiteMessage.CMD_EXIT:
                    sendSpykiteMessageToServer(SpykiteMessage.CMD_SHUTDOWN, StringUtil.EMPTY);
                    //dispatchBrowserExiting();

                    break;
                case SpykiteMessage.CMD_INIT:
                    dispatchSpykiteClientInit(browserMessage.getParams());
                    break;
                case SpykiteMessage.INFO_WORKER_STATE_CHANGE:
                    final Args a = new Args(browserMessage.getParams());
                    dispatchSpykiteStateChange(a.getString("-oldValue"), a.getString("-newValue"));
                    break;
            }
            
            dispatchIncomingSpykiteMessage(browserMessage);
            
        } catch (IOException ex) {
            Log.err(ex, ex);
        }
    }
    
    @Override
    public void clientMessagingConnectionClosed(MessagingConnection connection, Throwable exception) {
        if (exception != null) {
            new Thread(() -> {
                try {
                    
                    if (exiting) {
                        return;
                    }
                    if (closed) {
                        return;
                    }
                    dispatchSpykiteClientError(exception);
                    
                    if (reconnectEnabled) {
                        
                        if (exception instanceof ConnectException) {
                            Log.spykite.info("Waiting for Spykite browser server...");
                        } else {
                            if (closed) {
                                return;
                            }
                            Log.spykite.info("Disconnected, trying to reconnect...", exception);
                        }
                        Thread.sleep(RECONNECT_INTERVAL);
                        if (closed) {
                            return;
                        }
                        connect(getHost(), getPort());
                    } else {
                        
                    }
                } catch (InterruptedException | IOException ex1) {
                    Log.spykite.error(ex1, ex1);
                }
            }, "SK-clientMessagingConnectionClosed").start();
        }
    }
    
    @Override
    public void clientMessagingConnectionError(Throwable exception) {
        new Thread(() -> {
            try {
                
                if (exiting) {
                    return;
                }
                if (closed) {
                    return;
                }
                
                dispatchSpykiteClientError(exception);
                
                if (reconnectEnabled) {
                    
                    if (exception instanceof ConnectException) {
                        Log.spykite.info("Waiting for Spykite browser server...");
                    } else {
                        Log.spykite.info("Disconnected, trying to reconnect...", exception);
                    }
                    Thread.sleep(RECONNECT_INTERVAL);
                    
                    connect(getHost(), getPort());
                }
            } catch (InterruptedException | IOException ex1) {
                Log.spykite.error(ex1, ex1);
            }
        }, "SK-clientMessagingConnectionError").start();
    }

    //---------------------------------------------------------------------------------------------------
    //---------------------------------------------------------------------------------------------------
    //---------------------------------------------------------------------------------------------------
    //---------------------------------------------------------------------------------------------------
    public String getHost() {
        return connection == null ? host : connection.getHost();
    }
    
    public int getPort() {
        return connection == null ? port : connection.getPort();
    }
    
    private int sendSpykiteMessageToServer(int command, String params) throws IOException {
        final SpykiteMessage m = new SpykiteMessage(command, params);
        m.prepare();
        Log.spykite.info(UnixDisplay.format(id + " {c}Client ->: " + m + "{}"));
        connection.send(m);
        return m.getRequestId();
    }
    
    public int sendSpykiteMessage(int command, String params) {
        try {
            return sendSpykiteMessageToServer(command, params);
        } catch (IOException ex) {
            Log.err(ex, ex);
        }
        //waitForAnswer();
        return 0;
    }
    
    public int setContent(String html) {
        return sendSpykiteMessage(SpykiteMessage.CMD_CONTENT, html);
    }
    
    public void hardShutdownClient() {
        try {
            close();
        } catch (IOException ex) {
            Log.err(ex, ex);
        }
    }
    
    public void sendExit() {
        sendSpykiteMessage(SpykiteMessage.CMD_EXIT, StringUtil.EMPTY);
        //client.removeMessagingClientListener(this);
        exiting = true;
        Log.spykite.trace("exiting = true;");
        
    }
    
    public boolean isExiting() {
        return exiting;
    }
    
    public void show() {
        sendSpykiteMessage(SpykiteMessage.CMD_SHOW, StringUtil.EMPTY);
    }
    
    public void hide() {
        sendSpykiteMessage(SpykiteMessage.CMD_HIDE, StringUtil.EMPTY);
    }
    
    public void setAlwaysOnTop(boolean value) {
        sendSpykiteMessage(SpykiteMessage.CMD_ALWAYS_ON_TOP, "" + value);
    }
    
    @Override
    public void close() throws IOException {
        if (closed || connection == null) {
            throw new IOException("SpykiteClient is already not opened");
        }
        client.removeMessagingClientListener(this);
        closed = true;
        connection.close();
        
        spykiteClientListeners.clear();
    }
    
    public void setLocation(int x, int y) {
        final Args a = new Args();
        a.append("-x", x);
        a.append("-y", y);
        sendSpykiteMessage(SpykiteMessage.CMD_LOCATION, a.toString());
    }
    
    public void setX(int value) {
        final Args a = new Args();
        a.append("-x", value);
        sendSpykiteMessage(SpykiteMessage.CMD_LOCATION, a.toString());
    }
    
    public void setY(int value) {
        final Args a = new Args();
        a.append("-y", value);
        sendSpykiteMessage(SpykiteMessage.CMD_LOCATION, a.toString());
    }
    
    public void setSize(int w, int h) {
        final Args a = new Args();
        a.append("-w", w);
        a.append("-h", h);
        sendSpykiteMessage(SpykiteMessage.CMD_SIZE, a.toString());
    }
    
    public void setWidth(int value) {
        final Args a = new Args();
        a.append("-w", value);
        sendSpykiteMessage(SpykiteMessage.CMD_SIZE, a.toString());
    }
    
    public void setHeight(int value) {
        final Args a = new Args();
        a.append("-h", value);
        sendSpykiteMessage(SpykiteMessage.CMD_SIZE, a.toString());
    }
    
    public void requestLocation() {
        sendSpykiteMessage(SpykiteMessage.CMD_LOCATION, StringUtil.EMPTY);
    }
    
    public void requestSize() {
        sendSpykiteMessage(SpykiteMessage.CMD_SIZE, StringUtil.EMPTY);
    }
    
    public void requestUrl() {
        if (exiting) {
            return;
        }
        sendSpykiteMessage(SpykiteMessage.CMD_URL, StringUtil.EMPTY);
    }
    
    public void requestHtml() {
        if (exiting) {
            return;
        }
        sendSpykiteMessage(SpykiteMessage.CMD_HTML, StringUtil.EMPTY);
    }
    
    public void requestIsAlwaysOnTop() {
        sendSpykiteMessage(SpykiteMessage.CMD_ALWAYS_ON_TOP, StringUtil.EMPTY);
    }
    
    public boolean isAlwaysOnTop() {
        return syncGetBoolean("requestIsAlwaysOnTop");
    }
    
    public int getX() {
        return syncGetInt("requestLocation", "-x");
    }
    
    public int getY() {
        return syncGetInt("requestLocation", "-y");
    }
    
    public int getWidth() {
        return syncGetInt("requestSize", "-w");
    }
    
    public int getHeight() {
        return syncGetInt("requestSize", "-h");
    }
    
    public void setUrl(String url) {
        final Args args = new Args();
        args.append("-url", url);
        sendSpykiteMessage(SpykiteMessage.CMD_URL, args.toString());
    }
    
    public String getUrl() {
        if (!connection.isOpened()) {
            return null;
        }
        
        return syncGetString("requestUrl", "-url");
    }
    
    public String getHtml() {
        if (!connection.isOpened()) {
            return null;
        }
        
        return syncGetString("requestHtml");
    }
    
    private int syncGetInt(final String requestMethodName, final String key) {
        final Thread t = new Thread(() -> {
            connection.addMessagingConnectionListener(unlockListener);
            
            try {
                final Method method = SpykiteClient.class.getMethod(requestMethodName);
                method.invoke(SpykiteClient.this);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Log.err(ex, ex);
            }
        }, "SK-syncGetInt");
        t.start();
        
        try {
            synchronized (lock) {
                lock.wait();
            }
            
            connection.removeMessagingConnectionListener(unlockListener);
            final SpykiteMessage m = new SpykiteMessage(connection.getMessageData());
            final Args a = new Args(m.getParams());
            
            return a.getInt(key);
        } catch (InterruptedException | IOException ex) {
            Log.err(ex, ex);
        }
        return -1;
    }
    
    private void waitForAnswer() {
        connection.addMessagingConnectionListener(unlockListener);
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ex) {
                Log.err(ex, ex);
            }
        }
        connection.removeMessagingConnectionListener(unlockListener);
    }
    
    private String syncGetString(final String requestMethodName, final String key) {
        final Thread t = new Thread(() -> {
            connection.addMessagingConnectionListener(unlockListener);
            
            try {
                final Method method = SpykiteClient.class.getMethod(requestMethodName);
                method.invoke(SpykiteClient.this);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Log.err(ex, ex);
            }
        }, "SK-syncGetString");
        t.start();
        
        try {
            synchronized (lock) {
                lock.wait();
            }
            
            connection.removeMessagingConnectionListener(unlockListener);
            final SpykiteMessage m = new SpykiteMessage(connection.getMessageData());
            final Args a = new Args(m.getParams());
            return a.getString(key);
        } catch (InterruptedException | IOException ex) {
            Log.err(ex, ex);
        }
        return null;
    }
    
    private boolean syncGetBoolean(final String requestMethodName) {
        final Thread t = new Thread(() -> {
            connection.addMessagingConnectionListener(unlockListener);
            
            try {
                final Method method = SpykiteClient.class.getMethod(requestMethodName);
                method.invoke(SpykiteClient.this);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Log.err(ex, ex);
            }
        }, "SK-syncGetBoolean");
        t.start();
        
        try {
            synchronized (lock) {
                lock.wait();
            }
            
            connection.removeMessagingConnectionListener(unlockListener);
            final SpykiteMessage m = new SpykiteMessage(connection.getMessageData());
            return Boolean.valueOf(m.getParams());
        } catch (InterruptedException | IOException ex) {
            Log.err(ex, ex);
        }
        return false;
    }
    
    private String syncGetString(final String requestMethodName) {
        final Thread t = new Thread(() -> {
            connection.addMessagingConnectionListener(unlockListener);
            
            try {
                final Method method = SpykiteClient.class.getMethod(requestMethodName);
                method.invoke(SpykiteClient.this);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Log.err(ex, ex);
            }
        }, "SK-syncGetString");
        t.start();
        
        try {
            if (!exiting) {
                synchronized (lock) {
                    lock.wait();
                }
            }
            
            connection.removeMessagingConnectionListener(unlockListener);
            final SpykiteMessage m = new SpykiteMessage(connection.getMessageData());
            return m.getParams();
        } catch (InterruptedException | IOException ex) {
            Log.err(ex, ex);
        }
        return null;
    }
    
    public static final int killProcesses() {
        int count = 0;
        while (!processes.isEmpty()) {
            final Process process = processes.remove();
            if (process.isAlive()) {
                count ++;
                process.destroy();
                try {
                    process.waitFor();
                } catch (InterruptedException ex) {
                }
            }
        }
        return count;
    }
    
    public static final Process executeSpykiteBrowesrServer(String jar, int port, boolean quiet, boolean coloredLogs, boolean disableCache, String userAgent, int shutdownTimeout) throws IOException, InterruptedException {
        String javaInterpreter = System.getProperty("java.home") + "/bin/java";
        
        final List<String> commands = new ArrayList<>();
        
        commands.add(javaInterpreter);
        commands.add("-jar");
        commands.add(jar);
        commands.add("--port");
        commands.add("" + port);
        
        if (quiet) {
            commands.add("--quiet");
        }
        
        if (coloredLogs) {
            commands.add("--colored-logs");
        }
        
        if (disableCache) {
            commands.add("--disable-cache");
        }
        
        if (userAgent != null) {
            commands.add("--user-agent");
            commands.add(userAgent);
        }
        
        if (shutdownTimeout > 0) {
            commands.add("--shutdown-timeout");
            commands.add("" + shutdownTimeout);
        }
        
        final Process result = new ProcessBuilder(commands).start();
        
        processes.add(result);
        
        if (processes.size() > 100) {
            processes.poll();
        }
        
        return result;
    }
    
    @Override
    public void connectionOpened(MessagingConnection connection) {
        Log.spykite.info(id + " Client connection opened");
    }
    
    @Override
    public void connectionClosed(MessagingConnection connection, Throwable ex) {
        
        dispatchSpykiteExiting();
        connection.removeMessagingConnectionListener(this);
        connection.removeMessagingConnectionListener(unlockListener);
        
        closed = true;
        Log.spykite.info(UnixDisplay.format(id + " {r}Client connection closed{}"));
        synchronized (lock) {
            lock.notifyAll();
        }
    }
    
}

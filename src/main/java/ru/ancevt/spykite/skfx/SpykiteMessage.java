package ru.ancevt.spykite.skfx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import ru.ancevt.net.messaging.Log;
import ru.ancevt.net.messaging.message.Message;
import ru.ancevt.net.messaging.message.MessageData;
import ru.ancevt.util.string.ToStringBuilder;

/**
 *
 * @author ancevt
 */
public final class SpykiteMessage extends Message {

    public static final int INFO_OK = 255;

    public static final int CMD_LOCATION = 1;
    public static final int CMD_SIZE = 2;
    public static final int CMD_URL = 3;
    public static final int CMD_REFRESH = 4;
    public static final int CMD_CLOSE = 5;
    public static final int CMD_SHOW = 6;
    public static final int CMD_EXIT = 7;
    public static final int CMD_HIDE = 8;
    public static final int CMD_ALWAYS_ON_TOP = 9;
    public static final int CMD_SHUTDOWN = 10;

    public static final int INFO_WORKER_STATE_CHANGE = 11;
    public static final int INFO_STATUS_CHANGE = 12;

    public static final int CMD_INIT = 13;

    public static final int CMD_HTML = 20;
    public static final int CMD_CONTENT = 21;
    public static final int CMD_JAVASCRIPT = 22;

    private static String commandAsText(int command) {
        switch (command) {
            case INFO_OK:
                return "INFO_OK";
            case CMD_LOCATION:
                return "CMD_LOCATION";
            case CMD_SIZE:
                return "CMD_SIZE";
            case CMD_URL:
                return "CMD_URL";
            case CMD_REFRESH:
                return "CMD_REFRESH";
            case CMD_CLOSE:
                return "CMD_CLOSE";
            case CMD_SHOW:
                return "CMD_SHOW";
            case CMD_HIDE:
                return "CMD_HIDE";
            case CMD_EXIT:
                return "CMD_EXIT";
            case INFO_WORKER_STATE_CHANGE:
                return "INFO_WORKER_STATE_CHANGE";
            case INFO_STATUS_CHANGE:
                return "INFO_STATUS_CHANGE";
            case CMD_HTML:
                return "CMD_HTML";
            case CMD_ALWAYS_ON_TOP:
                return "CMD_ALWAYS_ON_TOP";
            case CMD_SHUTDOWN:
                return "CMD_SHUTDOWN";
            case CMD_INIT:
                return "CMD_INIT";
            case CMD_CONTENT:
                return "CMD_CONTENT";
            case CMD_JAVASCRIPT:
                return "CMD_JAVASCRIPT";
            default:
                return "Unknown";
        }
    }

    /*
     Message format:
     1 byte  - signature equals 0xFF
     4 bytes - message size
     4 bytes - req id
     ------------------------------------
     1 byte  - command
     1 byte  - answer (0/1)
     ? bytes - params
     */
    private int command;
    private boolean answer;
    private String params;

    public SpykiteMessage() {
        super();
    }

    public SpykiteMessage(int command, String params) {
        this();
        setCommand(command);
        setParams(params);
    }

    public SpykiteMessage(int command, String params, boolean answer) {
        this(command, params);
        setAnswer(answer);
    }

    public boolean isAnswer() {
        return answer;
    }

    public void setAnswer(boolean answer) {
        this.answer = answer;
    }

    public SpykiteMessage(MessageData messageData) throws IOException {
        super(messageData);

        setCommand(getDataInputStream().readUnsignedByte());
        setAnswer(getDataInputStream().readUnsignedByte() == 1);

        final byte[] bytes = new byte[length() - 11];
        getInputStream().read(bytes);

        setParams(new String(bytes));
    }

    public void setCommand(int command) {
        this.command = command;
    }

    public int getCommand() {
        return command;
    }

    public void setParams(String args) {
        this.params = args;
    }

    public String getParams() {
        return params;
    }

    @Override
    public Message prepare() {
        try {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                baos.write(getCommand());
                baos.write(isAnswer() ? 1 : 0);
                baos.write(getParams().getBytes());
                getMessageData().setBytes(getRequestId(), baos.toByteArray());
            }
        } catch (IOException ex) {
            Log.logger.error(ex);
        }

        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("requestId", getRequestId())
            .append("length", length())
            .append("command", commandAsText(command) + "(" + command + ")")
            .append("answer")
            .append("params")
            .build();
    }

}

package ru.ancevt.spykite.skfx;

/**
 * @author ancevt
 */
public interface SpykiteClientListener {
    void spykiteClientInit(String serverVersion);
    void spykiteClientError(Throwable exception);
    void spykiteStateChange(String oldValue, String value);
    void spykiteIncomingMessage(SpykiteMessage browserMessage);
    void spykiteExiting();
}

package ru.ancevt.spykite.skfx;

import java.io.IOException;
import ru.ancevt.util.string.StringUtil;

/**
 *
 * @author ancevt
 */
public class SpykiteKiller {
    
    public static final Object lock = new Object();
    
    public static final void kill(int port) throws IOException {
        SpykiteClient c = new SpykiteClient();
        c.addSpykiteClientListener(new SpykiteClientListener() {

            @Override
            public void spykiteClientInit(String serverVersion) {
                c.sendSpykiteMessage(SpykiteMessage.CMD_EXIT, StringUtil.EMPTY);
            }

            @Override
            public void spykiteClientError(Throwable exception) {
            }

            @Override
            public void spykiteStateChange(String oldValue, String value) {
            }

            @Override
            public void spykiteIncomingMessage(SpykiteMessage browserMessage) {
                if(browserMessage.getCommand() == SpykiteMessage.CMD_EXIT) {
                    c.sendSpykiteMessage(SpykiteMessage.CMD_SHUTDOWN, StringUtil.EMPTY);
                }
            }

            @Override
            public void spykiteExiting() {
                synchronized(lock) {
                    lock.notifyAll();
                }
            }
        });
        
        c.connect(port);
        
        synchronized(lock) {
            try {
                lock.wait();
            } catch (InterruptedException ex) {
            }
        }
    }
}

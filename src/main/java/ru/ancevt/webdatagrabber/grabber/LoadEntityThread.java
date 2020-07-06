package ru.ancevt.webdatagrabber.grabber;

import java.io.IOException;
import ru.ancevt.webdatagrabber.ds.IEntity;
import ru.ancevt.webdatagrabber.api.IRequester;
import ru.ancevt.webdatagrabber.log.Log;

/**
 *
 * @author ancevt
 * @param <T>
 */
public class LoadEntityThread<T extends IEntity> extends Thread {

    private static final int MAX_TRIES = 10;
    private static final int RETRYING_DELAY = 1000;

    private final int id;
    private int tries;
    private final IRequester<T> requester;

    private T result;

    public LoadEntityThread(IRequester<T> requester, int id) {
        this.id = id;
        this.requester = requester;
        tries = MAX_TRIES;
    }

    @Override
    public void run() {
        if (tries <= 0 || id == 0) {
            return;
        }

        try {
            result = requester.get(id);
        } catch (IOException ex) {
            Log.getLogger().error("Retrying... tries: " + tries, ex);
            tries--;
            try {
                Thread.sleep(RETRYING_DELAY);
            } catch (InterruptedException ex1) {
                ex1.printStackTrace();
            }
            run();
        }
    }

    public final T getResult() {
        return result;
    }

}

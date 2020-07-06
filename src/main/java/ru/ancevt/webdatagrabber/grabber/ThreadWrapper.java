package ru.ancevt.webdatagrabber.grabber;

import java.util.ArrayList;
import java.util.List;
import ru.ancevt.webdatagrabber.ds.IEntity;

/**
 * @author ancevt
 * @param <T>
 */
public class ThreadWrapper<T extends IEntity> {

    private final List<LoadEntityThread<T>> list;

    public ThreadWrapper() {
        list = new ArrayList<>();
    }

    public final void add(LoadEntityThread thread) {
        list.add(thread);
    }

    public final void start() {
        list.stream().forEach((t) -> {
            t.start();
        });
    }

    public final void join() {
        list.stream().forEach((t) -> {
            try {
                t.join();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        });
    }

    public final void clear() {
        list.clear();
    }

    public final IEntity[] getResults() {
        final IEntity[] results = new IEntity[size()];

        for (int i = 0; i < results.length; i++) {
            results[i] = list.get(i).getResult();
        }

        return results;
    }

    public final int size() {
        return list.size();
    }

}

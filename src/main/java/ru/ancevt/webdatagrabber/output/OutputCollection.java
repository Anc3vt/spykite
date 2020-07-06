package ru.ancevt.webdatagrabber.output;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ru.ancevt.webdatagrabber.ds.IEntity;

/**
 *
 * @author ancevt
 */
public class OutputCollection implements IOutput<IEntity> {

    private final List<IOutput> list;

    public OutputCollection() {
        list = new CopyOnWriteArrayList<>();
    }

    public void add(IOutput output) {
        list.add(output);
    }

    public int size() {
        return list.size();
    }

    public void remove(IOutput output) {
        list.remove(output);
    }

    @Override
    public void output(IEntity entity) {
        for(int i = 0; i < list.size(); i ++) {
            list.get(i).output(entity);
        }
    }

    @Override
    public void close() throws IOException {
        for (final IOutput ivo : list) {
            ivo.close();
        }
    }

}

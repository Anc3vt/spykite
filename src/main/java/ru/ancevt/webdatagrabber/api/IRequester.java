package ru.ancevt.webdatagrabber.api;

import java.io.IOException;
import ru.ancevt.webdatagrabber.ds.IEntity;

/**
 * @author ancevt
 * @param <T>
 */
public interface IRequester<T extends IEntity> {
    int getStatus();
    T get(int id) throws IOException;
}

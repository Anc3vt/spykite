package ru.ancevt.webdatagrabber.output;

import java.io.Closeable;

/**
 * @author ancevt
 */
public interface IOutput<T> extends Closeable {
    void output(T obj);
}

package ru.ancevt.webdatagrabber.api;

import java.io.IOException;

/**
 *
 * @author ancevt
 */
public interface IListRequester {
    int request(int page) throws IOException;
    int[] getIds();
}

package ru.ancevt.webdatagrabber.config;

import ru.ancevt.util.ini.Ini;

/**
 * @author ancevt
 */
public abstract class Config {

    protected static final String SEC_COMMON = "Common";
    private static final String THREADS = "threads";
    private static final int DEFAULT_THREADS = 50;

    private static final String NULLS_IN_ROW_TO_EXIT = "nulls_in_row_to_exit";
    private static final int DEFAULT_NULLS_IN_ROW_TO_EXIT = 500;

    protected final Ini ini;

    public Config(String configSource) {
        this.ini = new Ini(configSource);
    }

    public final Ini getIni() {
        return ini;
    }

    public int getThreads() {
        return ini.getInt(SEC_COMMON, THREADS, DEFAULT_THREADS);
    }
     
    public int getNullsInRowToExit() {
        return ini.getInt(SEC_COMMON, NULLS_IN_ROW_TO_EXIT, DEFAULT_NULLS_IN_ROW_TO_EXIT);
    }

    public abstract void validate() throws ConfigException;

}

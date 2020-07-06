package ru.ancevt.webdatagrabber.log;

import org.apache.log4j.Logger;

/**
 *
 * @author ancevt
 */
public class Log {
    private static final Logger logger = Logger.getLogger("WDG");
    private static final Logger devLogger = Logger.getLogger("DEV");
    
    public static final Logger getLogger() {
        return logger;
    }
    
    public static final Logger getDevLogger() {
        return devLogger;
    }
}

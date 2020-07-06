package ru.ancevt.spykite;

import org.apache.log4j.Logger;

/**
 *
 * @author ancevt
 */
public class Log {

    public static final Logger spykite = Logger.getLogger("Spykite");
    public static final Logger hl = Logger.getLogger("HTMLLoader");
    
    public final static void err(Object text, Throwable th) {
        spykite.error(text, th);
    }
    
    public final static void err(Throwable th) {
        spykite.error(th);
    }
}

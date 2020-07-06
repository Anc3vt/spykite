package ru.ancevt.webdatagrabber.config;

/**
 *
 * @author ancevt
 */
public class ConfigException extends Exception {

    /**
     * Creates a new instance of <code>ConfigException</code> without detail
     * message.
     */
    public ConfigException() {
    }

    /**
     * Constructs an instance of <code>ConfigException</code> with the specified
     * detail message.
     *
     * @param msg the detail message.
     */
    public ConfigException(String msg) {
        super(msg);
    }
}

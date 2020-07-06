package ru.ancevt.spykite.skfx;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import ru.ancevt.spykite.Log;

/**
 *
 * @author ancevt
 */
public final class SpykiteCacheCleaner {

    public static void main(String[] args) {
        System.out.println(System.getProperty("user.home"));
    }

    private static final String CACHE_PATH = ".ru.ancevt.spykite.remote.SpykiteFX";

    public static final void clear() throws IOException {
        if (System.getProperty("user.home").startsWith("/home")) {
            final File file = new File(System.getProperty("user.home") + "/" + CACHE_PATH);
            if (file.exists() && file.isDirectory()) {
                delete(file);
                Log.spykite.info("Cache deleted");
            }
        } else {
            final File file = new File(System.getProperty("user.home") + "\\AppData\\Roaming\\ru.ancevt.spykite.remote.SpykiteFX\\");
            if (file.exists() && file.isDirectory()) {
                delete(file);
                Log.spykite.info("Cache deleted");
            }
        }
    }

    private static final void delete(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                delete(c);
            }
        }
        if (!f.delete()) {
            throw new FileNotFoundException("Failed to delete file: " + f);
        }
    }
}

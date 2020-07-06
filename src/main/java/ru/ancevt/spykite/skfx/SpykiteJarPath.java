package ru.ancevt.spykite.skfx;

/**
 *
 * @author ancevt
 */
public class SpykiteJarPath {
    
    public static final String DEFAULT_BFX_JAR_PATH = "skfx.jar";
    
    private static String bfxJarPath = DEFAULT_BFX_JAR_PATH;

    public static String getBrowserJarPath() {
        return bfxJarPath;
    }

    public static void setBrowserFXJarPath(String bfxJarPath) {
        SpykiteJarPath.bfxJarPath = bfxJarPath;
    }
}

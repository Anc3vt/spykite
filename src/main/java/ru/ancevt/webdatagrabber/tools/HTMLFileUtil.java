package ru.ancevt.webdatagrabber.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 *
 * @author ancevt
 */
public class HTMLFileUtil {

    private String html;
    private File file;

    public void setHTML(String html) {
        this.html = html;
    }

    public String getHTML() {
        return html;
    }

    public void save(File file) {
        this.file = file;

        try {
            final OutputStream outputStream = new FileOutputStream(file);
            outputStream.write(html.getBytes());
            outputStream.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void browse() {
        try {
            Runtime rt = Runtime.getRuntime();
            Process pr = rt.exec("firefox " + file.getAbsolutePath());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void browse(String html, File file) {
        final HTMLFileUtil htmlFileUtil = new HTMLFileUtil();
        htmlFileUtil.setHTML(html);
        htmlFileUtil.save(file);
        htmlFileUtil.browse();
    }
}

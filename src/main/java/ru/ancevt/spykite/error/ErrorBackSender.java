package ru.ancevt.spykite.error;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.rauschig.jarchivelib.ArchiveFormat;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.rauschig.jarchivelib.CompressionType;
import ru.ancevt.net.httpclient.HttpClient;
import ru.ancevt.net.httpclient.HttpMethod;
import ru.ancevt.net.httpclient.HttpRequest;
import ru.ancevt.net.httpclient.HttpRequestMaker;
import ru.ancevt.net.httpclient.MimeType;
import ru.ancevt.net.httpclient.MultipartBodyMaker;
import ru.ancevt.spykite.GlobalInfo;
import ru.ancevt.spykite.Log;
import ru.ancevt.util.fs.SimpleFileReader;
import ru.ancevt.util.fs.SimpleFileWriter;
import ru.ancevt.util.ini.Ini;

/**
 *
 * @author ancevt
 */
public class ErrorBackSender {

    public static final String DEFAULT_URL = "http://ancevt.ru:2245/notifyerror";

    private final String url;
        

    public ErrorBackSender(String url) {
        this.url = url;
    }

    public ErrorBackSender() {
        this.url = DEFAULT_URL;
    }

    public void send(Throwable exception, Ini config, String extraData) throws IOException {
        try {
            final MultipartBodyMaker mbm = new MultipartBodyMaker();

            final StringBuilder sb = new StringBuilder();
            sb.append(GlobalInfo.lastSKFXVersionInfo).
                append("\r\n");
            sb.append(extraData).append("\r\n");

            if (exception != null) {
                sb.append(exception.toString()).append("\r\n");
                for (StackTraceElement stackTrace : exception.getStackTrace()) {
                    sb.append(stackTrace.toString()).append("\r\n");
                }
            }

            sb.append("\r\n\r\n------------CONFIG--------------\r\n");
            sb.append(config.stringify().trim());
            sb.append("\r\n------------END OF CONFIG--------------\r\n");

            final Archiver archiver
                = ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP);

            final String absPath = new File("").getAbsolutePath();

            final File logDir = new File("log");
            final File logToSend = new File("logToSend");
            logToSend.mkdir();
            for (File current : logDir.listFiles()) {
                final byte[] data = SimpleFileReader.readAllBytes(current);
                SimpleFileWriter.write(new File(logToSend.getAbsolutePath() + "/" + current.getName()), data);
            }

            final File excDir = new File("exceptions");
            final File excToSend = new File("exceptionsToSend");
            excToSend.mkdir();
            for (File current : excDir.listFiles()) {
                final byte[] data = SimpleFileReader.readAllBytes(current);
                SimpleFileWriter.write(new File(excToSend.getAbsolutePath() + "/" + current.getName()), data);
            }
            tar(logToSend.getName(), absPath, "log.tar.gz");
            mbm.add("file1", MimeType.APPLICATION_GZIP, "log.tar.gz", new File("log.tar.gz"));

            tar(excToSend.getName(), absPath, "exceptions.tar.gz");
            mbm.add("file2", MimeType.APPLICATION_GZIP, "exceptions.tar.gz", new File("exceptions.tar.gz"));

            deleteDir(logToSend);
            deleteDir(excToSend);

            mbm.add("message", MimeType.TEXT_PLAIN, sb.toString());

            final HttpRequestMaker maker = new HttpRequestMaker();
            //maker.addStandardDefaultHeaders();
            final HttpRequest req = maker.create(url, HttpMethod.POST, null);
            req.setHeader("Content-Type", MimeType.MULTIPART_FORM_DATA + "; boundary=" + mbm.getBoundary());
            req.setBody(mbm.getResult());
            
            try (HttpClient client = new HttpClient()) {
                client.connect(req);
                
                Log.spykite.info("Sending error " + client.getStatus() + " " + client.getStatusText());
            }
        } catch (TimeoutException ex) {
            Log.spykite.error(ex, ex);
        }
    }

    public void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (final File file : files) {
                deleteDir(file);
            }
        }
        dir.delete();
    }

    /**
     * @param directory The directory to tar
     * @param location The tarred file's location (/home/exampleUser/Desktop/)
     * @param name The name of the file (example.zip)
     * @throws IOException Oh no! Something went wrong!
     */
    public static void tar(String directory, String location, String name) throws IOException {
        Archiver archive = ArchiverFactory.createArchiver("tar", "gz");
        archive.create(
            name,
            new File(location),
            new File(directory)
        );
    }
    
    private static String getFormattedDate() {
        final String pattern = "yyyy_MM_dd__hh_mm_ss_SSS";
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern, new Locale("ru", "RU"));
        final String date = simpleDateFormat.format(new Date());
        return date;
    }

}

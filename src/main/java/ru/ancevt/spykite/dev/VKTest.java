package ru.ancevt.spykite.dev;

import ru.ancevt.spykite.grabbers.HtmlLoaderException;
import ru.ancevt.spykite.grabbers.ManualDynamicHtmlLoader;
import ru.ancevt.util.args.Args;
import ru.ancevt.webdatagrabber.api.Doc;

/**
 *
 * @author ancevt
 */
public class VKTest {

    public static void main(String[] args) {
        new VKTest(new Args());
    }

    private ManualDynamicHtmlLoader loader;
    private Object lock = new Object();
    
    public VKTest(Args args) {
        new Thread(() -> startBrowser(), "startBrowser").start();
        new Thread(() -> checkText(), "checkText").start();
    }

    private void startBrowser() {
        try {
            loader = new ManualDynamicHtmlLoader();
            loader.setQuiet(false);
            loader.setUserAgent("Mozilla/5.0 (X11; Linux x86_64; rv:68.0) Gecko/20100101 Firefox/68.0");
            loader.setCacheDisabled(false);
            loader.setSupressJavaErrorLogs(false);
            loader.prepareResources();
            loader.setUrl("https://vk.com/");

            synchronized(lock) {
                lock.notifyAll();
            }

        } catch (HtmlLoaderException ex) {
            ex.printStackTrace();
        }
    }

    private void checkText() {

        synchronized(lock) {
            try {
                lock.wait();
            } catch (InterruptedException ex) {
            }
        }
        
        
        String text = null;
        
        while (true) {
            try {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                }
                
                

                final String html = loader.loadHtml();
                final Doc doc = new Doc(html);

                final String newText = doc.getTextByChildHasParent("current_text", "my_current_info");
                
                if((newText != null) && !newText.equals(text)) {
                    text = newText;
                    System.out.println(text);
                }
                
                
            } catch (HtmlLoaderException ex) {
                ex.printStackTrace();
            }
        }
    }
}

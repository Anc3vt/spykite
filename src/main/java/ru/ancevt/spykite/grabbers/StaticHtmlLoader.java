package ru.ancevt.spykite.grabbers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeoutException;
import ru.ancevt.net.httpclient.GZIPUtil;
import ru.ancevt.net.httpclient.HttpClient;
import ru.ancevt.net.httpclient.HttpHeader;
import ru.ancevt.net.httpclient.HttpMethod;
import ru.ancevt.net.httpclient.HttpRequest;
import ru.ancevt.net.httpclient.HttpRequestMaker;
import ru.ancevt.util.string.StringUtil;

/**
 *
 * @author ancevt
 */
public class StaticHtmlLoader implements IHtmlLoader {

    public static void main(String[] args) throws HtmlLoaderException {
        final StaticHtmlLoader staticHtmlLoader = new StaticHtmlLoader();
        staticHtmlLoader.setUrl("https://cian.ru/cat.php?engine_version=2&deal_type=rent&type=4&sort=street_name&region=2&district[0]=136&district[1]=722&offer_type=flat&room1=1&room2=1&room3=1&p=2182");
        System.out.println(staticHtmlLoader.getPort());
        final String html = staticHtmlLoader.loadHtml();

        System.out.println(StringUtil.cut(html, 100));
    }

    public static final int DEFAULT_PORT_HTTP = 80;
    public static final int DEFAULT_PORT_TEST = 8080;
    public static final int DEFAULT_PORT_HTTPS = 443;

    private Object debug;
    private String html;
    private int port;
    private String url;
    private final HttpRequestMaker httpRequestMaker;
    private final HttpRequest request;
    private URL urlObj;
    private String userAgent;
    private boolean cacheDisabled;

    public StaticHtmlLoader() {
        httpRequestMaker = new HttpRequestMaker();
        httpRequestMaker.addStandardDefaultHeaders();
        request = httpRequestMaker.create(null, HttpMethod.GET, null);
    }

    @Override
    public void setPort(int port) throws HtmlLoaderException {
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void setQuiet(boolean value) {
    }

    @Override
    public boolean isQuiet() {
        return true;
    }

    @Override
    public void setUrl(String url) throws HtmlLoaderException {
        this.url = url;
        try {
            urlObj = new URL(url);

            if (urlObj.getProtocol().contains("https")) {
                port = DEFAULT_PORT_HTTPS;
            } else if (urlObj.getProtocol().contains("http")) {
                port = DEFAULT_PORT_HTTP;
            }

            request.setUrl(url);
            request.setHeader(HttpHeader.HOST, urlObj.getHost());

        } catch (MalformedURLException ex) {
            throw new HtmlLoaderException(ex.toString(), ex, new HtmlLoaderResult());
        }

    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public void setContent(String html) throws HtmlLoaderException {
    }

    @Override
    public String getContent() {
        return null;
    }

    @Override
    public void addIncludeWords(String... words) {

    }

    @Override
    public void addFailWords(boolean skip, String... words) {
    }

    @Override
    public int getRequestInterval() {
        return 0;
    }

    @Override
    public void setRequestInterval(int requestInterval) {

    }

    @Override
    public int getTimeout() {
        return request.getTimeout();
    }

    @Override
    public void setTimeout(int timeout) {
        request.setTimeout(timeout);
    }

    @Override
    public boolean isSupressJavaErorrLogs() {
        return false;
    }

    @Override
    public void setSupressJavaErrorLogs(boolean notUsed) {

    }

    @Override
    public String loadHtml() throws HtmlLoaderException {
        HttpClient client = new HttpClient();
        try {
            client.connect(request);

            if (userAgent != null) {
                request.setHeader(HttpHeader.USER_AGENT, userAgent);
            }
            
            while (client.getStatus() == 302 || client.getStatus() == 301) {
                final String newUrl = client.getHeaderValue(HttpHeader.LOCATION);
                request.setUrl(newUrl);
                request.setHeader(HttpHeader.HOST, new URL(newUrl).getHost());

                client.close();

                client = new HttpClient();
                client.connect(request);
            }

            if (client.getStatus() == 200) {
                final boolean gzip = "gzip".equalsIgnoreCase(client.getHeaderValue(HttpHeader.CONTENT_ENCODING));
                byte[] bytes = client.readBytes();
                html = gzip ? GZIPUtil.decompress(bytes) : new String(bytes);
                client.close();
            }

        } catch (IOException | TimeoutException ex) {
            throw new HtmlLoaderException(ex.toString(), ex, new HtmlLoaderResult());
        }

        System.out.println("Status:" + client.getStatus());

        if (html == null) {
            throw new HtmlLoaderException("html not loaded == null", new HtmlLoaderResult());
        }

        return html;
    }

    @Override
    public boolean isHtmlLoaded() {
        return html != null;
    }

    @Override
    public void shutdown() throws HtmlLoaderException {
    }

    @Override
    public void prepareResources() throws HtmlLoaderException {
    }

    @Override
    public String executeJavaScript(String javaScript) throws HtmlLoaderException {
        return null;
    }

    @Override
    public String getCurrentHtml() {
        return html;
    }

    @Override
    public void setDebug(Object debug) {
        this.debug = debug;
    }

    @Override
    public Object getDebug() {
        return debug;
    }

    @Override
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public String getUserAgent() {
        return userAgent;
    }
    
    @Override
    public void setCacheDisabled(boolean cacheDisabled) {
        this.cacheDisabled = cacheDisabled;
    }

    @Override
    public boolean isCacheDisabled() {
        return cacheDisabled;
    }
}

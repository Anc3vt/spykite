package ru.ancevt.webdatagrabber.ds;

import ru.ancevt.util.string.ToStringBuilder;

/**
 * @author ancevt
 */
public class Image {

    private byte[] bytes;
    private String alt;
    private String source;
    private String mimeType;

    public Image() {

    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public final String getAlt() {
        return alt;
    }

    public final void setAlt(String alt) {
        this.alt = alt;
    }

    public final byte[] getBytes() {
        return bytes;
    }

    public final void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public final int getLength() {
        return bytes != null ? bytes.length : 0;
    }

    public final boolean isEmpty() {
        return getLength() == 0;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("length", getLength())
            .append("alt", alt)
            .append("source", source)
            .append("mimeType", mimeType)
            .build();
    }

}

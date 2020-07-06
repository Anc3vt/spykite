package ru.ancevt.spykite.xls;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author ancevt
 */
public class Xls {

    private final List<XlsSheet> sheets;
    private int sheetCounter;

    public Xls() {
        sheets = new CopyOnWriteArrayList<>();
    }

    public XlsSheet createSheet(String name) {
        final XlsSheet s = new XlsSheet(name);
        sheets.add(s);
        return s;
    }

    public XlsSheet createSheet() {
        sheetCounter++;
        return createSheet("Sheet #" + sheetCounter);
    }

    public final int getSheetCount() {
        return sheets.size();
    }

    public final XlsSheet getSheet(int index) {
        return sheets.get(index);
    }

    public void writeToFile(File file) throws IOException {
        XlsFileWriter.write(this, file);
    }

}

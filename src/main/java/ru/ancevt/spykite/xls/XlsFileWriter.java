package ru.ancevt.spykite.xls;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFHyperlink;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 *
 * @author ancevt
 */
public class XlsFileWriter {

    public static void main(String[] args) throws IOException {
        final int[] widths = new int[]{100, 100, 100, 100, 100, 100, 100};
        final String[] titles = new String[]{"one", "two", "three", "four", "five", "six", "seven"};

        final Xls xls = new Xls();

        for (int i = 0; i < 5; i++) {
            final XlsSheet xs = xls.createSheet("Sheet " + (i + 1));

            xs.setColumnWidths(widths);
            xs.setTitles(titles);

            for (int j = 0; j < 100; j++) {
                xs.addRow(new String[]{
                    "one",
                    "two",
                    "three",
                    "four",
                    "five",
                    "six[@link]http://ancevt.ru",
                    "seven"
                });
            }

        }

        xls.writeToFile(new File("/tmp/test.xls"));
    }

    private static final int SIZE_FACTOR = 36;

    static void write(Xls xls, File file) throws IOException {
        final XlsFileWriter w = new XlsFileWriter(xls, file);
        w.build();
        w.write();
    }

    private final Xls xls;
    private final File file;

    private Workbook workbook;
    private Sheet currentSheet;
    private final Font defaultFont;
    private final CellStyle hyperlinkStyle;

    private XlsFileWriter(Xls xls, File file) {
        this.xls = xls;
        this.file = file;

        createWorkbook();

        defaultFont = workbook.createFont();
        defaultFont.setColor(IndexedColors.BLACK.getIndex());

        final Font linkFont = workbook.createFont();
        hyperlinkStyle = workbook.createCellStyle();
        linkFont.setUnderline(Font.U_SINGLE);
        linkFont.setColor(IndexedColors.BLUE.getIndex());
        hyperlinkStyle.setFont(linkFont);
        hyperlinkStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        hyperlinkStyle.setFillBackgroundColor(IndexedColors.WHITE.getIndex());
    }

    private void build() {
        for (int i = 0; i < xls.getSheetCount(); i++) {
            final XlsSheet s = xls.getSheet(i);
            buildSheet(s);
        }
    }

    private void buildSheet(XlsSheet sheet) {
        currentSheet = workbook.createSheet(sheet.getLabel());

        // Create title:
        final int[] widths = sheet.getColumnWidths();
        final String[] titles = sheet.getTitles();

        for (int i = 0; i < widths.length; i++) {
            final int w = widths[i];

            currentSheet.setColumnWidth(i, w * SIZE_FACTOR);
        }

        final Row titleRow = currentSheet.createRow(0);
        final CellStyle titleStyle = workbook.createCellStyle();
        final Font font = workbook.createFont();
        font.setBold(true);
        titleStyle.setFont(font);
        titleStyle.setAlignment(CellStyle.ALIGN_CENTER);
        titleStyle.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
        titleStyle.setFillForegroundColor(HSSFColor.GREY_25_PERCENT.index);

        for (int i = 0; i < titles.length; i++) {
            final String t = titles[i];
            titleRow.createCell(i).setCellValue(t);
            titleRow.getCell(i).setCellStyle(titleStyle);
        }

        // Create rows:
        for (int i = 0; i < sheet.getRowCount(); i++) {
            final String[] values = sheet.getValues(i);

            final int currentRowNum = currentSheet.getLastRowNum() + 1;
            final Row row = currentSheet.createRow(currentRowNum);

            for (int j = 0; j < values.length; j++) {
                final String v = values[j];

                if (v == null) {
                    continue;
                }

                final Cell cell = row.createCell(j);

                if (cell == null) {
                    continue;
                }

                if (v.contains("[@link]")) {
                    final String[] s = v.split("\\[@link\\]");
                    final String label = s[0];
                    final String url = s[1];

                    cell.setCellValue(label);

                    final Hyperlink hyperlink = new HSSFHyperlink(Hyperlink.LINK_URL);
                    hyperlink.setAddress(url);
                    hyperlink.setLabel(label);
                    cell.setHyperlink(hyperlink);
                    cell.setCellStyle(hyperlinkStyle);
                } else {
                    cell.setCellValue(v);
                    cell.getCellStyle().setFont(defaultFont);
                }
            }
        }
    }

    private void write() throws IOException {
        if (workbook != null) {
            workbook.write(new FileOutputStream(file));
            workbook.close();
        }
    }

    private void createWorkbook() {
        if (workbook == null) {
            workbook = new HSSFWorkbook();
        }
    }
}

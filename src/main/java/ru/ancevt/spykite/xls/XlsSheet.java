package ru.ancevt.spykite.xls;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ru.ancevt.util.string.ToStringBuilder;

/**
 * @author ancevt
 */
public class XlsSheet {

    private static int counter;

    private final List<String[]> rows;
    private String label;
    private int[] columnWidths;
    private String[] titles;

    XlsSheet() {
        counter++;
        this.label = "Sheet #" + counter;
        this.rows = new CopyOnWriteArrayList<>();
    }

    XlsSheet(String label) {
        this();
        this.label = label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public final void setColumnWidths(int[] widths) {
        this.columnWidths = widths;
    }

    public final void setTitles(String[] titles) {
        this.titles = titles;
    }

    /**
     * for hyperlink use value: label[@link]url
     *
     * @param values
     */
    public final void addRow(String[] values) {
        rows.add(values);
    }

    public final int[] getColumnWidths() {
        return columnWidths;
    }

    public final String[] getTitles() {
        return titles;
    }

    public final int getRowCount() {
        return rows.size();
    }
    
    public final String[] getValues(int index) {
        return rows.get(index);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .appendAll("rowCount")
            .build();
    }

}

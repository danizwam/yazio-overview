package de.yazio.overview.export;

import de.yazio.overview.model.Domain.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static de.yazio.overview.model.Labels.*;

/** Exportiert Tagesberichte im Office-Format. */
public final class XlsxExport {
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMANY);

    public static byte[] write(List<DayReport> reports) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            entry(zip, "[Content_Types].xml", contentTypes(reports.size()));
            entry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRels(reports.size()));
            entry(zip, "xl/styles.xml", styles());
            entry(zip, "xl/workbook.xml", workbook(reports));
            for (int i = 0; i < reports.size(); i++) {
                entry(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheet(reports.get(i)));
            }
        }
        return bytes.toByteArray();
    }

    private static String contentTypes(int count) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                """);
        for (int i = 1; i <= count; i++) {
            xml.append("  <Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n");
        }
        xml.append("</Types>");
        return xml.toString();
    }

    private static String workbookRels(int count) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                """);
        for (int i = 1; i <= count; i++) {
            xml.append("  <Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>\n");
        }
        xml.append("</Relationships>");
        return xml.toString();
    }

    private static String workbook(List<DayReport> reports) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                """);
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; i < reports.size(); i++) {
            String name = uniqueSheetName(reports.get(i).date(), names);
            xml.append("    <sheet name=\"").append(x(name)).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>\n");
        }
        xml.append("  </sheets>\n</workbook>");
        return xml.toString();
    }

    private static String sheet(DayReport report) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(24, List.of(new Cell("Meine Tagesübersicht", 1), new Cell(""), new Cell(""))));
        rows.add(new Row(18, List.of(new Cell(""), new Cell(""), new Cell(""))));
        rows.add(new Row(22, List.of(new Cell("Name: " + lineValue(report.settings().name(), 24), 2), new Cell("Geb. Datum: " + dateLineValue(report.settings().birthDate(), 18), 2), new Cell(""))));
        rows.add(new Row(22, List.of(new Cell("Datum: " + report.date().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), 2), new Cell("Wochentag: " + report.date().format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMANY)), 2), new Cell(""))));
        rows.add(new Row(14, List.of(new Cell(""), new Cell(""), new Cell(""))));
        rows.add(new Row(22, List.of(new Cell("Mahlzeit", 4), new Cell("gegessen wurde", 4), new Cell("getrunken wurde...", 4))));

        for (MealReport meal : exportMeals(report.meals()).values()) {
            rows.add(new Row(86, List.of(
                    new Cell(mealMacroBlock(meal), 5),
                    new Cell(joinItems(meal, false), 7),
                    new Cell(joinItems(meal, true), 7)
            )));
        }
        rows.add(new Row(22, List.of(new Cell("Gesamt:", 6), new Cell(report.total().inline(), 6), new Cell("", 6))));
        rows.add(new Row(18, List.of(new Cell(""), new Cell(""), new Cell(""))));
        rows.add(new Row(24, List.of(new Cell("Sport:", 2), new Cell(""), new Cell(""))));
        rows.add(new Row(24, List.of(new Cell("Besonderheiten an diesem Tag:", 2), new Cell(report.note() == null ? "" : report.note(), 2), new Cell(""))));

        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <cols>
                    <col min="1" max="1" width="24" customWidth="1"/>
                    <col min="2" max="2" width="48" customWidth="1"/>
                    <col min="3" max="3" width="48" customWidth="1"/>
                  </cols>
                  <sheetData>
                """);
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            xml.append("    <row r=\"").append(r + 1).append("\" ht=\"").append(row.height()).append("\" customHeight=\"1\">\n");
            for (int c = 0; c < row.size(); c++) {
                Cell cell = row.cells().get(c);
                String ref = column(c + 1) + (r + 1);
                if (cell.text != null) {
                    xml.append("      <c r=\"").append(ref).append("\" t=\"inlineStr\"")
                            .append(cell.style > 0 ? " s=\"" + cell.style + "\"" : "")
                            .append("><is><t>").append(x(cell.text)).append("</t></is></c>\n");
                } else {
                    xml.append("      <c r=\"").append(ref).append("\"")
                            .append(cell.style > 0 ? " s=\"" + cell.style + "\"" : "")
                            .append("><v>").append(round(cell.number)).append("</v></c>\n");
                }
            }
            xml.append("    </row>\n");
        }
        xml.append("""
                  </sheetData>
                  <mergeCells count="1">
                    <mergeCell ref="A1:C1"/>
                  </mergeCells>
                </worksheet>
                """);
        return xml.toString();
    }

    private static String styles() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="18"/><color rgb="FF000000"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FF000000"/><name val="Calibri"/></font></fonts>
                  <fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFF2F2F2"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE7E7E7"/><bgColor indexed="64"/></patternFill></fill></fills>
                  <borders count="2"><border/><border><left style="thin"><color rgb="FF000000"/></left><right style="thin"><color rgb="FF000000"/></right><top style="thin"><color rgb="FF000000"/></top><bottom style="thin"><color rgb="FF000000"/></bottom></border></borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="8"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyFill="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0"><alignment vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyFill="1"><alignment vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0"><alignment vertical="top" wrapText="1"/></xf></cellXfs>
                </styleSheet>
                """;
    }

    private static String joinItems(MealReport meal, boolean drinks) {
        List<String> lines = meal.items().stream()
                .filter(item -> isDrink(item) == drinks)
                .map(item -> itemLine(item))
                .toList();
        return String.join("\n", lines);
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String uniqueSheetName(LocalDate date, Set<String> used) {
        String base = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String name = base;
        int counter = 2;
        while (!used.add(name)) {
            name = base + " " + counter++;
        }
        return name;
    }

    private static String column(int index) {
        StringBuilder column = new StringBuilder();
        while (index > 0) {
            index--;
            column.insert(0, (char) ('A' + index % 26));
            index /= 26;
        }
        return column.toString();
    }

    private static String productLabel(FoodItem item) {
        return item.producer() == null ? item.name() : item.name() + " - " + item.producer();
    }

    private static String amountLabel(FoodItem item) {
        return item.amountLabel();
    }

    private static String x(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    record Row(int height, List<Cell> cells) {
        int size() {
            return cells.size();
        }
    }

    record Cell(String text, double number, int style) {
        Cell(String text) {
            this(text, 0, 0);
        }

        Cell(String text, int style) {
            this(text, 0, style);
        }

        Cell(double number) {
            this(null, number, 0);
        }

        Cell(double number, int style) {
            this(null, number, style);
        }
    }
}

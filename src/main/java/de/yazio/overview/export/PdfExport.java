package de.yazio.overview.export;

import de.yazio.overview.model.Domain.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static de.yazio.overview.model.Labels.*;

/** Exportiert Tagesberichte als schlichtes PDF. */
public final class PdfExport {
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMANY);

    public static byte[] write(List<DayReport> reports) throws IOException {
        List<String> objects = new ArrayList<>();
        List<Integer> pageObjectIds = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");

        for (DayReport report : reports) {
            List<String> pages = pages(report);
            for (String content : pages) {
                int contentId = objects.size() + 1;
                objects.add("<< /Length " + content.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + content + "endstream");
                int pageId = objects.size() + 1;
                objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + contentId + " 0 R >>");
                pageObjectIds.add(pageId);
            }
        }
        String kids = pageObjectIds.stream().map(id -> id + " 0 R").reduce("", (a, b) -> a + b + " ").trim();
        objects.set(1, "<< /Type /Pages /Count " + pageObjectIds.size() + " /Kids [" + kids + "] >>");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) {
            write(out, String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(i)));
        }
        write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF");
        return out.toByteArray();
    }

    private static List<String> pages(DayReport report) {
        List<String> pages = new ArrayList<>();
        Page page = new Page();
        page.title("Meine Tagesübersicht", 40, 808);
        page.boldAt("Name: " + lineValue(report.settings().name(), 22), 40, 758, 12);
        page.boldAt("Geb. Datum: " + dateLineValue(report.settings().birthDate(), 18), 318, 758, 12);
        page.boldAt("Datum:", 40, 732, 12);
        page.boldAt(report.date().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), 110, 732, 12);
        page.boldAt("Wochentag:", 318, 732, 12);
        page.boldAt(report.date().format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMANY)), 430, 732, 12);

        int left = 40;
        int top = 690;
        int mealWidth = 126;
        int eatenWidth = 194;
        int drinkWidth = 194;
        int headerHeight = 24;
        int rowHeight = 104;
        int totalHeight = 24;

        page.cell(left, top - headerHeight, mealWidth, headerHeight, "Mahlzeit", 9, true, true);
        page.cell(left + mealWidth, top - headerHeight, eatenWidth, headerHeight, "gegessen wurde", 9, true, true);
        page.cell(left + mealWidth + eatenWidth, top - headerHeight, drinkWidth, headerHeight, "getrunken wurde...", 9, true, true);

        int y = top - headerHeight;
        for (MealReport meal : exportMeals(report.meals()).values()) {
            y -= rowHeight;
            page.cell(left, y, mealWidth, rowHeight, mealMacroBlock(meal), 8, true, false);
            page.cell(left + mealWidth, y, eatenWidth, rowHeight, joinItems(meal, false), 7, false, false);
            page.cell(left + mealWidth + eatenWidth, y, drinkWidth, rowHeight, joinItems(meal, true), 7, false, false);
        }
        y -= totalHeight;
        page.cell(left, y, mealWidth, totalHeight, "Gesamt:", 9, true, true);
        page.cell(left + mealWidth, y, eatenWidth + drinkWidth, totalHeight, report.total().inline(), 9, false, true);

        page.boldAt("Sport:", 40, y - 42, 12);
        page.boldAt("Besonderheiten an diesem Tag:", 40, y - 84, 12);
        page.textAt("F1", report.note() == null ? "" : report.note(), 245, y - 84, 10);
        pages.add(page.finish());
        return pages;
    }

    private static void write(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    static final class Page {
        private final StringBuilder content = new StringBuilder();

        String finish() {
            return content.toString();
        }

        void title(String text, int x, int y) {
            boldAt(text, x, y, 16);
        }

        void boldAt(String text, int x, int y, int size) {
            textAt("F2", text, x, y, size);
        }

        void cell(int x, int y, int width, int height, String text, int size, boolean bold, boolean center) {
            content.append(x).append(' ').append(y).append(' ').append(width).append(' ').append(height).append(" re S\n");
            int maxChars = Math.max(12, width / Math.max(4, size - 1));
            List<String> lines = wrap(text == null ? "" : text, maxChars);
            int textY = center
                    ? y + (height + (lines.size() * (size + 2))) / 2 - size
                    : y + height - size - 5;
            for (String line : lines) {
                if (textY < y + 4) {
                    break;
                }
                textAt(bold ? "F2" : "F1", line, x + 4, textY, size);
                textY -= size + 3;
            }
        }

        private void textAt(String font, String text, int x, int y, int size) {
            content.append("BT /").append(font).append(' ').append(size).append(" Tf ")
                    .append(x).append(' ').append(y).append(" Td (").append(pdf(text)).append(") Tj ET\n");
        }

        private static List<String> wrap(String text, int max) {
            List<String> lines = new ArrayList<>();
            String[] paragraphs = (text == null ? "" : text).split("\\R", -1);
            for (String paragraph : paragraphs) {
                String remaining = paragraph;
                while (remaining.length() > max) {
                    int split = remaining.lastIndexOf(' ', max);
                    if (split < 8) {
                        split = Math.min(max, remaining.length());
                    }
                    lines.add(remaining.substring(0, split));
                    remaining = remaining.substring(split).trim();
                }
                lines.add(remaining);
            }
            return lines;
        }

        private static String pdf(String text) {
            return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        }
    }
}

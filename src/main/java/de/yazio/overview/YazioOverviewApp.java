package de.yazio.overview;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.yazio.overview.export.PdfExport;
import de.yazio.overview.export.XlsxExport;
import de.yazio.overview.json.JsonParser;
import de.yazio.overview.json.JsonWriter;
import de.yazio.overview.model.Domain.*;
import de.yazio.overview.service.ReportService;
import de.yazio.overview.sync.SyncSupport.*;

import static de.yazio.overview.model.Labels.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class YazioOverviewApp {
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    private static final Path DATA_DIR = Path.of(System.getenv().getOrDefault("YAZIO_DATA_DIR", "data"));
    private static final Path PRODUCTS_FILE = DATA_DIR.resolve("products.json");
    private static final Path DAYS_FILE = DATA_DIR.resolve("days.json");
    private static final Path SETTINGS_FILE = DATA_DIR.resolve("settings.json");
    private static final Path NOTES_FILE = DATA_DIR.resolve("notes.json");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".html", "text/html; charset=utf-8",
            ".css", "text/css; charset=utf-8",
            ".js", "text/javascript; charset=utf-8",
            ".json", "application/json; charset=utf-8",
            ".svg", "image/svg+xml"
    );

    private final Object lock = new Object();
    private final SyncState syncState = new SyncState();
    private final ReportService reportService = new ReportService();
    private DataStore store = DataStore.empty();

    public static void main(String[] args) throws Exception {
        Files.createDirectories(DATA_DIR);
        YazioOverviewApp app = new YazioOverviewApp();
        app.reload();

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/api/status", app::status);
        server.createContext("/api/upload", app::upload);
        server.createContext("/api/day", app::day);
        server.createContext("/api/range", app::range);
        server.createContext("/api/settings", app::settings);
        server.createContext("/api/note", app::note);
        server.createContext("/api/sync/status", app::syncStatus);
        server.createContext("/api/sync", app::sync);
        server.createContext("/api/export/xlsx", app::exportXlsx);
        server.createContext("/api/export/pdf", app::exportPdf);
        server.createContext("/", app::staticFile);
        server.start();
        System.out.printf(Locale.ROOT, "Yazio Overview running on http://localhost:%d%n", PORT);
    }

    private void reload() {
        synchronized (lock) {
            try {
                Map<String, Product> products = Files.exists(PRODUCTS_FILE)
                        ? parseProducts(Files.readString(PRODUCTS_FILE))
                        : Map.of();
                Map<LocalDate, Day> days = Files.exists(DAYS_FILE)
                        ? parseDays(Files.readString(DAYS_FILE))
                        : Map.of();
                AppSettings settings = Files.exists(SETTINGS_FILE)
                        ? parseSettings(Files.readString(SETTINGS_FILE))
                        : AppSettings.empty();
                Map<LocalDate, String> notes = Files.exists(NOTES_FILE)
                        ? parseNotes(Files.readString(NOTES_FILE))
                        : Map.of();
                store = new DataStore(products, days, settings, notes);
            } catch (RuntimeException | IOException ex) {
                store = DataStore.empty().withError(ex.getMessage());
            }
        }
    }

    private void status(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        DataStore snapshot = snapshot();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hasProducts", !snapshot.products().isEmpty());
        body.put("hasDays", !snapshot.days().isEmpty());
        body.put("productCount", snapshot.products().size());
        body.put("dayCount", snapshot.days().size());
        body.put("firstDate", snapshot.firstDate().map(LocalDate::toString).orElse(null));
        body.put("lastDate", snapshot.lastDate().map(LocalDate::toString).orElse(null));
        body.put("settings", snapshot.settings().publicMap());
        body.put("error", snapshot.error());
        send(exchange, 200, body);
    }

    private void settings(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equals("GET")) {
            send(exchange, 200, snapshot().settings().publicMap());
            return;
        }
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) new JsonParser(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8)).parse();
        AppSettings existing = snapshot().settings();
        String password = str(body.get("password"));
        AppSettings updated = new AppSettings(
                str(body.get("name")),
                str(body.get("birthDate")),
                str(body.get("username")),
                password == null || password.isBlank() ? existing.passwordBase64() : Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8))
        );
        Files.createDirectories(DATA_DIR);
        Files.writeString(SETTINGS_FILE, JsonWriter.write(updated.toPersistedMap()), StandardCharsets.UTF_8);
        reload();
        send(exchange, 200, snapshot().settings().publicMap());
    }

    private void note(HttpExchange exchange) throws IOException {
        Map<String, String> query = query(exchange);
        LocalDate date = parseDate(query.get("date"));
        if (date == null) {
            send(exchange, 400, Map.of("error", "Bitte ein gültiges Datum angeben."));
            return;
        }
        if (exchange.getRequestMethod().equals("GET")) {
            send(exchange, 200, Map.of("date", date.toString(), "note", snapshot().notes().getOrDefault(date, "")));
            return;
        }
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) new JsonParser(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8)).parse();
        Map<LocalDate, String> notes = new TreeMap<>(snapshot().notes());
        String text = str(body.get("note"));
        if (text == null || text.isBlank()) {
            notes.remove(date);
        } else {
            notes.put(date, text.trim());
        }
        Files.createDirectories(DATA_DIR);
        Files.writeString(NOTES_FILE, JsonWriter.write(notesToMap(notes)), StandardCharsets.UTF_8);
        reload();
        send(exchange, 200, Map.of("date", date.toString(), "note", snapshot().notes().getOrDefault(date, "")));
    }

    private void sync(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) new JsonParser(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8)).parse();
        LocalDate from = parseDate(str(body.get("from")));
        LocalDate to = parseDate(str(body.get("to")));
        if (from == null || to == null || from.isAfter(to)) {
            send(exchange, 400, Map.of("error", "Bitte einen gültigen Zeitraum angeben."));
            return;
        }
        AppSettings settings = snapshot().settings();
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        if (username == null || username.isBlank()) {
            username = settings.username();
        }
        if (password == null || password.isBlank()) {
            password = settings.password();
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            send(exchange, 400, Map.of("error", "Bitte Yazio-Benutzername und Passwort speichern oder mitsenden."));
            return;
        }
        if (!syncState.start(from, to)) {
            send(exchange, 409, Map.of("error", "Es läuft bereits eine Synchronisierung."));
            return;
        }
        String syncUsername = username;
        String syncPassword = password;
        Thread worker = new Thread(() -> runSync(syncUsername, syncPassword, from, to), "yazio-sync");
        worker.setDaemon(true);
        worker.start();
        send(exchange, 202, syncState.toMap());
    }

    private void syncStatus(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        send(exchange, 200, syncState.toMap());
    }

    private void runSync(String username, String password, LocalDate from, LocalDate to) {
        try {
            syncState.log("Starte Yazio-Synchronisierung für " + from + " bis " + to + ".");
            YazioSyncResult result = new YazioClient(syncState::log).sync(username, password, from, to);
            syncState.log("Speichere days.json und products.json lokal.");
            Files.createDirectories(DATA_DIR);
            Files.writeString(DAYS_FILE, JsonWriter.write(result.days()), StandardCharsets.UTF_8);
            Files.writeString(PRODUCTS_FILE, JsonWriter.write(result.products()), StandardCharsets.UTF_8);
            reload();
            syncState.success(result.days().size(), result.products().size());
        } catch (Exception ex) {
            syncState.fail(ex.getMessage());
        }
    }

    private void upload(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            send(exchange, 400, Map.of("error", "Bitte products.json und days.json als Multipart-Upload senden."));
            return;
        }
        String boundary = boundary(contentType);
        if (boundary == null) {
            send(exchange, 400, Map.of("error", "Multipart-Boundary fehlt."));
            return;
        }
        byte[] request = readAll(exchange.getRequestBody());
        Map<String, byte[]> parts = parseMultipart(request, boundary);
        Files.createDirectories(DATA_DIR);
        List<String> updated = new ArrayList<>();
        if (parts.containsKey("products")) {
            validateJson(parts.get("products"), "products.json");
            Files.write(PRODUCTS_FILE, parts.get("products"));
            updated.add("products.json");
        }
        if (parts.containsKey("days")) {
            validateJson(parts.get("days"), "days.json");
            Files.write(DAYS_FILE, parts.get("days"));
            updated.add("days.json");
        }
        reload();
        DataStore snapshot = snapshot();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("updated", updated);
        body.put("status", Map.of(
                "productCount", snapshot.products().size(),
                "dayCount", snapshot.days().size(),
                "firstDate", snapshot.firstDate().map(LocalDate::toString).orElse(null),
                "lastDate", snapshot.lastDate().map(LocalDate::toString).orElse(null)
        ));
        send(exchange, 200, body);
    }

    private void day(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, String> query = query(exchange);
        LocalDate date = parseDate(query.get("date"));
        if (date == null) {
            send(exchange, 400, Map.of("error", "Bitte ein gültiges Datum angeben."));
            return;
        }
        DayReport report = buildDayReport(date);
        if (report == null) {
            send(exchange, 404, Map.of("error", "Kein Eintrag für " + date + " gefunden."));
            return;
        }
        send(exchange, 200, report.toMap());
    }

    private void range(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, String> query = query(exchange);
        LocalDate from = parseDate(query.get("from"));
        LocalDate to = parseDate(query.get("to"));
        if (from == null || to == null || from.isAfter(to)) {
            send(exchange, 400, Map.of("error", "Bitte einen gültigen Datumsbereich angeben."));
            return;
        }
        List<Map<String, Object>> reports = new ArrayList<>();
        DataStore snapshot = snapshot();
        for (LocalDate date : snapshot.days().keySet()) {
            if (!date.isBefore(from) && !date.isAfter(to)) {
                DayReport report = buildDayReport(date);
                if (report != null) {
                    reports.add(report.toMap());
                }
            }
        }
        send(exchange, 200, Map.of("days", reports));
    }

    private void exportXlsx(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        List<DayReport> reports = reportsFor(exchange);
        if (reports.isEmpty()) {
            send(exchange, 404, Map.of("error", "Keine Tage für den Export gefunden."));
            return;
        }
        sendBytes(exchange, 200, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                exportName(reports, "xlsx"), XlsxExport.write(reports));
    }

    private void exportPdf(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        List<DayReport> reports = reportsFor(exchange);
        if (reports.isEmpty()) {
            send(exchange, 404, Map.of("error", "Keine Tage für den Export gefunden."));
            return;
        }
        sendBytes(exchange, 200, "application/pdf", exportName(reports, "pdf"), PdfExport.write(reports));
    }

    private List<DayReport> reportsFor(HttpExchange exchange) {
        Map<String, String> query = query(exchange);
        LocalDate date = parseDate(query.get("date"));
        if (date != null) {
            DayReport report = buildDayReport(date);
            return report == null ? List.of() : List.of(report);
        }
        LocalDate from = parseDate(query.get("from"));
        LocalDate to = parseDate(query.get("to"));
        if (from == null || to == null || from.isAfter(to)) {
            return List.of();
        }
        List<DayReport> reports = new ArrayList<>();
        DataStore snapshot = snapshot();
        for (LocalDate day : snapshot.days().keySet()) {
            if (!day.isBefore(from) && !day.isAfter(to)) {
                DayReport report = buildDayReport(day);
                if (report != null) {
                    reports.add(report);
                }
            }
        }
        return reports;
    }

    private void staticFile(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String rawPath = exchange.getRequestURI().getPath();
        String fileName = rawPath.equals("/") ? "index.html" : rawPath.substring(1);
        if (fileName.contains("..")) {
            send(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }
        Path path = Path.of("static").resolve(fileName);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            send(exchange, 404, Map.of("error", "Not found"));
            return;
        }
        String contentType = CONTENT_TYPES.entrySet().stream()
                .filter(entry -> fileName.endsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("application/octet-stream");
        byte[] bytes = Files.readAllBytes(path);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private DayReport buildDayReport(LocalDate date) {
        return reportService.buildDayReport(date, snapshot());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Product> parseProducts(String json) {
        Object root = new JsonParser(json).parse();
        if (!(root instanceof Map<?, ?> rootMap)) {
            throw new IllegalArgumentException("products.json muss ein JSON-Objekt sein.");
        }
        Map<String, Product> products = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rootMap.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> productMap)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) productMap;
            Map<String, Double> nutrients = numbers((Map<String, Object>) map.get("nutrients"));
            List<Serving> servings = new ArrayList<>();
            Object rawServings = map.get("servings");
            if (rawServings instanceof List<?> list) {
                for (Object rawServing : list) {
                    if (rawServing instanceof Map<?, ?> servingMap) {
                        Map<String, Object> sm = (Map<String, Object>) servingMap;
                        servings.add(new Serving(str(sm.get("serving")), dbl(sm.get("amount"))));
                    }
                }
            }
            String id = String.valueOf(entry.getKey());
            products.put(id, new Product(
                    id,
                    str(map.get("name")),
                    str(map.get("producer")),
                    str(map.get("base_unit")),
                    nutrients,
                    servings
            ));
        }
        return products;
    }

    @SuppressWarnings("unchecked")
    private static Map<LocalDate, Day> parseDays(String json) {
        Object root = new JsonParser(json).parse();
        if (!(root instanceof Map<?, ?> rootMap)) {
            throw new IllegalArgumentException("days.json muss ein JSON-Objekt sein.");
        }
        Map<LocalDate, Day> days = new TreeMap<>();
        for (Map.Entry<?, ?> entry : rootMap.entrySet()) {
            LocalDate date;
            try {
                date = LocalDate.parse(String.valueOf(entry.getKey()));
            } catch (DateTimeParseException ignored) {
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> dayMapRaw)) {
                continue;
            }
            Map<String, Object> dayMap = (Map<String, Object>) dayMapRaw;
            Daily daily = parseDaily((Map<String, Object>) dayMap.get("daily"));
            List<ConsumedProduct> products = new ArrayList<>();
            List<SimpleProduct> simpleProducts = new ArrayList<>();
            Object consumedRaw = dayMap.get("consumed");
            if (consumedRaw instanceof Map<?, ?> consumedMap) {
                Map<String, Object> consumed = (Map<String, Object>) consumedMap;
                Object productList = consumed.get("products");
                if (productList instanceof List<?> list) {
                    for (Object raw : list) {
                        if (raw instanceof Map<?, ?> productMapRaw) {
                            Map<String, Object> productMap = (Map<String, Object>) productMapRaw;
                            products.add(new ConsumedProduct(
                                    str(productMap.get("id")),
                                    str(productMap.get("date")),
                                    str(productMap.get("daytime")),
                                    str(productMap.get("type")),
                                    str(productMap.get("product_id")),
                                    dbl(productMap.get("amount")),
                                    str(productMap.get("serving")),
                                    dbl(productMap.get("serving_quantity"))
                            ));
                        }
                    }
                }
                Object simpleProductList = consumed.get("simple_products");
                if (simpleProductList instanceof List<?> list) {
                    for (Object raw : list) {
                        if (raw instanceof Map<?, ?> simpleMapRaw) {
                            Map<String, Object> simpleMap = (Map<String, Object>) simpleMapRaw;
                            simpleProducts.add(new SimpleProduct(
                                    str(simpleMap.get("id")),
                                    str(simpleMap.get("date")),
                                    str(simpleMap.get("daytime")),
                                    str(simpleMap.get("type")),
                                    str(simpleMap.get("name")),
                                    numbers((Map<String, Object>) simpleMap.get("nutrients")),
                                    bool(simpleMap.get("is_ai_generated"))
                            ));
                        }
                    }
                }
            }
            days.put(date, new Day(date, daily, products, simpleProducts));
        }
        return days;
    }

    private static Daily parseDaily(Map<String, Object> map) {
        if (map == null) {
            return new Daily(0, 0, 0, 0, 0);
        }
        return new Daily(
                dbl(map.get("energy")),
                dbl(map.get("carb")),
                dbl(map.get("protein")),
                dbl(map.get("fat")),
                dbl(map.get("energy_goal"))
        );
    }

    @SuppressWarnings("unchecked")
    private static AppSettings parseSettings(String json) {
        Object root = new JsonParser(json).parse();
        if (!(root instanceof Map<?, ?> raw)) {
            return AppSettings.empty();
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        return new AppSettings(
                str(map.get("name")),
                str(map.get("birthDate")),
                str(map.get("username")),
                str(map.get("passwordBase64"))
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<LocalDate, String> parseNotes(String json) {
        Object root = new JsonParser(json).parse();
        Map<LocalDate, String> notes = new TreeMap<>();
        if (!(root instanceof Map<?, ?> raw)) {
            return notes;
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            LocalDate date = parseDate(entry.getKey());
            String text = str(entry.getValue());
            if (date != null && text != null && !text.isBlank()) {
                notes.put(date, text);
            }
        }
        return notes;
    }

    private static Map<String, Object> notesToMap(Map<LocalDate, String> notes) {
        Map<String, Object> map = new LinkedHashMap<>();
        notes.forEach((date, note) -> map.put(date.toString(), note));
        return map;
    }

    private static Map<String, Double> numbers(Map<String, Object> map) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (map == null) {
            return result;
        }
        map.forEach((key, value) -> result.put(key, dbl(value)));
        return result;
    }

    private static void validateJson(byte[] content, String name) {
        try {
            new JsonParser(new String(content, StandardCharsets.UTF_8)).parse();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(name + " ist kein gültiges JSON: " + ex.getMessage());
        }
    }

    private DataStore snapshot() {
        synchronized (lock) {
            return store;
        }
    }

    private static int mealOrder(String daytime) {
        return switch (normalizeMeal(daytime)) {
            case "breakfast" -> 10;
            case "lunch" -> 20;
            case "dinner" -> 30;
            case "snack" -> 40;
            default -> 99;
        };
    }

    private static String normalizeMeal(String daytime) {
        return daytime == null || daytime.isBlank() ? "other" : daytime;
    }

    private static String mealLabel(String key) {
        return switch (key) {
            case "breakfast" -> "Frühstück";
            case "lunch" -> "Mittagessen";
            case "dinner" -> "Abendessen";
            case "snack" -> "Snack";
            default -> "Sonstiges";
        };
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.equals("null") ? null : text;
    }

    private static double dbl(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static LocalDate parseDate(String raw) {
        try {
            return raw == null ? null : LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> query = new LinkedHashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return query;
        }
        for (String pair : raw.split("&")) {
            int split = pair.indexOf('=');
            if (split > -1) {
                query.put(decode(pair.substring(0, split)), decode(pair.substring(split + 1)));
            }
        }
        return query;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JsonWriter.write(body).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void sendBytes(HttpExchange exchange, int status, String contentType, String fileName, byte[] bytes)
            throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String exportName(List<DayReport> reports, String extension) {
        String first = reports.getFirst().date().toString();
        String last = reports.getLast().date().toString();
        String range = first.equals(last) ? first : first + "_bis_" + last;
        return "yazio_" + range + "." + extension;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        input.transferTo(output);
        return output.toByteArray();
    }

    private static String boundary(String contentType) {
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("boundary=")) {
                return trimmed.substring("boundary=".length()).replace("\"", "");
            }
        }
        return null;
    }

    private static Map<String, byte[]> parseMultipart(byte[] request, String boundary) {
        String body = new String(request, StandardCharsets.ISO_8859_1);
        String marker = "--" + boundary;
        Map<String, byte[]> parts = new LinkedHashMap<>();
        for (String section : body.split(java.util.regex.Pattern.quote(marker))) {
            if (section.isBlank() || section.equals("--\r\n") || section.equals("--")) {
                continue;
            }
            int headerEnd = section.indexOf("\r\n\r\n");
            if (headerEnd < 0) {
                continue;
            }
            String header = section.substring(0, headerEnd);
            String name = null;
            for (String headerLine : header.split("\r\n")) {
                String lower = headerLine.toLowerCase(Locale.ROOT);
                if (lower.startsWith("content-disposition:")) {
                    for (String token : headerLine.split(";")) {
                        String trimmed = token.trim();
                        if (trimmed.startsWith("name=")) {
                            name = trimmed.substring(5).replace("\"", "");
                        }
                    }
                }
            }
            if (name != null) {
                String content = section.substring(headerEnd + 4);
                if (content.endsWith("\r\n")) {
                    content = content.substring(0, content.length() - 2);
                }
                if (content.endsWith("--")) {
                    content = content.substring(0, content.length() - 2);
                }
                parts.put(name, content.getBytes(StandardCharsets.ISO_8859_1));
            }
        }
        return parts;
    }

}


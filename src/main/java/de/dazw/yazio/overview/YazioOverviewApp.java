package de.dazw.yazio.overview;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.dazw.yazio.overview.export.PdfExport;
import de.dazw.yazio.overview.export.XlsxExport;
import de.dazw.yazio.overview.json.JsonParser;
import de.dazw.yazio.overview.json.JsonWriter;
import de.dazw.yazio.overview.model.Domain.*;
import de.dazw.yazio.overview.service.InsightService;
import de.dazw.yazio.overview.service.ReportService;
import de.dazw.yazio.overview.sync.SyncSupport.*;

import static de.dazw.yazio.overview.model.Labels.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.awt.Desktop;
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
import java.time.LocalDateTime;
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
    private static final Path APP_BASE_DIR = appBaseDir();
    private static final Path DATA_DIR = defaultDataDir();
    private static final Path STATIC_DIR = defaultStaticDir();
    private static final Path PRODUCTS_FILE = DATA_DIR.resolve("products.json");
    private static final Path DAYS_FILE = DATA_DIR.resolve("days.json");
    private static final Path SETTINGS_FILE = DATA_DIR.resolve("settings.json");
    private static final Path NOTES_FILE = DATA_DIR.resolve("notes.json");
    private static final Path ITEM_CLASSIFICATIONS_FILE = DATA_DIR.resolve("item-classifications.json");
    private static final Path IMPORTS_DIR = DATA_DIR.resolve("imports");
    private static final int DEFAULT_SYNC_LOOKBACK_DAYS = 14;
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
    private final InsightService insightService = new InsightService();
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
        server.createContext("/api/item-classification", app::itemClassification);
        server.createContext("/api/sync/status", app::syncStatus);
        server.createContext("/api/sync", app::sync);
        server.createContext("/api/insights/products", app::insightProducts);
        server.createContext("/api/insights/product-days", app::insightProductDays);
        server.createContext("/api/insights/days", app::insightDays);
        server.createContext("/api/insights/meals", app::insightMeals);
        server.createContext("/api/insights/weekdays", app::insightWeekdays);
        server.createContext("/api/insights/months", app::insightMonths);
        server.createContext("/api/export/xlsx", app::exportXlsx);
        server.createContext("/api/export/pdf", app::exportPdf);
        server.createContext("/", app::staticFile);
        server.start();
        String url = String.format(Locale.ROOT, "http://localhost:%d", PORT);
        System.out.printf(Locale.ROOT, "Yazio Overview running on %s%n", url);
        openBrowserIfRequested(url);
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
                Map<String, String> itemClassifications = Files.exists(ITEM_CLASSIFICATIONS_FILE)
                        ? parseItemClassifications(Files.readString(ITEM_CLASSIFICATIONS_FILE))
                        : Map.of();
                store = new DataStore(products, days, settings, notes, itemClassifications);
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
        SyncRecommendation recommendation = syncRecommendation();
        body.put("recommendedSyncFrom", recommendation.from().toString());
        body.put("recommendedSyncTo", recommendation.to().toString());
        body.put("oldestIncompleteDay", recommendation.oldestIncompleteDay().map(LocalDate::toString).orElse(null));
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

    private void itemClassification(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) new JsonParser(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8)).parse();
        String itemId = str(body.get("itemId"));
        String requested = str(body.get("classification"));
        boolean learnProduct = bool(body.get("learnProduct"));
        if (itemId == null || itemId.isBlank()) {
            send(exchange, 400, Map.of("error", "Bitte einen Eintrag auswählen."));
            return;
        }

        Map<String, FoodItem> currentItems = currentItemsById();
        FoodItem item = currentItems.get(itemId);
        if (item == null) {
            send(exchange, 404, Map.of("error", "Der Eintrag existiert im aktuellen Datenbestand nicht mehr."));
            return;
        }

        Map<String, String> overrides = new TreeMap<>(snapshot().itemClassifications());
        cleanupClassificationOverrides(overrides, currentItems);
        String automatic = item.automaticDrink() ? "drink" : "food";
        String productKey = classificationProductKey(item.productId());
        boolean canLearnProduct = productKey != null && snapshot().products().containsKey(item.productId());
        if (requested == null || requested.isBlank() || requested.equals("auto")) {
            overrides.remove(itemId);
            requested = automatic;
        } else if (requested.equals("food") || requested.equals("drink")) {
            if (learnProduct && canLearnProduct) {
                if (requested.equals(automatic)) {
                    overrides.remove(productKey);
                } else {
                    overrides.put(productKey, requested);
                }
                overrides.remove(itemId);
            } else {
                String productOverride = productKey == null ? null : overrides.get(productKey);
                if (requested.equals(automatic) && productOverride == null) {
                    overrides.remove(itemId);
                } else if (requested.equals(productOverride)) {
                    overrides.remove(itemId);
                } else {
                    overrides.put(itemId, requested);
                }
            }
        } else {
            send(exchange, 400, Map.of("error", "Bitte gegessen oder getrunken auswählen."));
            return;
        }

        Files.createDirectories(DATA_DIR);
        Files.writeString(ITEM_CLASSIFICATIONS_FILE, JsonWriter.write(new LinkedHashMap<>(overrides)), StandardCharsets.UTF_8);
        reload();
        send(exchange, 200, Map.of(
                "itemId", itemId,
                "classification", requested,
                "automaticClassification", automatic,
                "classificationOverridden", !requested.equals(automatic),
                "learnedProduct", learnProduct && canLearnProduct
        ));
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
            syncState.log("Speichere Import-Snapshot lokal.");
            Files.createDirectories(DATA_DIR);
            Map<String, Object> currentDays = readJsonObject(DAYS_FILE);
            Map<String, Object> currentProducts = readJsonObject(PRODUCTS_FILE);
            writeImportSnapshot(result, from, to);
            syncState.log("Konsolidiere Import-Historie.");
            ConsolidatedImport consolidated = consolidateImports(currentDays, currentProducts);
            Files.writeString(DAYS_FILE, JsonWriter.write(consolidated.days()), StandardCharsets.UTF_8);
            Files.writeString(PRODUCTS_FILE, JsonWriter.write(consolidated.products()), StandardCharsets.UTF_8);
            reload();
            syncState.success(consolidated.days().size(), consolidated.products().size());
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

    private void insightProducts(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, String> query = query(exchange);
        send(exchange, 200, Map.of("items", insightService.products(
                allReports(),
                query.getOrDefault("query", ""),
                query.getOrDefault("sort", "amount"),
                intQuery(query.get("limit"), 100, 1, 500)
        )));
    }

    private void insightProductDays(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, String> query = query(exchange);
        String key = query.get("key");
        if (key == null || key.isBlank()) {
            send(exchange, 400, Map.of("error", "Bitte ein Lebensmittel auswählen."));
            return;
        }
        send(exchange, 200, Map.of("days", insightService.productDays(
                allReports(),
                key,
                query.getOrDefault("sort", "amount")
        )));
    }

    private void insightDays(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, String> query = query(exchange);
        send(exchange, 200, Map.of("days", insightService.days(
                allReports(),
                query.getOrDefault("sort", "energy"),
                query.getOrDefault("dir", "desc")
        )));
    }

    private void insightMeals(HttpExchange exchange) throws IOException {
        sendInsightMacroList(exchange, "meals");
    }

    private void insightWeekdays(HttpExchange exchange) throws IOException {
        sendInsightMacroList(exchange, "weekdays");
    }

    private void insightMonths(HttpExchange exchange) throws IOException {
        sendInsightMacroList(exchange, "months");
    }

    private void sendInsightMacroList(HttpExchange exchange, String type) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, String> query = query(exchange);
        String sort = query.getOrDefault("sort", "energy");
        List<Map<String, Object>> items = switch (type) {
            case "weekdays" -> insightService.weekdays(allReports(), sort);
            case "months" -> insightService.months(allReports(), sort);
            default -> insightService.meals(allReports(), sort);
        };
        send(exchange, 200, Map.of("items", items));
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
        Path path = STATIC_DIR.resolve(fileName);
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

    private List<DayReport> allReports() {
        DataStore snapshot = snapshot();
        List<DayReport> reports = new ArrayList<>();
        for (LocalDate date : snapshot.days().keySet()) {
            DayReport report = reportService.buildDayReport(date, snapshot);
            if (report != null) {
                reports.add(report);
            }
        }
        return reports;
    }

    private Map<String, FoodItem> currentItemsById() {
        Map<String, FoodItem> items = new LinkedHashMap<>();
        for (DayReport report : allReports()) {
            for (MealReport meal : report.meals()) {
                for (FoodItem item : meal.items()) {
                    if (item.itemId() != null && !item.itemId().isBlank()) {
                        items.put(item.itemId(), item);
                    }
                }
            }
        }
        return items;
    }

    private void cleanupClassificationOverrides(Map<String, String> overrides, Map<String, FoodItem> currentItems) {
        Set<String> productKeys = new LinkedHashSet<>();
        snapshot().products().keySet().forEach(productId -> productKeys.add(classificationProductKey(productId)));
        overrides.keySet().removeIf(key -> key.startsWith("product:")
                ? !productKeys.contains(key)
                : !currentItems.containsKey(key));
    }

    private static String classificationProductKey(String productId) {
        return productId == null || productId.isBlank() ? null : "product:" + productId;
    }

    private SyncRecommendation syncRecommendation() {
        LocalDate today = LocalDate.now();
        LocalDate regularStart = today.minusDays(DEFAULT_SYNC_LOOKBACK_DAYS);
        Optional<LocalDate> oldestIncomplete = oldestIncompleteImportDay();
        LocalDate recommendedFrom = oldestIncomplete
                .map(day -> day.isBefore(regularStart) ? day : regularStart)
                .orElse(regularStart);
        return new SyncRecommendation(recommendedFrom, today, oldestIncomplete);
    }

    private Optional<LocalDate> oldestIncompleteImportDay() {
        Map<LocalDate, Boolean> completeness = importCompleteness();
        return completeness.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .min(Comparator.naturalOrder());
    }

    /**
     * Ermittelt pro Tag, ob der beste bekannte Import vollständig war.
     * Vollständige Imports schlagen unvollständige; bei gleicher Qualität gewinnt
     * der später eingelesene Snapshot.
     */
    @SuppressWarnings("unchecked")
    private Map<LocalDate, Boolean> importCompleteness() {
        Map<LocalDate, Boolean> completeness = new TreeMap<>();
        for (Path metadata : importMetadataFiles()) {
            Map<String, Object> map = readJsonObject(metadata);
            markCompleteness(completeness, (List<Object>) map.get("incompleteDays"), false);
            markCompleteness(completeness, (List<Object>) map.get("completeDays"), true);
        }
        return completeness;
    }

    private static void markCompleteness(Map<LocalDate, Boolean> completeness, List<Object> days, boolean complete) {
        if (days == null) {
            return;
        }
        for (Object raw : days) {
            LocalDate date = parseDate(str(raw));
            if (date == null) {
                continue;
            }
            Boolean existing = completeness.get(date);
            if (complete || existing == null || !existing) {
                completeness.put(date, complete);
            }
        }
    }

    private void writeImportSnapshot(YazioSyncResult result, LocalDate from, LocalDate to) throws IOException {
        LocalDateTime importedAt = LocalDateTime.now();
        String folderName = importedAt.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path importDir = IMPORTS_DIR.resolve(folderName);
        Files.createDirectories(importDir);
        Files.writeString(importDir.resolve("days.json"), JsonWriter.write(result.days()), StandardCharsets.UTF_8);
        Files.writeString(importDir.resolve("products.json"), JsonWriter.write(result.products()), StandardCharsets.UTF_8);
        Files.writeString(importDir.resolve("metadata.json"),
                JsonWriter.write(importMetadata(result, from, to, importedAt)), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> importMetadata(YazioSyncResult result, LocalDate from, LocalDate to,
                                                      LocalDateTime importedAt) {
        LocalDate importDate = importedAt.toLocalDate();
        List<String> completeDays = new ArrayList<>();
        List<String> incompleteDays = new ArrayList<>();
        for (String rawDate : result.days().keySet()) {
            LocalDate date = parseDate(rawDate);
            if (date == null) {
                continue;
            }
            if (date.isBefore(importDate)) {
                completeDays.add(date.toString());
            } else {
                incompleteDays.add(date.toString());
            }
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("importedAt", importedAt.toString());
        metadata.put("from", from.toString());
        metadata.put("to", to.toString());
        metadata.put("source", "yazio-api");
        metadata.put("dayCount", result.days().size());
        metadata.put("productCount", result.products().size());
        metadata.put("completeDays", completeDays);
        metadata.put("incompleteDays", incompleteDays);
        return metadata;
    }

    private ConsolidatedImport consolidateImports(Map<String, Object> baseDays, Map<String, Object> baseProducts) {
        Map<String, Object> days = new TreeMap<>(baseDays);
        Map<String, Object> products = new LinkedHashMap<>(baseProducts);
        Map<String, Boolean> dayComplete = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (String rawDate : days.keySet()) {
            LocalDate date = parseDate(rawDate);
            dayComplete.put(rawDate, date == null || date.isBefore(today));
        }

        for (Path metadata : importMetadataFiles()) {
            Path importDir = metadata.getParent();
            Map<String, Object> importedProducts = readJsonObject(importDir.resolve("products.json"));
            products.putAll(importedProducts);
            Map<String, Object> importedDays = readJsonObject(importDir.resolve("days.json"));
            Map<LocalDate, Boolean> completeness = importCompletenessFor(metadata);
            for (Map.Entry<String, Object> entry : importedDays.entrySet()) {
                LocalDate date = parseDate(entry.getKey());
                boolean complete = date != null && completeness.getOrDefault(date, date.isBefore(today));
                boolean existingComplete = dayComplete.getOrDefault(entry.getKey(), false);
                if (complete || !existingComplete) {
                    days.put(entry.getKey(), entry.getValue());
                    dayComplete.put(entry.getKey(), complete);
                }
            }
        }
        return new ConsolidatedImport(days, products);
    }

    @SuppressWarnings("unchecked")
    private Map<LocalDate, Boolean> importCompletenessFor(Path metadataFile) {
        Map<String, Object> metadata = readJsonObject(metadataFile);
        Map<LocalDate, Boolean> completeness = new TreeMap<>();
        markCompleteness(completeness, (List<Object>) metadata.get("incompleteDays"), false);
        markCompleteness(completeness, (List<Object>) metadata.get("completeDays"), true);
        return completeness;
    }

    private List<Path> importMetadataFiles() {
        if (!Files.isDirectory(IMPORTS_DIR)) {
            return List.of();
        }
        try (var stream = Files.list(IMPORTS_DIR)) {
            return stream
                    .map(path -> path.resolve("metadata.json"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJsonObject(Path path) {
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        try {
            Object root = new JsonParser(Files.readString(path)).parse();
            if (root instanceof Map<?, ?> raw) {
                return new LinkedHashMap<>((Map<String, Object>) raw);
            }
        } catch (RuntimeException | IOException ignored) {
            return Map.of();
        }
        return Map.of();
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

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseItemClassifications(String json) {
        Object root = new JsonParser(json).parse();
        Map<String, String> result = new TreeMap<>();
        if (!(root instanceof Map<?, ?> raw)) {
            return result;
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String itemId = entry.getKey();
            String classification = str(entry.getValue());
            if (itemId != null && !itemId.isBlank() && ("food".equals(classification) || "drink".equals(classification))) {
                result.put(itemId, classification);
            }
        }
        return result;
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

    private static int intQuery(String raw, int fallback, int min, int max) {
        try {
            int value = raw == null ? fallback : Integer.parseInt(raw);
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ex) {
            return fallback;
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
        DayReport firstReport = reports.getFirst();
        DayReport lastReport = reports.getLast();
        String first = firstReport.date().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String last = lastReport.date().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String prefix = fileNamePart(firstReport.settings().name());
        if (prefix.isBlank()) {
            prefix = "Yazio";
        }
        String range = first.equals(last) ? first : first + "-" + last;
        return prefix + "_Yazio-Export_" + range + "." + extension;
    }

    private static String fileNamePart(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        input.transferTo(output);
        return output.toByteArray();
    }

    private static Path defaultDataDir() {
        String configured = System.getenv("YAZIO_DATA_DIR");
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty("yazio.data.dir");
        }
        return configured == null || configured.isBlank() ? APP_BASE_DIR.resolve("data") : Path.of(configured);
    }

    private static Path defaultStaticDir() {
        String configured = System.getenv("YAZIO_STATIC_DIR");
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty("yazio.static.dir");
        }
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        Path packaged = APP_BASE_DIR.resolve("static");
        return Files.exists(packaged) ? packaged : Path.of("static");
    }

    private static Path appBaseDir() {
        String classPath = System.getProperty("java.class.path", "");
        if (!classPath.isBlank()) {
            Path firstEntry = Path.of(classPath.split(File.pathSeparator)[0]).toAbsolutePath().normalize();
            Path container = Files.isDirectory(firstEntry) ? firstEntry : firstEntry.getParent();
            if (container != null) {
                Path fileName = container.getFileName();
                if (fileName != null && (fileName.toString().equals("app") || fileName.toString().equals("out"))) {
                    Path parent = container.getParent();
                    if (parent != null) {
                        return parent;
                    }
                }
                return container;
            }
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    private static void openBrowserIfRequested(String url) {
        String value = System.getProperty("yazio.openBrowser", System.getenv().getOrDefault("YAZIO_OPEN_BROWSER", "false"));
        if (!Boolean.parseBoolean(value) || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (RuntimeException | IOException ex) {
            System.err.println("Browser konnte nicht automatisch geoeffnet werden: " + ex.getMessage());
        }
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

    private record SyncRecommendation(LocalDate from, LocalDate to, Optional<LocalDate> oldestIncompleteDay) {
    }

    private record ConsolidatedImport(Map<String, Object> days, Map<String, Object> products) {
    }

}


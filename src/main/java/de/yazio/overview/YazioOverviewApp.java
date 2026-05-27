package de.yazio.overview;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

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
        DataStore snapshot = snapshot();
        Day day = snapshot.days().get(date);
        if (day == null) {
            return null;
        }
        Map<String, MealReport> meals = new LinkedHashMap<>();
        List<ConsumedProduct> entries = new ArrayList<>(day.products());
        entries.sort(Comparator.comparingInt((ConsumedProduct item) -> mealOrder(item.daytime()))
                .thenComparing(ConsumedProduct::date, Comparator.nullsLast(String::compareTo)));

        for (ConsumedProduct entry : entries) {
            Product product = snapshot.products().get(entry.productId());
            Macro macro = macroFor(product, entry.amount());
            String mealKey = normalizeMeal(entry.daytime());
            MealReport meal = meals.computeIfAbsent(mealKey, MealReport::new);
            meal.items().add(new FoodItem(
                    product == null ? "(Unbekanntes Produkt)" : product.name(),
                    product == null ? null : product.producer(),
                    entry.amount(),
                    product == null ? null : product.baseUnit(),
                    entry.serving(),
                    entry.servingQuantity(),
                    entry.productId(),
                    amountLabel(entry.amount(), product == null ? null : product.baseUnit(), entry.serving(), entry.servingQuantity()),
                    false,
                    macro
            ));
            meal.total().add(macro);
        }
        List<SimpleProduct> simpleProducts = new ArrayList<>(day.simpleProducts());
        simpleProducts.sort(Comparator.comparingInt((SimpleProduct item) -> mealOrder(item.daytime()))
                .thenComparing(SimpleProduct::date, Comparator.nullsLast(String::compareTo)));
        for (SimpleProduct entry : simpleProducts) {
            Macro macro = macroFor(entry.nutrients());
            String mealKey = normalizeMeal(entry.daytime());
            MealReport meal = meals.computeIfAbsent(mealKey, MealReport::new);
            meal.items().add(new FoodItem(
                    entry.name(),
                    entry.aiGenerated() ? "KI erfasst" : null,
                    0,
                    null,
                    "simple_product",
                    1,
                    entry.id(),
                    entry.aiGenerated() ? "KI erfasste Mahlzeit" : "Einfache Mahlzeit",
                    entry.aiGenerated(),
                    macro
            ));
            meal.total().add(macro);
        }

        Macro total = new Macro();
        meals.values().forEach(meal -> total.add(meal.total()));
        List<MealReport> mealReports = new ArrayList<>(meals.values());
        mealReports.sort(Comparator.comparingInt(meal -> mealOrder(meal.key())));
        return new DayReport(date, day.daily(), mealReports, total, snapshot.settings(), snapshot.notes().getOrDefault(date, ""));
    }

    private static Macro macroFor(Product product, double amount) {
        Macro macro = new Macro();
        if (product == null) {
            return macro;
        }
        Map<String, Double> nutrients = product.nutrients();
        macro.energy = nutrients.getOrDefault("energy.energy", 0.0) * amount;
        macro.carbs = nutrients.getOrDefault("nutrient.carb", 0.0) * amount;
        macro.protein = nutrients.getOrDefault("nutrient.protein", 0.0) * amount;
        macro.fat = nutrients.getOrDefault("nutrient.fat", 0.0) * amount;
        macro.sugar = nutrients.getOrDefault("nutrient.sugar", 0.0) * amount;
        macro.fiber = nutrients.getOrDefault("nutrient.dietaryfiber", 0.0) * amount;
        return macro;
    }

    private static Macro macroFor(Map<String, Double> nutrients) {
        Macro macro = new Macro();
        macro.energy = nutrients.getOrDefault("energy.energy", 0.0);
        macro.carbs = nutrients.getOrDefault("nutrient.carb", 0.0);
        macro.protein = nutrients.getOrDefault("nutrient.protein", 0.0);
        macro.fat = nutrients.getOrDefault("nutrient.fat", 0.0);
        macro.sugar = nutrients.getOrDefault("nutrient.sugar", 0.0);
        macro.fiber = nutrients.getOrDefault("nutrient.dietaryfiber", 0.0);
        return macro;
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

    record DataStore(Map<String, Product> products, Map<LocalDate, Day> days, AppSettings settings,
                     Map<LocalDate, String> notes, String error) {
        static DataStore empty() {
            return new DataStore(Map.of(), Map.of(), AppSettings.empty(), Map.of(), null);
        }

        DataStore(Map<String, Product> products, Map<LocalDate, Day> days, AppSettings settings,
                  Map<LocalDate, String> notes) {
            this(products, days, settings, notes, null);
        }

        DataStore withError(String error) {
            return new DataStore(products, days, settings, notes, error);
        }

        Optional<LocalDate> firstDate() {
            return days.keySet().stream().min(LocalDate::compareTo);
        }

        Optional<LocalDate> lastDate() {
            return days.keySet().stream().max(LocalDate::compareTo);
        }
    }

    record AppSettings(String name, String birthDate, String username, String passwordBase64) {
        static AppSettings empty() {
            return new AppSettings("", "", "", "");
        }

        String password() {
            if (passwordBase64 == null || passwordBase64.isBlank()) {
                return "";
            }
            try {
                return new String(Base64.getDecoder().decode(passwordBase64), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                return "";
            }
        }

        Map<String, Object> publicMap() {
            return Map.of(
                    "name", name == null ? "" : name,
                    "birthDate", birthDate == null ? "" : birthDate,
                    "username", username == null ? "" : username,
                    "hasPassword", passwordBase64 != null && !passwordBase64.isBlank()
            );
        }

        Map<String, Object> toPersistedMap() {
            return Map.of(
                    "name", name == null ? "" : name,
                    "birthDate", birthDate == null ? "" : birthDate,
                    "username", username == null ? "" : username,
                    "passwordBase64", passwordBase64 == null ? "" : passwordBase64
            );
        }
    }

    record Product(String id, String name, String producer, String baseUnit, Map<String, Double> nutrients,
                   List<Serving> servings) {
    }

    record Serving(String serving, double amount) {
    }

    record Day(LocalDate date, Daily daily, List<ConsumedProduct> products, List<SimpleProduct> simpleProducts) {
    }

    record Daily(double energy, double carbs, double protein, double fat, double energyGoal) {
        Map<String, Object> toMap() {
            return Map.of(
                    "energy", round(energy),
                    "carbs", round(carbs),
                    "protein", round(protein),
                    "fat", round(fat),
                    "energyGoal", round(energyGoal)
            );
        }
    }

    record ConsumedProduct(String id, String date, String daytime, String type, String productId, double amount,
                           String serving, double servingQuantity) {
    }

    record SimpleProduct(String id, String date, String daytime, String type, String name, Map<String, Double> nutrients,
                         boolean aiGenerated) {
    }

    record DayReport(LocalDate date, Daily daily, List<MealReport> meals, Macro total, AppSettings settings, String note) {
        Map<String, Object> toMap() {
            List<Map<String, Object>> mealMaps = meals.stream().map(MealReport::toMap).toList();
            return Map.of(
                    "date", date.toString(),
                    "daily", daily.toMap(),
                    "total", total.toMap(),
                    "meals", mealMaps,
                    "note", note == null ? "" : note,
                    "copyText", copyText()
            );
        }

        String copyText() {
            StringBuilder text = new StringBuilder();
            text.append(date).append('\n');
            text.append("Gesamt: ").append(total.inline()).append('\n');
            for (MealReport meal : meals) {
                text.append('\n').append(mealLabel(meal.key())).append(": ").append(meal.total().inline()).append('\n');
                for (FoodItem item : meal.items()) {
                    text.append("- ")
                            .append(item.name())
                            .append(" (")
                    .append(item.amountLabel())
                    .append(": ")
                            .append(item.macro().inline())
                            .append('\n');
                }
            }
            return text.toString().trim();
        }
    }

    record MealReport(String key, List<FoodItem> items, Macro total) {
        MealReport(String key) {
            this(key, new ArrayList<>(), new Macro());
        }

        Map<String, Object> toMap() {
            return Map.of(
                    "key", key,
                    "label", mealLabel(key),
                    "total", total.toMap(),
                    "items", items.stream().map(FoodItem::toMap).toList(),
                    "copyText", copyText()
            );
        }

        String copyText() {
            StringBuilder text = new StringBuilder();
            text.append(mealLabel(key)).append(": ").append(total.inline()).append('\n');
            for (FoodItem item : items) {
                text.append("- ")
                        .append(item.name())
                        .append(" (")
                        .append(item.amountLabel())
                        .append(": ")
                        .append(item.macro().inline())
                        .append('\n');
            }
            return text.toString().trim();
        }
    }

    record FoodItem(String name, String producer, double amount, String baseUnit, String serving, double servingQuantity,
                    String productId, String amountLabel, boolean aiGenerated, Macro macro) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("producer", producer);
            map.put("amount", round(amount));
            map.put("baseUnit", baseUnit);
            map.put("serving", serving);
            map.put("servingQuantity", round(servingQuantity));
            map.put("productId", productId);
            map.put("amountLabel", amountLabel);
            map.put("aiGenerated", aiGenerated);
            map.put("macro", macro.toMap());
            return map;
        }
    }

    static final class Macro {
        double energy;
        double carbs;
        double protein;
        double fat;
        double sugar;
        double fiber;

        void add(Macro other) {
            energy += other.energy;
            carbs += other.carbs;
            protein += other.protein;
            fat += other.fat;
            sugar += other.sugar;
            fiber += other.fiber;
        }

        Map<String, Object> toMap() {
            return Map.of(
                    "energy", round(energy),
                    "carbs", round(carbs),
                    "protein", round(protein),
                    "fat", round(fat),
                    "sugar", round(sugar),
                    "fiber", round(fiber)
            );
        }

        String inline() {
            return format(energy) + " kcal, KH " + format(carbs) + " g, Protein " + format(protein)
                    + " g, Fett " + format(fat) + " g";
        }
    }

    private static String format(double value) {
        double rounded = round(value);
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001) {
            return String.valueOf((long) rounded);
        }
        return String.format(Locale.GERMANY, "%.1f", rounded);
    }

    private static String amountLabel(double amount, String baseUnit, String serving, double servingQuantity) {
        String label = format(amount) + (baseUnit == null ? "" : " " + baseUnit);
        if (serving != null && !serving.isBlank()) {
            label += " (" + format(servingQuantity) + " x " + serving + ")";
        }
        return label;
    }

    private static boolean isDrink(FoodItem item) {
        String unit = item.baseUnit() == null ? "" : item.baseUnit().toLowerCase(Locale.ROOT);
        String serving = item.serving() == null ? "" : item.serving().toLowerCase(Locale.ROOT);
        String name = item.name() == null ? "" : item.name().toLowerCase(Locale.ROOT);
        return unit.equals("ml")
                || serving.contains("bottle")
                || serving.contains("can")
                || serving.contains("cup")
                || serving.contains("glass")
                || serving.contains("drink")
                || name.contains("drink")
                || name.contains("wasser")
                || name.contains("cola")
                || name.contains("red bull")
                || name.contains("coffee")
                || name.contains("kaffee");
    }

    private static String itemLine(FoodItem item) {
        return item.name() + " (" + item.amountLabel() + ")";
    }

    private static String joinItems(MealReport meal, boolean drinks) {
        List<String> lines = meal.items().stream()
                .filter(item -> isDrink(item) == drinks)
                .map(YazioOverviewApp::itemLine)
                .toList();
        return String.join("\n", lines);
    }

    private static String mealExportLabel(String mealKey) {
        return switch (mealKey) {
            case "breakfast" -> "Frühstück";
            case "lunch" -> "Mittagessen";
            case "dinner" -> "Abendessen";
            default -> "Sonstiges";
        };
    }

    private static String mealMacroBlock(MealReport meal) {
        Macro total = meal.total();
        return mealExportLabel(meal.key()) + "\n"
                + format(total.energy) + " kcal\n"
                + "KH " + format(total.carbs) + " g\n"
                + "Protein " + format(total.protein) + " g\n"
                + "Fett " + format(total.fat) + " g";
    }

    private static String lineValue(String value, int underscores) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return "_".repeat(Math.max(4, underscores));
    }

    private static String dateLineValue(String value, int underscores) {
        if (value != null && !value.isBlank()) {
            LocalDate date = parseDate(value);
            return date == null ? value : date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        }
        return "_".repeat(Math.max(4, underscores));
    }

    private static Map<String, MealReport> exportMeals(List<MealReport> meals) {
        Map<String, MealReport> grouped = new LinkedHashMap<>();
        grouped.put("breakfast", new MealReport("breakfast"));
        grouped.put("lunch", new MealReport("lunch"));
        grouped.put("dinner", new MealReport("dinner"));
        grouped.put("other", new MealReport("other"));
        for (MealReport meal : meals) {
            String key = switch (meal.key()) {
                case "breakfast", "lunch", "dinner" -> meal.key();
                default -> "other";
            };
            MealReport target = grouped.get(key);
            target.items().addAll(meal.items());
            target.total().add(meal.total());
        }
        return grouped;
    }

    static final class JsonWriter {
        static String write(Object value) {
            StringBuilder out = new StringBuilder();
            append(out, value);
            return out.toString();
        }

        private static void append(StringBuilder out, Object value) {
            if (value == null) {
                out.append("null");
            } else if (value instanceof String text) {
                string(out, text);
            } else if (value instanceof Number || value instanceof Boolean) {
                out.append(value);
            } else if (value instanceof Map<?, ?> map) {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    string(out, String.valueOf(entry.getKey()));
                    out.append(':');
                    append(out, entry.getValue());
                    first = false;
                }
                out.append('}');
            } else if (value instanceof Iterable<?> iterable) {
                out.append('[');
                boolean first = true;
                for (Object item : iterable) {
                    if (!first) {
                        out.append(',');
                    }
                    append(out, item);
                    first = false;
                }
                out.append(']');
            } else {
                string(out, String.valueOf(value));
            }
        }

        private static void string(StringBuilder out, String value) {
            out.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 32) {
                            out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            out.append('"');
        }
    }

    static final class XlsxExport {
        private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMANY);

        static byte[] write(List<DayReport> reports) throws IOException {
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
                    .map(YazioOverviewApp::itemLine)
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

    static final class PdfExport {
        private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMANY);

        static byte[] write(List<DayReport> reports) throws IOException {
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

    record YazioSyncResult(Map<String, Object> days, Map<String, Object> products) {
    }

    static final class SyncState {
        private boolean running;
        private String status = "idle";
        private String error = "";
        private LocalDate from;
        private LocalDate to;
        private int dayCount;
        private int productCount;
        private final List<String> logs = new ArrayList<>();

        synchronized boolean start(LocalDate from, LocalDate to) {
            if (running) {
                return false;
            }
            this.running = true;
            this.status = "running";
            this.error = "";
            this.from = from;
            this.to = to;
            this.dayCount = 0;
            this.productCount = 0;
            this.logs.clear();
            log("Sync initialisiert.");
            return true;
        }

        synchronized void log(String message) {
            String line = DateTimeFormatter.ofPattern("HH:mm:ss").format(java.time.LocalTime.now()) + "  " + message;
            logs.add(line);
            if (logs.size() > 500) {
                logs.remove(0);
            }
        }

        synchronized void success(int dayCount, int productCount) {
            this.dayCount = dayCount;
            this.productCount = productCount;
            this.running = false;
            this.status = "success";
            log("Fertig: " + dayCount + " Tage, " + productCount + " Produkte synchronisiert.");
        }

        synchronized void fail(String message) {
            this.running = false;
            this.status = "error";
            this.error = message == null ? "Unbekannter Fehler" : message;
            log("Fehler: " + this.error);
        }

        synchronized Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("running", running);
            map.put("status", status);
            map.put("error", error);
            map.put("from", from == null ? null : from.toString());
            map.put("to", to == null ? null : to.toString());
            map.put("dayCount", dayCount);
            map.put("productCount", productCount);
            map.put("logs", new ArrayList<>(logs));
            return map;
        }
    }

    static final class YazioClient {
        private static final String BASE = "https://yzapi.yazio.com";
        private static final String CLIENT_ID = "1_4hiybetvfksgw40o0sog4s884kwc840wwso8go4k8c04goo4c";
        private static final String CLIENT_SECRET = "6rok2m65xuskgkgogw40wkkk8sw0osg84s8cggsc4woos4s8o";

        private final HttpClient http = HttpClient.newHttpClient();
        private final Consumer<String> log;
        private String token;

        YazioClient(Consumer<String> log) {
            this.log = log == null ? ignored -> {
            } : log;
        }

        YazioSyncResult sync(String username, String password, LocalDate from, LocalDate to) throws Exception {
            log.accept("Melde bei Yazio an.");
            token = login(username, password);
            log.accept("Login erfolgreich.");
            Map<LocalDate, Map<String, Object>> daily = loadDailySummaries(from, to);
            log.accept("Tagesübersichten geladen: " + daily.size() + ".");
            Set<LocalDate> dates = new LinkedHashSet<>(daily.keySet());
            if (dates.isEmpty()) {
                for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                    dates.add(date);
                }
            }

            Map<String, Object> days = new TreeMap<>();
            int loadedDays = 0;
            for (LocalDate date : dates) {
                if (date.isBefore(from) || date.isAfter(to)) {
                    continue;
                }
                log.accept("Lade Tagesdetails für " + date + ".");
                Map<String, Object> consumed = objectOrEmpty(get("/v9/user/consumed-items?date=" + date));
                Map<String, Object> goals = objectOrEmpty(get("/v9/user/goals?date=" + date));
                Map<String, Object> exercises = objectOrEmpty(get("/v9/user/exercises?date=" + date));
                Map<String, Object> water = objectOrEmpty(get("/v9/user/water-intake?date=" + date));
                Map<String, Object> day = new LinkedHashMap<>();
                day.put("daily", daily.getOrDefault(date, fallbackDaily(date, consumed)));
                day.put("consumed", consumed);
                day.put("goals", goals);
                day.put("exercises", exercises);
                day.put("water", water);
                if (daily.containsKey(date) || hasConsumedEntries(consumed)) {
                    days.put(date.toString(), day);
                    loadedDays++;
                }
            }
            log.accept("Tagesdetails übernommen: " + loadedDays + ".");

            Set<String> productIds = new LinkedHashSet<>();
            collectProductIds(days, productIds);
            log.accept("Produktdetails zu laden: " + productIds.size() + ".");
            Map<String, Object> products = new LinkedHashMap<>();
            int loadedProducts = 0;
            for (String productId : productIds) {
                products.put(productId, objectOrEmpty(get("/v9/products/" + encode(productId))));
                loadedProducts++;
                if (loadedProducts == productIds.size() || loadedProducts % 25 == 0) {
                    log.accept("Produkte geladen: " + loadedProducts + "/" + productIds.size() + ".");
                }
            }
            return new YazioSyncResult(days, products);
        }

        private String login(String username, String password) throws Exception {
            String body = "{"
                    + "\"client_id\":\"" + CLIENT_ID + "\","
                    + "\"client_secret\":\"" + CLIENT_SECRET + "\","
                    + "\"username\":\"" + jsonEscape(username) + "\","
                    + "\"password\":\"" + jsonEscape(password) + "\","
                    + "\"grant_type\":\"password\""
                    + "}";
            String response = request("POST", "/v9/oauth/token", body);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = (Map<String, Object>) new JsonParser(response).parse();
            String accessToken = str(parsed.get("access_token"));
            if (accessToken == null || accessToken.isBlank()) {
                throw new IOException("Login-Antwort enthält kein access_token.");
            }
            return accessToken;
        }

        private Map<LocalDate, Map<String, Object>> loadDailySummaries(LocalDate from, LocalDate to) throws Exception {
            Map<LocalDate, Map<String, Object>> result = new TreeMap<>();
            LocalDate cursor = from.withDayOfMonth(1);
            LocalDate last = to.withDayOfMonth(1);
            while (!cursor.isAfter(last)) {
                LocalDate monthEnd = cursor.plusMonths(1).minusDays(1);
                log.accept("Lade Monatsübersicht " + cursor + " bis " + monthEnd + ".");
                String response = get("/v9/user/consumed-items/nutrients-daily?start=" + cursor + "&end=" + monthEnd);
                extractDailySummaries(new JsonParser(response).parse(), result);
                cursor = cursor.plusMonths(1);
            }
            result.entrySet().removeIf(entry -> entry.getKey().isBefore(from) || entry.getKey().isAfter(to));
            return result;
        }

        @SuppressWarnings("unchecked")
        private static void extractDailySummaries(Object node, Map<LocalDate, Map<String, Object>> result) {
            if (node instanceof Map<?, ?> raw) {
                Map<String, Object> map = (Map<String, Object>) raw;
                LocalDate date = parseDate(str(map.get("date")));
                if (date != null && (map.containsKey("energy") || map.containsKey("energy_goal"))) {
                    result.put(date, normalizeDaily(date, map));
                }
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    LocalDate keyDate = parseDate(entry.getKey());
                    if (keyDate != null && entry.getValue() instanceof Map<?, ?> valueRaw) {
                        result.put(keyDate, normalizeDaily(keyDate, (Map<String, Object>) valueRaw));
                    } else {
                        extractDailySummaries(entry.getValue(), result);
                    }
                }
            } else if (node instanceof List<?> list) {
                list.forEach(item -> extractDailySummaries(item, result));
            }
        }

        private static Map<String, Object> normalizeDaily(LocalDate date, Map<String, Object> map) {
            Map<String, Object> daily = new LinkedHashMap<>();
            daily.put("date", date.toString());
            daily.put("energy", dbl(map.get("energy")));
            daily.put("carb", dbl(map.containsKey("carb") ? map.get("carb") : map.get("nutrient.carb")));
            daily.put("protein", dbl(map.containsKey("protein") ? map.get("protein") : map.get("nutrient.protein")));
            daily.put("fat", dbl(map.containsKey("fat") ? map.get("fat") : map.get("nutrient.fat")));
            daily.put("energy_goal", dbl(map.containsKey("energy_goal") ? map.get("energy_goal") : map.get("energy.energy.goal")));
            return daily;
        }

        private static Map<String, Object> fallbackDaily(LocalDate date, Map<String, Object> consumed) {
            Map<String, Object> daily = new LinkedHashMap<>();
            daily.put("date", date.toString());
            daily.put("energy", 0);
            daily.put("carb", 0);
            daily.put("protein", 0);
            daily.put("fat", 0);
            daily.put("energy_goal", 0);
            return daily;
        }

        private static boolean hasConsumedEntries(Map<String, Object> consumed) {
            for (String key : List.of("products", "recipe_portions", "simple_products")) {
                Object value = consumed.get(key);
                if (value instanceof List<?> list && !list.isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings("unchecked")
        private static void collectProductIds(Object node, Set<String> ids) {
            if (node instanceof Map<?, ?> raw) {
                Map<String, Object> map = (Map<String, Object>) raw;
                String productId = str(map.get("product_id"));
                if (productId != null && !productId.isBlank()) {
                    ids.add(productId);
                }
                map.values().forEach(value -> collectProductIds(value, ids));
            } else if (node instanceof List<?> list) {
                list.forEach(value -> collectProductIds(value, ids));
            }
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> objectOrEmpty(String json) {
            Object parsed = new JsonParser(json).parse();
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return new LinkedHashMap<>();
        }

        private String get(String path) throws Exception {
            return request("GET", path, null);
        }

        private String request(String method, String path, String body) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path))
                    .header("Accept", "application/json");
            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
            }
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException(method + " " + path + " lieferte HTTP " + response.statusCode());
            }
            return response.body();
        }

        private static String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }

        private static String jsonEscape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    static final class JsonParser {
        private final String text;
        private int position;

        JsonParser(String text) {
            this.text = Objects.requireNonNull(text);
        }

        Object parse() {
            Object value = value();
            whitespace();
            if (position != text.length()) {
                throw error("Unerwartete Zeichen");
            }
            return value;
        }

        private Object value() {
            whitespace();
            if (position >= text.length()) {
                throw error("Unerwartetes Ende");
            }
            char c = text.charAt(position);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            whitespace();
            if (peek('}')) {
                position++;
                return map;
            }
            while (true) {
                String key = string();
                whitespace();
                expect(':');
                map.put(key, value());
                whitespace();
                if (peek('}')) {
                    position++;
                    return map;
                }
                expect(',');
            }
        }

        private List<Object> array() {
            expect('[');
            List<Object> list = new ArrayList<>();
            whitespace();
            if (peek(']')) {
                position++;
                return list;
            }
            while (true) {
                list.add(value());
                whitespace();
                if (peek(']')) {
                    position++;
                    return list;
                }
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (position >= text.length()) {
                        throw error("Unvollständige Escape-Sequenz");
                    }
                    char escaped = text.charAt(position++);
                    switch (escaped) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (position + 4 > text.length()) {
                                throw error("Unvollständige Unicode-Escape-Sequenz");
                            }
                            out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                            position += 4;
                        }
                        default -> throw error("Ungültige Escape-Sequenz");
                    }
                } else {
                    out.append(c);
                }
            }
            throw error("Nicht abgeschlossener String");
        }

        private Object number() {
            int start = position;
            if (peek('-')) {
                position++;
            }
            while (position < text.length() && Character.isDigit(text.charAt(position))) {
                position++;
            }
            if (peek('.')) {
                position++;
                while (position < text.length() && Character.isDigit(text.charAt(position))) {
                    position++;
                }
            }
            if (position < text.length() && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
                position++;
                if (peek('+') || peek('-')) {
                    position++;
                }
                while (position < text.length() && Character.isDigit(text.charAt(position))) {
                    position++;
                }
            }
            if (start == position) {
                throw error("Zahl erwartet");
            }
            return Double.parseDouble(text.substring(start, position));
        }

        private Object literal(String literal, Object value) {
            if (text.startsWith(literal, position)) {
                position += literal.length();
                return value;
            }
            throw error("Literal erwartet: " + literal);
        }

        private void whitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        private void expect(char expected) {
            whitespace();
            if (position >= text.length() || text.charAt(position) != expected) {
                throw error("'" + expected + "' erwartet");
            }
            position++;
        }

        private boolean peek(char c) {
            return position < text.length() && text.charAt(position) == c;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " bei Position " + position);
        }
    }
}

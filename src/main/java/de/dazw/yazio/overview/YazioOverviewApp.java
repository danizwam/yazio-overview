package de.dazw.yazio.overview;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.dazw.yazio.overview.demo.DemoDataFactory;
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
import java.util.HashMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;

public class YazioOverviewApp {
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    private static final Path APP_BASE_DIR = appBaseDir();
    private static final Path DATA_DIR = defaultDataDir();
    private static final Path STATIC_DIR = defaultStaticDir();
    private static final int DEFAULT_SYNC_LOOKBACK_DAYS = 14;
    private static final String APP_VERSION = configuredValue("yazio.app.version", "YAZIO_APP_VERSION", "dev");
    private static final String BUILD_DATE = configuredValue("yazio.build.date", "YAZIO_BUILD_DATE", "");
    private static final boolean DEMO_MODE = Boolean.parseBoolean(configuredValue("yazio.demo.mode", "YAZIO_DEMO_MODE", "false"));
    private static final boolean USER_MANAGEMENT = Boolean.parseBoolean(configuredValue("yazio.user.management", "YAZIO_USER_MANAGEMENT", "false"));
    private static final String ADMIN_ID = "1337";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = configuredValue("yazio.admin.password", "YAZIO_ADMIN_PASSWORD", "admin");
    private static final String DEMO_COOKIE = "YAZIO_OVERVIEW_DEMO_SESSION";
    private static final String AUTH_COOKIE = "YAZIO_OVERVIEW_AUTH";
    private static final String DEMO_PASSWORD = "passwordMock123";
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".html", "text/html; charset=utf-8",
            ".css", "text/css; charset=utf-8",
            ".js", "text/javascript; charset=utf-8",
            ".json", "application/json; charset=utf-8",
            ".svg", "image/svg+xml"
    );

    private final Object lock = new Object();
    private final SyncState defaultSyncState = new SyncState();
    private final Map<String, DemoSession> demoSessions = new ConcurrentHashMap<>();
    private final Map<String, AppSession> appSessions = new ConcurrentHashMap<>();
    private final ThreadLocal<String> requestSessionId = new ThreadLocal<>();
    private final ThreadLocal<String> requestUserId = ThreadLocal.withInitial(() -> ADMIN_ID);
    private final ReportService reportService = new ReportService();
    private final InsightService insightService = new InsightService();
    private DataStore store = DataStore.empty();

    public static void main(String[] args) throws Exception {
        Files.createDirectories(userDataDir(ADMIN_ID));
        YazioOverviewApp app = new YazioOverviewApp();
        app.reload();

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/api/auth/status", app.route(app::authStatus));
        server.createContext("/api/login", app.route(app::login));
        server.createContext("/api/logout", app.route(app::logout));
        server.createContext("/api/users", app.route(app::users));
        server.createContext("/api/status", app.route(app::status));
        server.createContext("/api/upload", app.route(app::upload));
        server.createContext("/api/day", app.route(app::day));
        server.createContext("/api/range", app.route(app::range));
        server.createContext("/api/settings", app.route(app::settings));
        server.createContext("/api/note", app.route(app::note));
        server.createContext("/api/item-classification", app.route(app::itemClassification));
        server.createContext("/api/item-classifications", app.route(app::itemClassifications));
        server.createContext("/api/data-quality", app.route(app::dataQuality));
        server.createContext("/api/backup", app.route(app::backup));
        server.createContext("/api/restore", app.route(app::restore));
        server.createContext("/api/sync/status", app.route(app::syncStatus));
        server.createContext("/api/sync", app.route(app::sync));
        server.createContext("/api/insights/products", app.route(app::insightProducts));
        server.createContext("/api/insights/product-days", app.route(app::insightProductDays));
        server.createContext("/api/insights/days", app.route(app::insightDays));
        server.createContext("/api/insights/meals", app.route(app::insightMeals));
        server.createContext("/api/insights/weekdays", app.route(app::insightWeekdays));
        server.createContext("/api/insights/months", app.route(app::insightMonths));
        server.createContext("/api/export/xlsx", app.route(app::exportXlsx));
        server.createContext("/api/export/pdf", app.route(app::exportPdf));
        server.createContext("/", app.route(app::staticFile));
        server.start();
        String url = String.format(Locale.ROOT, "http://localhost:%d", PORT);
        System.out.printf(Locale.ROOT, "Yazio Overview running on %s%n", url);
        openBrowserIfRequested(url);
    }

    private void reload() {
        synchronized (lock) {
            try {
                if (DEMO_MODE) {
                    store = DataStore.empty();
                    return;
                }
                Map<String, Product> products = Files.exists(productsFile())
                        ? parseProducts(Files.readString(productsFile()))
                        : Map.of();
                Map<LocalDate, Day> days = Files.exists(daysFile())
                        ? parseDays(Files.readString(daysFile()))
                        : Map.of();
                AppSettings settings = Files.exists(settingsFile())
                        ? parseSettings(Files.readString(settingsFile()))
                        : AppSettings.empty();
                Map<LocalDate, String> notes = Files.exists(notesFile())
                        ? parseNotes(Files.readString(notesFile()))
                        : Map.of();
                Map<String, String> itemClassifications = Files.exists(itemClassificationsFile())
                        ? parseItemClassifications(Files.readString(itemClassificationsFile()))
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
        body.put("version", Map.of(
                "number", APP_VERSION,
                "buildDate", BUILD_DATE
        ));
        body.put("demoMode", DEMO_MODE);
        if (DEMO_MODE) {
            body.put("demoPassword", DEMO_PASSWORD);
        }
        body.put("error", snapshot.error());
        send(exchange, 200, body);
    }

    private void authStatus(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        User user = currentUser();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userManagement", USER_MANAGEMENT);
        body.put("loggedIn", !USER_MANAGEMENT || user != null);
        body.put("user", user == null ? null : user.publicMap());
        body.put("adminId", ADMIN_ID);
        send(exchange, 200, body);
    }

    @SuppressWarnings("unchecked")
    private void login(HttpExchange exchange) throws IOException {
        if (!USER_MANAGEMENT) {
            send(exchange, 200, Map.of("user", adminUser().publicMap()));
            return;
        }
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> body = (Map<String, Object>) new JsonParser(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8)).parse();
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        User user = authenticate(username, password);
        if (user == null) {
            send(exchange, 401, Map.of("error", "Benutzername oder Passwort ist falsch."));
            return;
        }
        String sessionId = UUID.randomUUID().toString();
        appSessions.put(sessionId, new AppSession(user.id(), LocalDateTime.now()));
        requestUserId.set(user.id());
        exchange.getResponseHeaders().add("Set-Cookie",
                AUTH_COOKIE + "=" + sessionId + "; Path=/; SameSite=Lax; HttpOnly");
        send(exchange, 200, Map.of("user", user.publicMap()));
    }

    private void logout(HttpExchange exchange) throws IOException {
        String sessionId = cookie(exchange, AUTH_COOKIE);
        if (sessionId != null) {
            appSessions.remove(sessionId);
        }
        exchange.getResponseHeaders().add("Set-Cookie",
                AUTH_COOKIE + "=; Path=/; Max-Age=0; SameSite=Lax; HttpOnly");
        send(exchange, 200, Map.of("ok", true));
    }

    @SuppressWarnings("unchecked")
    private void users(HttpExchange exchange) throws IOException {
        User user = currentUser();
        if (user == null || !user.admin()) {
            send(exchange, 403, Map.of("error", "Nur Admins koennen Benutzer verwalten."));
            return;
        }
        if (exchange.getRequestMethod().equals("GET")) {
            send(exchange, 200, Map.of("items", users().values().stream().map(User::publicMap).toList()));
            return;
        }
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Map<String, Object> body = (Map<String, Object>) new JsonParser(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8)).parse();
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        String name = str(body.get("name"));
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            send(exchange, 400, Map.of("error", "Bitte Benutzername und Passwort angeben."));
            return;
        }
        Map<String, User> users = new LinkedHashMap<>(users());
        boolean exists = users.values().stream().anyMatch(existing -> existing.username().equalsIgnoreCase(username.trim()));
        if (exists) {
            send(exchange, 409, Map.of("error", "Der Benutzername existiert bereits."));
            return;
        }
        String id = nextUserId(users);
        User created = new User(id, username.trim(), name == null ? "" : name.trim(),
                Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)), false);
        users.put(id, created);
        saveUsers(users);
        Files.createDirectories(userDataDir(id));
        send(exchange, 200, Map.of("user", created.publicMap(), "items", users.values().stream().map(User::publicMap).toList()));
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
        if (DEMO_MODE) {
            AppSettings updated = new AppSettings(
                    str(body.get("name")),
                    str(body.get("birthDate")),
                    str(body.get("username")),
                    Base64.getEncoder().encodeToString(DEMO_PASSWORD.getBytes(StandardCharsets.UTF_8))
            );
            updateStore(new DataStore(snapshot().products(), snapshot().days(), updated,
                    snapshot().notes(), snapshot().itemClassifications()));
            send(exchange, 200, snapshot().settings().publicMap());
            return;
        }
        AppSettings updated = new AppSettings(
                str(body.get("name")),
                str(body.get("birthDate")),
                str(body.get("username")),
                password == null || password.isBlank() ? existing.passwordBase64() : Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8))
        );
        Files.createDirectories(dataDir());
        Files.writeString(settingsFile(), JsonWriter.write(updated.toPersistedMap()), StandardCharsets.UTF_8);
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
        if (DEMO_MODE) {
            updateStore(new DataStore(snapshot().products(), snapshot().days(), snapshot().settings(),
                    notes, snapshot().itemClassifications()));
            send(exchange, 200, Map.of("date", date.toString(), "note", snapshot().notes().getOrDefault(date, "")));
            return;
        }
        Files.createDirectories(dataDir());
        Files.writeString(notesFile(), JsonWriter.write(notesToMap(notes)), StandardCharsets.UTF_8);
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

        if (DEMO_MODE) {
            updateStore(new DataStore(snapshot().products(), snapshot().days(), snapshot().settings(),
                    snapshot().notes(), overrides));
            send(exchange, 200, Map.of(
                    "itemId", itemId,
                    "classification", requested,
                    "automaticClassification", automatic,
                    "classificationOverridden", !requested.equals(automatic),
                    "learnedProduct", learnProduct && canLearnProduct
            ));
            return;
        }

        Files.createDirectories(dataDir());
        Files.writeString(itemClassificationsFile(), JsonWriter.write(new LinkedHashMap<>(overrides)), StandardCharsets.UTF_8);
        reload();
        send(exchange, 200, Map.of(
                "itemId", itemId,
                "classification", requested,
                "automaticClassification", automatic,
                "classificationOverridden", !requested.equals(automatic),
                "learnedProduct", learnProduct && canLearnProduct
        ));
    }

    private void itemClassifications(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equals("GET")) {
            send(exchange, 200, Map.of("items", productClassificationRules()));
            return;
        }
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) new JsonParser(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8)).parse();
        String key = str(body.get("key"));
        if (key == null || !key.startsWith("product:")) {
            send(exchange, 400, Map.of("error", "Bitte eine Produktregel auswÃ¤hlen."));
            return;
        }
        Map<String, String> overrides = new TreeMap<>(snapshot().itemClassifications());
        overrides.remove(key);
        if (DEMO_MODE) {
            updateStore(new DataStore(snapshot().products(), snapshot().days(), snapshot().settings(),
                    snapshot().notes(), overrides));
            send(exchange, 200, Map.of("items", productClassificationRules()));
            return;
        }
        Files.createDirectories(dataDir());
        Files.writeString(itemClassificationsFile(), JsonWriter.write(new LinkedHashMap<>(overrides)), StandardCharsets.UTF_8);
        reload();
        send(exchange, 200, Map.of("items", productClassificationRules()));
    }

    private void dataQuality(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        send(exchange, 200, Map.of("items", dataQualityItems()));
    }

    private void backup(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String fileName = "Yazio-Overview-Backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip";
        sendBytes(exchange, 200, "application/zip", fileName, backupBytes());
    }

    private void restore(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        if (DEMO_MODE) {
            send(exchange, 200, Map.of("restored", List.of("demo-session")));
            return;
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            send(exchange, 400, Map.of("error", "Bitte ein ZIP-Backup hochladen."));
            return;
        }
        String boundary = boundary(contentType);
        if (boundary == null) {
            send(exchange, 400, Map.of("error", "Multipart-Boundary fehlt."));
            return;
        }
        Map<String, byte[]> parts = parseMultipart(readAll(exchange.getRequestBody()), boundary);
        byte[] backup = parts.get("backup");
        if (backup == null || backup.length == 0) {
            send(exchange, 400, Map.of("error", "Bitte ein ZIP-Backup auswÃ¤hlen."));
            return;
        }
        List<String> restored = restoreBackup(backup);
        reload();
        send(exchange, 200, Map.of("restored", restored));
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
        if (DEMO_MODE) {
            if (!syncState().start(from, to)) {
                send(exchange, 409, Map.of("error", "Es lÃ¤uft bereits eine Synchronisierung."));
                return;
            }
            runDemoSync(from, to);
            send(exchange, 202, syncState().toMap());
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
        if (!syncState().start(from, to)) {
            send(exchange, 409, Map.of("error", "Es läuft bereits eine Synchronisierung."));
            return;
        }
        String syncUsername = username;
        String syncPassword = password;
        Thread worker = new Thread(() -> runSync(syncUsername, syncPassword, from, to), "yazio-sync");
        worker.setDaemon(true);
        worker.start();
        send(exchange, 202, syncState().toMap());
    }

    private void syncStatus(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        send(exchange, 200, syncState().toMap());
    }

    private void runDemoSync(LocalDate from, LocalDate to) {
        SyncState state = syncState();
        try {
            state.log("Demo-Modus aktiv: Es wird keine Verbindung zur Yazio-API aufgebaut.");
            state.log("Erzeuge Mock-Produkte und Mock-Tage fuer " + from + " bis " + to + ".");
            DataStore generated = DemoDataFactory.generate(from, to, snapshot().settings());
            updateStore(new DataStore(generated.products(), generated.days(), generated.settings(),
                    snapshot().notes(), snapshot().itemClassifications()));
            state.log("Mock-Import abgeschlossen. Credentials wurden ignoriert.");
            state.success(generated.days().size(), generated.products().size());
        } catch (RuntimeException ex) {
            state.fail(ex.getMessage());
        }
    }

    private Map<String, Object> demoImportResponse() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(DEFAULT_SYNC_LOOKBACK_DAYS);
        runDemoSync(from, to);
        DataStore snapshot = snapshot();
        return Map.of(
                "updated", List.of("demo-products", "demo-days"),
                "status", Map.of(
                        "productCount", snapshot.products().size(),
                        "dayCount", snapshot.days().size(),
                        "firstDate", snapshot.firstDate().map(LocalDate::toString).orElse(null),
                        "lastDate", snapshot.lastDate().map(LocalDate::toString).orElse(null)
                )
        );
    }

    private void runSync(String username, String password, LocalDate from, LocalDate to) {
        try {
            SyncState state = syncState();
            state.log("Starte Yazio-Synchronisierung fuer " + from + " bis " + to + ".");
            YazioSyncResult result = new YazioClient(state::log).sync(username, password, from, to);
            state.log("Speichere Import-Snapshot lokal.");
            Files.createDirectories(dataDir());
            Map<String, Object> currentDays = readJsonObject(daysFile());
            Map<String, Object> currentProducts = readJsonObject(productsFile());
            writeImportSnapshot(result, from, to);
            state.log("Konsolidiere Import-Historie.");
            ConsolidatedImport consolidated = consolidateImports(currentDays, currentProducts);
            Files.writeString(daysFile(), JsonWriter.write(consolidated.days()), StandardCharsets.UTF_8);
            Files.writeString(productsFile(), JsonWriter.write(consolidated.products()), StandardCharsets.UTF_8);
            reload();
            state.success(consolidated.days().size(), consolidated.products().size());
        } catch (Exception ex) {
            syncState().fail(ex.getMessage());
        }
    }

    private void upload(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        if (DEMO_MODE) {
            send(exchange, 200, demoImportResponse());
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
        Files.createDirectories(dataDir());
        List<String> updated = new ArrayList<>();
        if (parts.containsKey("products")) {
            validateJson(parts.get("products"), "products.json");
            Files.write(productsFile(), parts.get("products"));
            updated.add("products.json");
        }
        if (parts.containsKey("days")) {
            validateJson(parts.get("days"), "days.json");
            Files.write(daysFile(), parts.get("days"));
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

    private List<Map<String, Object>> productClassificationRules() {
        DataStore snapshot = snapshot();
        List<Map<String, Object>> rules = new ArrayList<>();
        for (Map.Entry<String, String> entry : snapshot.itemClassifications().entrySet()) {
            if (!entry.getKey().startsWith("product:")) {
                continue;
            }
            String productId = entry.getKey().substring("product:".length());
            Product product = snapshot.products().get(productId);
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("key", entry.getKey());
            rule.put("productId", productId);
            rule.put("name", product == null ? "(Unbekanntes Produkt)" : product.name());
            rule.put("producer", product == null ? "" : product.producer());
            rule.put("classification", entry.getValue());
            rules.add(rule);
        }
        rules.sort(Comparator.comparing(rule -> String.valueOf(rule.get("name")), String.CASE_INSENSITIVE_ORDER));
        return rules;
    }

    private List<Map<String, Object>> dataQualityItems() {
        DataStore snapshot = snapshot();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Day day : snapshot.days().values()) {
            for (ConsumedProduct entry : day.products()) {
                Product product = snapshot.products().get(entry.productId());
                if (product == null) {
                    items.add(dataQualityItem("warnung", day.date(), "Unbekanntes Produkt",
                            "Produkt-ID " + entry.productId() + " ist in products.json nicht vorhanden."));
                    continue;
                }
                if (entry.amount() > 0 && macroEnergy(product, entry.amount()) <= 0) {
                    items.add(dataQualityItem("hinweis", day.date(), "Produkt ohne Kalorien",
                            product.name() + " hat fÃ¼r diesen Eintrag 0 kcal."));
                }
            }
            for (SimpleProduct simple : day.simpleProducts()) {
                String title = simple.aiGenerated() ? "KI erfasste Mahlzeit" : "Einfaches Produkt";
                items.add(dataQualityItem("info", day.date(), title,
                        (simple.name() == null || simple.name().isBlank()) ? "Eintrag ohne Namen" : simple.name()));
            }
        }
        items.sort(Comparator.comparing(item -> String.valueOf(item.get("date"))));
        return items;
    }

    private static Map<String, Object> dataQualityItem(String severity, LocalDate date, String type, String message) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("severity", severity);
        item.put("date", date.toString());
        item.put("type", type);
        item.put("message", message);
        return item;
    }

    private static double macroEnergy(Product product, double amount) {
        return product.nutrients().getOrDefault("energy.energy", 0.0) * amount;
    }

    private byte[] backupBytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            if (!Files.isDirectory(dataDir())) {
                return output.toByteArray();
            }
            try (var stream = Files.walk(dataDir())) {
                for (Path path : stream.filter(Files::isRegularFile).toList()) {
                    Path relative = dataDir().relativize(path);
                    zip.putNextEntry(new ZipEntry(relative.toString().replace('\\', '/')));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        }
        return output.toByteArray();
    }

    private List<String> restoreBackup(byte[] bytes) throws IOException {
        Files.createDirectories(dataDir());
        Path root = dataDir().toAbsolutePath().normalize();
        List<String> restored = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !allowedBackupEntry(entry.getName())) {
                    continue;
                }
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                restored.add(root.relativize(target).toString().replace('\\', '/'));
            }
        }
        return restored;
    }

    private static boolean allowedBackupEntry(String name) {
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../")) {
            return false;
        }
        if (normalized.equals("products.json") || normalized.equals("days.json")
                || normalized.equals("settings.json") || normalized.equals("notes.json")
                || normalized.equals("item-classifications.json")) {
            return true;
        }
        return normalized.startsWith("imports/") && normalized.endsWith(".json");
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

    private HttpHandler route(ExchangeHandler handler) {
        return exchange -> {
            String sessionId = null;
            String userId = USER_MANAGEMENT ? null : ADMIN_ID;
            if (DEMO_MODE) {
                sessionId = demoSessionId(exchange);
                requestSessionId.set(sessionId);
                exchange.getResponseHeaders().add("Set-Cookie",
                        DEMO_COOKIE + "=" + sessionId + "; Path=/; SameSite=Lax; HttpOnly");
            }
            if (USER_MANAGEMENT) {
                AppSession appSession = appSession(exchange);
                if (appSession != null) {
                    userId = appSession.userId();
                } else if (protectedApi(exchange)) {
                    send(exchange, 401, Map.of("error", "Bitte einloggen."));
                    return;
                }
            }
            requestUserId.set(userId);
            try {
                handler.handle(exchange);
            } finally {
                if (DEMO_MODE) {
                    requestSessionId.remove();
                }
                requestUserId.remove();
            }
        };
    }

    private boolean protectedApi(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        return path.startsWith("/api/")
                && !path.equals("/api/auth/status")
                && !path.equals("/api/login")
                && !path.equals("/api/logout");
    }

    private AppSession appSession(HttpExchange exchange) {
        String sessionId = cookie(exchange, AUTH_COOKIE);
        return sessionId == null ? null : appSessions.get(sessionId);
    }

    private static String cookie(HttpExchange exchange, String name) {
        String header = exchange.getRequestHeaders().getFirst("Cookie");
        if (header == null) {
            return null;
        }
        for (String part : header.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                String value = trimmed.substring((name + "=").length());
                return value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private String demoSessionId(HttpExchange exchange) {
        String id = cookie(exchange, DEMO_COOKIE);
        if (id != null) {
            demoSessions.computeIfAbsent(id, ignored -> DemoSession.create());
            return id;
        }
        String newId = UUID.randomUUID().toString();
        demoSessions.put(newId, DemoSession.create());
        return newId;
    }

    private void writeImportSnapshot(YazioSyncResult result, LocalDate from, LocalDate to) throws IOException {
        LocalDateTime importedAt = LocalDateTime.now();
        String folderName = importedAt.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path importDir = importsDir().resolve(folderName);
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
        if (!Files.isDirectory(importsDir())) {
            return List.of();
        }
        try (var stream = Files.list(importsDir())) {
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
        if (DEMO_MODE) {
            String sessionId = requestSessionId.get();
            if (sessionId == null) {
                return DataStore.empty();
            }
            return demoSessions.computeIfAbsent(sessionId, ignored -> DemoSession.create()).store();
        }
        return loadStore(dataDir());
    }

    private DataStore loadStore(Path directory) {
        try {
            Path productsFile = directory.resolve("products.json");
            Path daysFile = directory.resolve("days.json");
            Path settingsFile = directory.resolve("settings.json");
            Path notesFile = directory.resolve("notes.json");
            Path classificationsFile = directory.resolve("item-classifications.json");
            Map<String, Product> products = Files.exists(productsFile)
                    ? parseProducts(Files.readString(productsFile))
                    : Map.of();
            Map<LocalDate, Day> days = Files.exists(daysFile)
                    ? parseDays(Files.readString(daysFile))
                    : Map.of();
            AppSettings settings = Files.exists(settingsFile)
                    ? parseSettings(Files.readString(settingsFile))
                    : AppSettings.empty();
            Map<LocalDate, String> notes = Files.exists(notesFile)
                    ? parseNotes(Files.readString(notesFile))
                    : Map.of();
            Map<String, String> itemClassifications = Files.exists(classificationsFile)
                    ? parseItemClassifications(Files.readString(classificationsFile))
                    : Map.of();
            return new DataStore(products, days, settings, notes, itemClassifications);
        } catch (RuntimeException | IOException ex) {
            return DataStore.empty().withError(ex.getMessage());
        }
    }

    private void updateStore(DataStore updated) {
        if (DEMO_MODE) {
            String sessionId = requestSessionId.get();
            if (sessionId != null) {
                demoSessions.compute(sessionId, (ignored, existing) -> {
                    DemoSession session = existing == null ? DemoSession.create() : existing;
                    return new DemoSession(updated, session.syncState());
                });
            }
            return;
        }
        synchronized (lock) {
            store = updated;
        }
    }

    private SyncState syncState() {
        if (DEMO_MODE) {
            String sessionId = requestSessionId.get();
            if (sessionId == null) {
                return defaultSyncState;
            }
            return demoSessions.computeIfAbsent(sessionId, ignored -> DemoSession.create()).syncState();
        }
        return defaultSyncState;
    }

    private Path dataDir() {
        return userDataDir(requestUserId.get());
    }

    private static Path userDataDir(String userId) {
        String safeId = userId == null || userId.isBlank() ? ADMIN_ID : userId.replaceAll("[^A-Za-z0-9_-]", "_");
        return DATA_DIR.resolve(safeId);
    }

    private Path productsFile() {
        return dataDir().resolve("products.json");
    }

    private Path daysFile() {
        return dataDir().resolve("days.json");
    }

    private Path settingsFile() {
        return dataDir().resolve("settings.json");
    }

    private Path notesFile() {
        return dataDir().resolve("notes.json");
    }

    private Path itemClassificationsFile() {
        return dataDir().resolve("item-classifications.json");
    }

    private Path importsDir() {
        return dataDir().resolve("imports");
    }

    private static Path usersFile() {
        return DATA_DIR.resolve("users.json");
    }

    private User currentUser() {
        String userId = requestUserId.get();
        return userId == null ? null : users().get(userId);
    }

    private User adminUser() {
        return new User(ADMIN_ID, ADMIN_USERNAME, "Admin", "", true);
    }

    private User authenticate(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        if (ADMIN_USERNAME.equalsIgnoreCase(username.trim()) && ADMIN_PASSWORD.equals(password)) {
            return adminUser();
        }
        return users().values().stream()
                .filter(user -> !user.admin())
                .filter(user -> user.username().equalsIgnoreCase(username.trim()))
                .filter(user -> password.equals(user.password()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, User> users() {
        Map<String, User> users = new LinkedHashMap<>();
        users.put(ADMIN_ID, adminUser());
        if (!Files.isRegularFile(usersFile())) {
            return users;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) new JsonParser(Files.readString(usersFile())).parse();
            Object rawItems = root.get("users");
            if (rawItems instanceof List<?> items) {
                for (Object raw : items) {
                    if (raw instanceof Map<?, ?> map) {
                        User user = User.fromMap((Map<?, ?>) map);
                        if (user != null && !ADMIN_ID.equals(user.id())) {
                            users.put(user.id(), user);
                        }
                    }
                }
            }
        } catch (RuntimeException | IOException ignored) {
            return users;
        }
        return users;
    }

    private void saveUsers(Map<String, User> users) throws IOException {
        Files.createDirectories(DATA_DIR);
        List<Map<String, Object>> items = users.values().stream()
                .filter(user -> !user.admin())
                .map(User::persistedMap)
                .toList();
        Files.writeString(usersFile(), JsonWriter.write(Map.of("users", items)), StandardCharsets.UTF_8);
    }

    private static String nextUserId(Map<String, User> users) {
        int next = users.keySet().stream()
                .filter(id -> !ADMIN_ID.equals(id))
                .mapToInt(id -> {
                    try {
                        return Integer.parseInt(id);
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                })
                .max()
                .orElse(0) + 1;
        while (users.containsKey(String.valueOf(next))) {
            next++;
        }
        return String.valueOf(next);
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

    private static String configuredValue(String property, String environment, String fallback) {
        String configured = System.getProperty(property);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(environment);
        }
        return configured == null || configured.isBlank() ? fallback : configured;
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

    private record DemoSession(DataStore store, SyncState syncState) {
        private static DemoSession create() {
            return new DemoSession(DemoDataFactory.emptyStore(), new SyncState());
        }
    }

    private record AppSession(String userId, LocalDateTime createdAt) {
    }

    private record User(String id, String username, String name, String passwordBase64, boolean admin) {
        private String password() {
            if (passwordBase64 == null || passwordBase64.isBlank()) {
                return "";
            }
            try {
                return new String(Base64.getDecoder().decode(passwordBase64), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                return "";
            }
        }

        private Map<String, Object> publicMap() {
            return Map.of(
                    "id", id,
                    "username", username,
                    "name", name == null ? "" : name,
                    "admin", admin
            );
        }

        private Map<String, Object> persistedMap() {
            return Map.of(
                    "id", id,
                    "username", username,
                    "name", name == null ? "" : name,
                    "passwordBase64", passwordBase64 == null ? "" : passwordBase64,
                    "admin", admin
            );
        }

        private static User fromMap(Map<?, ?> map) {
            String id = str(map.get("id"));
            String username = str(map.get("username"));
            if (id == null || id.isBlank() || username == null || username.isBlank()) {
                return null;
            }
            return new User(id, username, str(map.get("name")), str(map.get("passwordBase64")), bool(map.get("admin")));
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

}


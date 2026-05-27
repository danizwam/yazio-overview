package de.yazio.overview.sync;

import de.yazio.overview.json.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Kapselt den direkten Import aus der inoffiziellen Yazio-API.
 *
 * <p>Der Code ist bewusst von der Web-App getrennt, weil Login, API-Endpunkte
 * und Fortschrittslogs eine eigene technische Verantwortung bilden.</p>
 */
public final class SyncSupport {
    private SyncSupport() {
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

    private static LocalDate parseDate(String raw) {
        try {
            return raw == null ? null : LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
    public record YazioSyncResult(Map<String, Object> days, Map<String, Object> products) {
    }

    public static final class SyncState {
        private boolean running;
        private String status = "idle";
        private String error = "";
        private LocalDate from;
        private LocalDate to;
        private int dayCount;
        private int productCount;
        private final List<String> logs = new ArrayList<>();

        public synchronized boolean start(LocalDate from, LocalDate to) {
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

        public synchronized void log(String message) {
            String line = DateTimeFormatter.ofPattern("HH:mm:ss").format(java.time.LocalTime.now()) + "  " + message;
            logs.add(line);
            if (logs.size() > 500) {
                logs.remove(0);
            }
        }

        public synchronized void success(int dayCount, int productCount) {
            this.dayCount = dayCount;
            this.productCount = productCount;
            this.running = false;
            this.status = "success";
            log("Fertig: " + dayCount + " Tage, " + productCount + " Produkte synchronisiert.");
        }

        public synchronized void fail(String message) {
            this.running = false;
            this.status = "error";
            this.error = message == null ? "Unbekannter Fehler" : message;
            log("Fehler: " + this.error);
        }

        public synchronized Map<String, Object> toMap() {
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

    public static final class YazioClient {
        private static final String BASE = "https://yzapi.yazio.com";
        private static final String CLIENT_ID = "1_4hiybetvfksgw40o0sog4s884kwc840wwso8go4k8c04goo4c";
        private static final String CLIENT_SECRET = "6rok2m65xuskgkgogw40wkkk8sw0osg84s8cggsc4woos4s8o";

        private final HttpClient http = HttpClient.newHttpClient();
        private final Consumer<String> log;
        private String token;

        public YazioClient(Consumer<String> log) {
            this.log = log == null ? ignored -> {
            } : log;
        }

        public YazioSyncResult sync(String username, String password, LocalDate from, LocalDate to) throws Exception {
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


}

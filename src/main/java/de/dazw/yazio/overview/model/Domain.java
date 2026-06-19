package de.dazw.yazio.overview.model;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Zentrale Datenstrukturen der Anwendung.
 *
 * <p>Die Yazio-Exportdaten bleiben bewusst nah an der JSON-Struktur, damit
 * neue Exportfelder später leicht ergänzt werden können.</p>
 */
public final class Domain {
    private Domain() {
    }

    public record DataStore(Map<String, Product> products, Map<LocalDate, Day> days, AppSettings settings,
                            Map<LocalDate, String> notes, Map<LocalDate, String> sportNotes,
                            Map<String, String> itemClassifications, String error) {
        public static DataStore empty() {
            return new DataStore(Map.of(), Map.of(), AppSettings.empty(), Map.of(), Map.of(), Map.of(), null);
        }

        public DataStore(Map<String, Product> products, Map<LocalDate, Day> days, AppSettings settings,
                         Map<LocalDate, String> notes, Map<String, String> itemClassifications) {
            this(products, days, settings, notes, Map.of(), itemClassifications, null);
        }

        public DataStore(Map<String, Product> products, Map<LocalDate, Day> days, AppSettings settings,
                         Map<LocalDate, String> notes, Map<LocalDate, String> sportNotes,
                         Map<String, String> itemClassifications) {
            this(products, days, settings, notes, sportNotes, itemClassifications, null);
        }

        public DataStore withError(String error) {
            return new DataStore(products, days, settings, notes, sportNotes, itemClassifications, error);
        }

        public Optional<LocalDate> firstDate() {
            return days.keySet().stream().min(LocalDate::compareTo);
        }

        public Optional<LocalDate> lastDate() {
            return days.keySet().stream().max(LocalDate::compareTo);
        }
    }

    public record AppSettings(String name, String birthDate, String username, String passwordBase64,
                              int defaultRangeDays) {
        public static final int DEFAULT_RANGE_DAYS = 7;
        public static final int MIN_RANGE_DAYS = 1;
        public static final int MAX_RANGE_DAYS = 365;

        public AppSettings {
            defaultRangeDays = clampDefaultRangeDays(defaultRangeDays);
        }

        public AppSettings(String name, String birthDate, String username, String passwordBase64) {
            this(name, birthDate, username, passwordBase64, DEFAULT_RANGE_DAYS);
        }

        public static AppSettings empty() {
            return new AppSettings("", "", "", "", DEFAULT_RANGE_DAYS);
        }

        public static int clampDefaultRangeDays(int value) {
            if (value <= 0) {
                return DEFAULT_RANGE_DAYS;
            }
            return Math.max(MIN_RANGE_DAYS, Math.min(MAX_RANGE_DAYS, value));
        }

        public String password() {
            if (passwordBase64 == null || passwordBase64.isBlank()) {
                return "";
            }
            try {
                return new String(Base64.getDecoder().decode(passwordBase64), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                return "";
            }
        }

        public Map<String, Object> publicMap() {
            return Map.of(
                    "name", name == null ? "" : name,
                    "birthDate", birthDate == null ? "" : birthDate,
                    "username", username == null ? "" : username,
                    "hasPassword", passwordBase64 != null && !passwordBase64.isBlank(),
                    "defaultRangeDays", defaultRangeDays
            );
        }

        public Map<String, Object> toPersistedMap() {
            return Map.of(
                    "name", name == null ? "" : name,
                    "birthDate", birthDate == null ? "" : birthDate,
                    "username", username == null ? "" : username,
                    "passwordBase64", passwordBase64 == null ? "" : passwordBase64,
                    "defaultRangeDays", defaultRangeDays
            );
        }
    }

    public record Product(String id, String name, String producer, String baseUnit, Map<String, Double> nutrients,
                          List<Serving> servings) {
    }

    public record Serving(String serving, double amount) {
    }

    public record Day(LocalDate date, Daily daily, List<ConsumedProduct> products, List<SimpleProduct> simpleProducts) {
    }

    public record Daily(double energy, double carbs, double protein, double fat, double energyGoal) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "energy", Macro.round(energy),
                    "carbs", Macro.round(carbs),
                    "protein", Macro.round(protein),
                    "fat", Macro.round(fat),
                    "energyGoal", Macro.round(energyGoal)
            );
        }
    }

    public record ConsumedProduct(String id, String date, String daytime, String type, String productId, double amount,
                                  String serving, double servingQuantity) {
    }

    public record SimpleProduct(String id, String date, String daytime, String type, String name,
                                Map<String, Double> nutrients, boolean aiGenerated) {
    }

    public record DayReport(LocalDate date, Daily daily, List<MealReport> meals, Macro total, AppSettings settings,
                            String note, String sportNote) {
        public DayReport(LocalDate date, Daily daily, List<MealReport> meals, Macro total, AppSettings settings,
                         String note) {
            this(date, daily, meals, total, settings, note, "");
        }

        public Map<String, Object> toMap() {
            List<Map<String, Object>> mealMaps = meals.stream().map(MealReport::toMap).toList();
            return Map.of(
                    "date", date.toString(),
                    "daily", daily.toMap(),
                    "total", total.toMap(),
                    "meals", mealMaps,
                    "note", note == null ? "" : note,
                    "sportNote", sportNote == null ? "" : sportNote,
                    "copyText", copyText()
            );
        }

        public String copyText() {
            StringBuilder text = new StringBuilder();
            text.append(date).append('\n');
            text.append("Gesamt: ").append(total.inline()).append('\n');
            for (MealReport meal : meals) {
                text.append('\n').append(Labels.mealLabel(meal.key())).append(": ").append(meal.total().inline()).append('\n');
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

    public record MealReport(String key, List<FoodItem> items, Macro total) {
        public MealReport(String key) {
            this(key, new ArrayList<>(), new Macro());
        }

        public Map<String, Object> toMap() {
            return Map.of(
                    "key", key,
                    "label", Labels.mealLabel(key),
                    "total", total.toMap(),
                    "items", items.stream().map(FoodItem::toMap).toList(),
                    "copyText", copyText()
            );
        }

        public String copyText() {
            StringBuilder text = new StringBuilder();
            text.append(Labels.mealLabel(key)).append(": ").append(total.inline()).append('\n');
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

    public record FoodItem(String itemId, String name, String producer, double amount, String baseUnit, String serving,
                           double servingQuantity, String productId, String amountLabel, boolean aiGenerated,
                           Macro macro, boolean automaticDrink, String classification) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("itemId", itemId);
            map.put("name", name);
            map.put("producer", producer);
            map.put("amount", Macro.round(amount));
            map.put("baseUnit", baseUnit);
            map.put("serving", serving);
            map.put("servingQuantity", Macro.round(servingQuantity));
            map.put("productId", productId);
            map.put("amountLabel", amountLabel);
            map.put("aiGenerated", aiGenerated);
            map.put("macro", macro.toMap());
            map.put("automaticClassification", automaticDrink ? "drink" : "food");
            map.put("classification", classification);
            map.put("classificationOverridden", !classification.equals(automaticDrink ? "drink" : "food"));
            return map;
        }
    }

    public static final class Macro {
        public double energy;
        public double carbs;
        public double protein;
        public double fat;
        public double sugar;
        public double fiber;

        public void add(Macro other) {
            energy += other.energy;
            carbs += other.carbs;
            protein += other.protein;
            fat += other.fat;
            sugar += other.sugar;
            fiber += other.fiber;
        }

        public Map<String, Object> toMap() {
            return Map.of(
                    "energy", round(energy),
                    "carbs", round(carbs),
                    "protein", round(protein),
                    "fat", round(fat),
                    "sugar", round(sugar),
                    "fiber", round(fiber)
            );
        }

        public String inline() {
            return Labels.format(energy) + " kcal, KH " + Labels.format(carbs) + " g, Protein " + Labels.format(protein)
                    + " g, Fett " + Labels.format(fat) + " g";
        }

        public static double round(double value) {
            return Labels.round(value);
        }
    }
}

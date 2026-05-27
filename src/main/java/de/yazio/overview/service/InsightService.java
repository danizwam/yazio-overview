package de.yazio.overview.service;

import de.yazio.overview.model.Domain.*;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static de.yazio.overview.model.Labels.format;
import static de.yazio.overview.model.Labels.mealLabel;

/**
 * Erstellt produkt- und tagesbezogene Verdichtungen für die Listenansicht.
 *
 * <p>Die Klasse arbeitet bewusst auf {@link DayReport}, damit Produktverknüpfung,
 * KI-Mahlzeiten und Makroberechnung an einer Stelle bleiben.</p>
 */
public final class InsightService {
    public List<Map<String, Object>> products(List<DayReport> reports, String query, String sort, int limit) {
        Map<String, ProductAggregate> aggregates = productAggregates(reports);
        String needle = normalizeSearch(query);
        return aggregates.values().stream()
                .filter(item -> needle.isBlank() || item.searchText().contains(needle))
                .sorted(productComparator(sort))
                .limit(limit)
                .map(ProductAggregate::toMap)
                .toList();
    }

    public List<Map<String, Object>> productDays(List<DayReport> reports, String key, String sort) {
        Map<LocalDate, ProductDayAggregate> days = new TreeMap<>();
        for (DayReport report : reports) {
            for (MealReport meal : report.meals()) {
                for (FoodItem item : meal.items()) {
                    if (!productKey(item).equals(key)) {
                        continue;
                    }
                    ProductDayAggregate aggregate = days.computeIfAbsent(report.date(), ProductDayAggregate::new);
                    aggregate.add(item);
                }
            }
        }
        return days.values().stream()
                .sorted(productDayComparator(sort))
                .map(ProductDayAggregate::toMap)
                .toList();
    }

    public List<Map<String, Object>> days(List<DayReport> reports, String sort, String direction) {
        Comparator<DayReport> comparator = switch (safe(sort)) {
            case "protein" -> Comparator.comparingDouble(report -> report.total().protein);
            case "carbs" -> Comparator.comparingDouble(report -> report.total().carbs);
            case "fat" -> Comparator.comparingDouble(report -> report.total().fat);
            case "date" -> Comparator.comparing(DayReport::date);
            default -> Comparator.comparingDouble(report -> report.total().energy);
        };
        if (!"asc".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }
        return reports.stream()
                .sorted(comparator)
                .map(report -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", report.date().toString());
                    item.put("energy", Macro.round(report.total().energy));
                    item.put("carbs", Macro.round(report.total().carbs));
                    item.put("protein", Macro.round(report.total().protein));
                    item.put("fat", Macro.round(report.total().fat));
                    return item;
                })
                .toList();
    }

    public List<Map<String, Object>> meals(List<DayReport> reports, String sort) {
        Map<String, MacroAggregate> meals = new LinkedHashMap<>();
        for (DayReport report : reports) {
            for (MealReport meal : report.meals()) {
                MacroAggregate aggregate = meals.computeIfAbsent(meal.key(), key -> new MacroAggregate(mealLabel(key)));
                aggregate.add(meal.total());
            }
        }
        return macroList(meals.values(), sort);
    }

    public List<Map<String, Object>> weekdays(List<DayReport> reports, String sort) {
        Map<DayOfWeek, MacroAggregate> weekdays = new TreeMap<>();
        for (DayReport report : reports) {
            DayOfWeek weekday = report.date().getDayOfWeek();
            String label = weekday.getDisplayName(TextStyle.FULL, Locale.GERMANY);
            MacroAggregate aggregate = weekdays.computeIfAbsent(weekday, ignored -> new MacroAggregate(label));
            aggregate.add(report.total());
        }
        return macroList(weekdays.values(), sort);
    }

    public List<Map<String, Object>> months(List<DayReport> reports, String sort) {
        Map<YearMonth, MacroAggregate> months = new TreeMap<>();
        for (DayReport report : reports) {
            YearMonth month = YearMonth.from(report.date());
            MacroAggregate aggregate = months.computeIfAbsent(month, key -> new MacroAggregate(key.toString()));
            aggregate.add(report.total());
        }
        return macroList(months.values(), sort);
    }

    private static List<Map<String, Object>> macroList(Iterable<MacroAggregate> aggregates, String sort) {
        List<MacroAggregate> list = new ArrayList<>();
        aggregates.forEach(list::add);
        return list.stream()
                .sorted(macroComparator(sort))
                .map(MacroAggregate::toMap)
                .toList();
    }

    private static Map<String, ProductAggregate> productAggregates(List<DayReport> reports) {
        Map<String, ProductAggregate> aggregates = new LinkedHashMap<>();
        for (DayReport report : reports) {
            for (MealReport meal : report.meals()) {
                for (FoodItem item : meal.items()) {
                    String key = productKey(item);
                    ProductAggregate aggregate = aggregates.computeIfAbsent(key, ignored -> new ProductAggregate(key, item));
                    aggregate.add(report.date(), item);
                }
            }
        }
        return aggregates;
    }

    private static Comparator<ProductAggregate> productComparator(String sort) {
        return switch (safe(sort)) {
            case "name" -> Comparator.comparing(ProductAggregate::label, String.CASE_INSENSITIVE_ORDER);
            case "calories" -> Comparator.comparingDouble((ProductAggregate item) -> item.macro.energy).reversed();
            case "protein" -> Comparator.comparingDouble((ProductAggregate item) -> item.macro.protein).reversed();
            case "count" -> Comparator.comparingInt((ProductAggregate item) -> item.count).reversed();
            case "days" -> Comparator.comparingInt((ProductAggregate item) -> item.days.size()).reversed();
            default -> Comparator.comparingDouble((ProductAggregate item) -> item.totalAmount).reversed();
        };
    }

    private static Comparator<ProductDayAggregate> productDayComparator(String sort) {
        return switch (safe(sort)) {
            case "date" -> Comparator.comparing(ProductDayAggregate::date).reversed();
            case "calories" -> Comparator.comparingDouble((ProductDayAggregate day) -> day.macro.energy).reversed();
            default -> Comparator.comparingDouble((ProductDayAggregate day) -> day.totalAmount).reversed();
        };
    }

    private static Comparator<MacroAggregate> macroComparator(String sort) {
        return switch (safe(sort)) {
            case "protein" -> Comparator.comparingDouble((MacroAggregate item) -> item.total.protein).reversed();
            case "carbs" -> Comparator.comparingDouble((MacroAggregate item) -> item.total.carbs).reversed();
            case "fat" -> Comparator.comparingDouble((MacroAggregate item) -> item.total.fat).reversed();
            case "count" -> Comparator.comparingInt((MacroAggregate item) -> item.count).reversed();
            default -> Comparator.comparingDouble((MacroAggregate item) -> item.total.energy).reversed();
        };
    }

    private static String productKey(FoodItem item) {
        String raw;
        if (item.productId() != null && !item.productId().isBlank() && item.baseUnit() != null && !item.baseUnit().isBlank()) {
            raw = "product:" + item.productId();
        } else {
            raw = "item:" + normalize(item.name()) + "|" + normalize(item.producer()) + "|" + normalize(item.baseUnit());
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeSearch(String query) {
        String cleaned = query == null ? "" : query.trim();
        while (cleaned.startsWith("*")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith("*")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return normalize(cleaned);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.GERMANY).trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static double itemAmount(FoodItem item) {
        return item.amount() > 0 ? item.amount() : Math.max(1, item.servingQuantity());
    }

    private static String itemUnit(FoodItem item) {
        return item.baseUnit() == null || item.baseUnit().isBlank() ? "Portion" : item.baseUnit();
    }

    private static String amountText(double amount, String unit) {
        return format(amount) + (unit == null || unit.isBlank() ? "" : " " + unit);
    }

    private static final class ProductAggregate {
        private final String key;
        private final String name;
        private final String producer;
        private final String unit;
        private final Macro macro = new Macro();
        private final Set<LocalDate> days = new LinkedHashSet<>();
        private double totalAmount;
        private int count;

        private ProductAggregate(String key, FoodItem item) {
            this.key = key;
            this.name = item.name();
            this.producer = item.producer();
            this.unit = itemUnit(item);
        }

        private void add(LocalDate date, FoodItem item) {
            totalAmount += itemAmount(item);
            macro.add(item.macro());
            days.add(date);
            count++;
        }

        private String label() {
            return producer == null || producer.isBlank() ? name : name + " · " + producer;
        }

        private String searchText() {
            return normalize(name + " " + producer);
        }

        private Map<String, Object> toMap() {
            return Map.of(
                    "key", key,
                    "name", name == null ? "" : name,
                    "producer", producer == null ? "" : producer,
                    "amount", Macro.round(totalAmount),
                    "amountText", amountText(totalAmount, unit),
                    "count", count,
                    "dayCount", days.size(),
                    "macro", macro.toMap()
            );
        }
    }

    private static final class ProductDayAggregate {
        private final LocalDate date;
        private final Macro macro = new Macro();
        private String unit = "Portion";
        private double totalAmount;
        private int count;

        private ProductDayAggregate(LocalDate date) {
            this.date = date;
        }

        private LocalDate date() {
            return date;
        }

        private void add(FoodItem item) {
            totalAmount += itemAmount(item);
            unit = itemUnit(item);
            macro.add(item.macro());
            count++;
        }

        private Map<String, Object> toMap() {
            return Map.of(
                    "date", date.toString(),
                    "amount", Macro.round(totalAmount),
                    "amountText", amountText(totalAmount, unit),
                    "count", count,
                    "macro", macro.toMap()
            );
        }
    }

    private static final class MacroAggregate {
        private final String label;
        private final Macro total = new Macro();
        private int count;

        private MacroAggregate(String label) {
            this.label = label;
        }

        private void add(Macro macro) {
            total.add(macro);
            count++;
        }

        private Map<String, Object> toMap() {
            Macro average = new Macro();
            if (count > 0) {
                average.energy = total.energy / count;
                average.carbs = total.carbs / count;
                average.protein = total.protein / count;
                average.fat = total.fat / count;
                average.sugar = total.sugar / count;
                average.fiber = total.fiber / count;
            }
            return Map.of(
                    "label", label,
                    "count", count,
                    "total", total.toMap(),
                    "average", average.toMap()
            );
        }
    }
}

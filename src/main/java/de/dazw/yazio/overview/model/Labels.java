package de.dazw.yazio.overview.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import de.dazw.yazio.overview.model.Domain.FoodItem;
import de.dazw.yazio.overview.model.Domain.Macro;
import de.dazw.yazio.overview.model.Domain.MealReport;

/**
 * Formatierung und Exportlabels.
 *
 * <p>Die Klasse hält reine Darstellungslogik aus Parsern, Exportern und
 * HTTP-Handlern heraus.</p>
 */
public final class Labels {
    private static final List<String> FOOD_SERVINGS = List.of("bar", "piece", "slice", "bread", "spread");
    private static final List<String> DRINK_SERVINGS = List.of("bottle", "can", "cup", "glass", "drink");
    private static final List<Pattern> DRINK_NAME_PATTERNS = List.of(
            wordPattern("drink"),
            wordPattern("wasser"),
            wordPattern("cola"),
            wordPattern("red\\s*bull"),
            wordPattern("coffee"),
            wordPattern("kaffee"),
            wordPattern("tee"),
            wordPattern("saft"),
            wordPattern("smoothie"),
            wordPattern("shake"),
            wordPattern("milchshake"),
            wordPattern("milkshake"),
            wordPattern("limo"),
            wordPattern("schorle")
    );

    private Labels() {
    }

    public static String mealLabel(String key) {
        return switch (key) {
            case "breakfast" -> "Frühstück";
            case "lunch" -> "Mittagessen";
            case "dinner" -> "Abendessen";
            case "snack" -> "Snack";
            default -> "Sonstiges";
        };
    }

    public static String mealExportLabel(String mealKey) {
        return switch (mealKey) {
            case "breakfast" -> "Frühstück";
            case "lunch" -> "Mittagessen";
            case "dinner" -> "Abendessen";
            default -> "Sonstiges";
        };
    }

    public static String mealMacroBlock(MealReport meal) {
        Macro total = meal.total();
        return mealExportLabel(meal.key()) + "\n"
                + format(total.energy) + " kcal\n"
                + "KH " + format(total.carbs) + " g\n"
                + "Protein " + format(total.protein) + " g\n"
                + "Fett " + format(total.fat) + " g";
    }

    public static String lineValue(String value, int underscores) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return "_".repeat(Math.max(4, underscores));
    }

    public static String dateLineValue(String value, int underscores) {
        if (value != null && !value.isBlank()) {
            try {
                return LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            } catch (RuntimeException ignored) {
                return value;
            }
        }
        return "_".repeat(Math.max(4, underscores));
    }

    public static boolean isDrink(FoodItem item) {
        String unit = item.baseUnit() == null ? "" : item.baseUnit().toLowerCase(Locale.ROOT);
        String serving = item.serving() == null ? "" : item.serving().toLowerCase(Locale.ROOT);
        String name = item.name() == null ? "" : item.name().toLowerCase(Locale.ROOT);

        // Klare Essens-Portionen gewinnen vor generischen Namenssignalen.
        if (containsServingToken(serving, FOOD_SERVINGS)) {
            return false;
        }
        return unit.equals("ml")
                || containsServingToken(serving, DRINK_SERVINGS)
                || containsDrinkName(name);
    }

    private static boolean containsServingToken(String serving, List<String> tokens) {
        return tokens.stream().anyMatch(token -> wordPattern(token).matcher(serving).find());
    }

    private static boolean containsDrinkName(String name) {
        return DRINK_NAME_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(name).find());
    }

    private static Pattern wordPattern(String expression) {
        return Pattern.compile("(^|[^a-z0-9äöüß])" + expression + "([^a-z0-9äöüß]|$)");
    }

    public static String itemLine(FoodItem item) {
        return item.name() + " (" + item.amountLabel() + ")";
    }

    public static String joinItems(MealReport meal, boolean drinks) {
        List<String> lines = meal.items().stream()
                .filter(item -> isDrink(item) == drinks)
                .map(Labels::itemLine)
                .toList();
        return String.join("\n", lines);
    }

    public static Map<String, MealReport> exportMeals(List<MealReport> meals) {
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

    public static String amountLabel(double amount, String baseUnit, String serving, double servingQuantity) {
        String label = format(amount) + (baseUnit == null ? "" : " " + baseUnit);
        if (serving != null && !serving.isBlank()) {
            label += " (" + format(servingQuantity) + " x " + serving + ")";
        }
        return label;
    }

    public static String format(double value) {
        double rounded = round(value);
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001) {
            return String.valueOf((long) rounded);
        }
        return String.format(Locale.GERMANY, "%.1f", rounded);
    }

    public static double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}

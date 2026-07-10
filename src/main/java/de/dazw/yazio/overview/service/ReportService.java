package de.dazw.yazio.overview.service;

import de.dazw.yazio.overview.model.Domain.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static de.dazw.yazio.overview.model.Labels.amountLabel;
import static de.dazw.yazio.overview.model.Labels.detectedDrink;

/**
 * Erzeugt aus den gespeicherten Yazio-Rohdaten die fachliche Tagesauswertung.
 *
 * <p>Hier findet die Verbindung zwischen Tageseinträgen und Produktkatalog
 * statt. Die HTTP-Schicht muss dadurch nichts über Yazio-Details wissen.</p>
 */
public final class ReportService {
    public DayReport buildDayReport(LocalDate date, DataStore snapshot) {
        Day day = snapshot.days().get(date);
        if (day == null) {
            return null;
        }
        Map<String, MealReport> meals = new java.util.LinkedHashMap<>();
        List<ConsumedProduct> entries = new ArrayList<>(day.products());
        entries.sort(Comparator.comparingInt((ConsumedProduct item) -> mealOrder(item.daytime()))
                .thenComparing(ConsumedProduct::date, Comparator.nullsLast(String::compareTo)));

        for (ConsumedProduct entry : entries) {
            Product product = snapshot.products().get(entry.productId());
            Macro macro = macroFor(product, entry.amount());
            String mealKey = normalizeMeal(entry.daytime());
            MealReport meal = meals.computeIfAbsent(mealKey, MealReport::new);
            FoodItem item = new FoodItem(
                    entry.id(),
                    product == null ? "(Unbekanntes Produkt)" : product.name(),
                    product == null ? null : product.producer(),
                    entry.amount(),
                    product == null ? null : product.baseUnit(),
                    entry.serving(),
                    entry.servingQuantity(),
                    entry.productId(),
                    amountLabel(entry.amount(), product == null ? null : product.baseUnit(), entry.serving(), entry.servingQuantity()),
                    false,
                    macro,
                    false,
                    "food"
            );
            meal.items().add(applyClassification(item, snapshot.itemClassifications()));
            meal.total().add(macro);
        }

        List<SimpleProduct> simpleProducts = new ArrayList<>(day.simpleProducts());
        simpleProducts.sort(Comparator.comparingInt((SimpleProduct item) -> mealOrder(item.daytime()))
                .thenComparing(SimpleProduct::date, Comparator.nullsLast(String::compareTo)));
        for (SimpleProduct entry : simpleProducts) {
            Macro macro = macroFor(entry.nutrients());
            String mealKey = normalizeMeal(entry.daytime());
            MealReport meal = meals.computeIfAbsent(mealKey, MealReport::new);
            FoodItem item = new FoodItem(
                    entry.id(),
                    entry.name(),
                    entry.aiGenerated() ? "KI erfasst" : null,
                    0,
                    null,
                    "simple_product",
                    1,
                    entry.id(),
                    entry.aiGenerated() ? "KI erfasste Mahlzeit" : "Einfache Mahlzeit",
                    entry.aiGenerated(),
                    macro,
                    false,
                    "food"
            );
            meal.items().add(applyClassification(item, snapshot.itemClassifications()));
            meal.total().add(macro);
        }

        Macro total = new Macro();
        meals.values().forEach(meal -> total.add(meal.total()));
        List<MealReport> mealReports = new ArrayList<>(meals.values());
        mealReports.sort(Comparator.comparingInt(meal -> mealOrder(meal.key())));
        return new DayReport(date, day.daily(), mealReports, total, snapshot.settings(),
                snapshot.notes().getOrDefault(date, ""),
                snapshot.sportNotes().getOrDefault(date, ""),
                day.exercises());
    }

    private static FoodItem applyClassification(FoodItem item, Map<String, String> overrides) {
        boolean automaticDrink = detectedDrink(item);
        String automatic = automaticDrink ? "drink" : "food";
        String itemOverride = itemClassification(overrides, item.itemId());
        String productOverride = itemClassification(overrides, productKey(item.productId()));
        String classification = firstClassification(itemOverride, productOverride, automatic);
        return new FoodItem(
                item.itemId(),
                item.name(),
                item.producer(),
                item.amount(),
                item.baseUnit(),
                item.serving(),
                item.servingQuantity(),
                item.productId(),
                item.amountLabel(),
                item.aiGenerated(),
                item.macro(),
                automaticDrink,
                classification
        );
    }

    private static String itemClassification(Map<String, String> overrides, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String value = overrides.get(key);
        return "drink".equals(value) || "food".equals(value) ? value : null;
    }

    private static String productKey(String productId) {
        return productId == null || productId.isBlank() ? null : "product:" + productId;
    }

    private static String firstClassification(String itemOverride, String productOverride, String automatic) {
        if (itemOverride != null) {
            return itemOverride;
        }
        if (productOverride != null) {
            return productOverride;
        }
        return automatic;
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
}

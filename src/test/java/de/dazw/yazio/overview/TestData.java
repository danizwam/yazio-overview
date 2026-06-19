package de.dazw.yazio.overview;

import de.dazw.yazio.overview.model.Domain.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TestData {
    private static final LocalDate DAY = LocalDate.of(2026, 6, 1);

    private TestData() {
    }

    public static DataStore dataStore() {
        Map<String, Product> products = new LinkedHashMap<>();
        products.put("skyr", product("skyr", "Skyr Natur", "Molkerei", "g",
                0.64, 0.11, 0.04, 0.002, "gram"));
        products.put("shake", product("shake", "Protein Milkshake", "Sports", "ml",
                0.74, 0.07, 0.06, 0.015, "bottle"));
        products.put("bar", product("bar", "Proteinriegel", "Sports", "g",
                3.80, 0.33, 0.32, 0.12, "bar"));

        Day day = new Day(
                DAY,
                new Daily(0, 0, 0, 0, 2200),
                List.of(
                        consumed("entry-skyr", "breakfast", "skyr", 200, "gram", 200),
                        consumed("entry-shake", "snack", "shake", 330, "bottle", 1),
                        consumed("entry-bar", "snack", "bar", 45, "bar", 1)
                ),
                List.of(new SimpleProduct(
                        "simple-lunch",
                        DAY + " 12:00:00",
                        "lunch",
                        "simple_product",
                        "Haehnchen mit Reis",
                        nutrients(720, 55, 55, 35),
                        true
                ))
        );
        return new DataStore(products, Map.of(DAY, day), AppSettings.empty(),
                Map.of(DAY, "Testnotiz"), Map.of(DAY, "45 Minuten Krafttraining"), Map.of());
    }

    public static DataStore dataStoreWithProductOverride(String productId, String classification) {
        DataStore base = dataStore();
        return new DataStore(base.products(), base.days(), base.settings(), base.notes(), base.sportNotes(),
                Map.of("product:" + productId, classification));
    }

    public static LocalDate day() {
        return DAY;
    }

    private static Product product(String id, String name, String producer, String baseUnit,
                                   double energy, double protein, double carbs, double fat,
                                   String serving) {
        return new Product(id, name, producer, baseUnit, nutrients(energy, protein, carbs, fat),
                List.of(new Serving(serving, 1)));
    }

    private static ConsumedProduct consumed(String id, String meal, String productId, double amount,
                                            String serving, double servingQuantity) {
        return new ConsumedProduct(id, DAY + " 08:00:00", meal, "product", productId,
                amount, serving, servingQuantity);
    }

    private static Map<String, Double> nutrients(double energy, double protein, double carbs, double fat) {
        Map<String, Double> nutrients = new LinkedHashMap<>();
        nutrients.put("energy.energy", energy);
        nutrients.put("nutrient.protein", protein);
        nutrients.put("nutrient.carb", carbs);
        nutrients.put("nutrient.fat", fat);
        nutrients.put("nutrient.sugar", 0.0);
        nutrients.put("nutrient.dietaryfiber", 0.0);
        return nutrients;
    }
}

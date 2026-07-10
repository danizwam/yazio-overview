package de.dazw.yazio.overview.demo;

import de.dazw.yazio.overview.model.Domain.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Erzeugt realistisch wirkende Demo-Daten ohne Zugriff auf die Yazio-API.
 *
 * <p>Die Produktwerte sind bewusst im Code enthalten, damit der Demo-Modus
 * ohne vorhandene products.json oder days.json funktioniert.</p>
 */
public final class DemoDataFactory {
    private static final AppSettings DEFAULT_SETTINGS = new AppSettings("Demo Benutzer", "1990-01-01", "demo@yazio.local", "");
    private static final List<DemoFood> FOODS = List.of(
            food("demo-skyr", "Skyr Natur", "Demo Molkerei", 0.64, 0.11, 0.04, 0.002),
            food("demo-quark", "Magerquark", "Demo Molkerei", 0.68, 0.12, 0.04, 0.002),
            food("demo-greek-yogurt", "Griechischer Joghurt 2%", "Demo Molkerei", 0.73, 0.09, 0.04, 0.02),
            food("demo-oats", "Haferflocken", "Demo Korn", 3.72, 0.13, 0.60, 0.07),
            food("demo-protein-powder", "Whey Protein Vanille", "Demo Sports", 3.85, 0.78, 0.08, 0.06),
            food("demo-banana", "Banane", "Demo Obst", 0.89, 0.011, 0.23, 0.003),
            food("demo-blueberries", "Blaubeeren", "Demo Obst", 0.57, 0.007, 0.14, 0.003),
            food("demo-strawberries", "Erdbeeren", "Demo Obst", 0.32, 0.007, 0.08, 0.003),
            food("demo-almonds", "Mandeln", "Demo Nuss", 5.76, 0.21, 0.22, 0.49),
            food("demo-peanut-butter", "Erdnussmus", "Demo Nuss", 5.88, 0.25, 0.20, 0.50),
            food("demo-wholegrain-bread", "Vollkornbrot", "Demo Baeckerei", 2.25, 0.08, 0.42, 0.03),
            food("demo-cream-cheese", "Frischkaese leicht", "Demo Molkerei", 1.40, 0.10, 0.05, 0.08),
            food("demo-turkey", "Putenbrust Aufschnitt", "Demo Metzgerei", 1.10, 0.23, 0.01, 0.02),
            food("demo-eggs", "Ruehrei", "Demo Hof", 1.43, 0.13, 0.01, 0.10),
            food("demo-chicken", "Haehnchenbrust", "Demo Gefluegel", 1.65, 0.31, 0.00, 0.04),
            food("demo-salmon", "Lachsfilet", "Demo Fisch", 2.08, 0.20, 0.00, 0.13),
            food("demo-lean-beef", "Rinderhack fettarm", "Demo Metzgerei", 1.80, 0.26, 0.00, 0.08),
            food("demo-tofu", "Tofu Natur", "Demo Vegan", 1.25, 0.15, 0.02, 0.07),
            food("demo-lentils", "Linsen gekocht", "Demo Huelse", 1.16, 0.09, 0.20, 0.004),
            food("demo-kidney-beans", "Kidneybohnen", "Demo Huelse", 1.27, 0.087, 0.23, 0.005),
            food("demo-rice", "Basmatireis gekocht", "Demo Korn", 1.30, 0.027, 0.28, 0.003),
            food("demo-potatoes", "Kartoffeln", "Demo Feld", 0.77, 0.02, 0.17, 0.001),
            food("demo-pasta", "Vollkornnudeln gekocht", "Demo Pasta", 1.48, 0.06, 0.27, 0.015),
            food("demo-quinoa", "Quinoa gekocht", "Demo Korn", 1.20, 0.044, 0.21, 0.019),
            food("demo-broccoli", "Brokkoli", "Demo Gemuese", 0.34, 0.028, 0.07, 0.004),
            food("demo-peppers", "Paprika", "Demo Gemuese", 0.31, 0.01, 0.06, 0.003),
            food("demo-spinach", "Blattspinat", "Demo Gemuese", 0.23, 0.029, 0.036, 0.004),
            food("demo-tomatoes", "Tomaten", "Demo Gemuese", 0.18, 0.009, 0.039, 0.002),
            food("demo-avocado", "Avocado", "Demo Obst", 1.60, 0.02, 0.09, 0.15),
            food("demo-olive-oil", "Olivenoel", "Demo Oel", 8.84, 0.00, 0.00, 1.00),
            food("demo-cottage-cheese", "Huettenkaese", "Demo Molkerei", 0.98, 0.11, 0.034, 0.043),
            food("demo-protein-bar", "Proteinriegel Schoko", "Demo Sports", 3.80, 0.33, 0.32, 0.12),
            food("demo-milkshake", "Protein Milkshake Erdbeere", "Demo Drinks", 0.74, 0.07, 0.06, 0.015),
            food("demo-apple", "Apfel", "Demo Obst", 0.52, 0.003, 0.14, 0.002),
            food("demo-crispbread", "Knackebrot", "Demo Baeckerei", 3.50, 0.10, 0.65, 0.03),
            food("demo-hummus", "Hummus", "Demo Vegan", 2.40, 0.08, 0.14, 0.17)
    );

    private DemoDataFactory() {
    }

    public static DataStore emptyStore() {
        return new DataStore(Map.of(), Map.of(), DEFAULT_SETTINGS, Map.of(), Map.of());
    }

    public static DataStore generate(LocalDate from, LocalDate to, AppSettings currentSettings) {
        Map<String, Product> products = new LinkedHashMap<>();
        FOODS.forEach(food -> products.put(food.id(), food.toProduct()));
        Map<LocalDate, Day> days = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            days.put(date, day(date));
        }
        AppSettings settings = currentSettings == null ? DEFAULT_SETTINGS : currentSettings;
        if (settings.name() == null || settings.name().isBlank()) {
            settings = new AppSettings(DEFAULT_SETTINGS.name(), settings.birthDate(), settings.username(),
                    settings.passwordBase64(), settings.defaultRangeDays());
        }
        return new DataStore(products, days, settings, Map.of(), Map.of());
    }

    private static Day day(LocalDate date) {
        Random random = new Random(date.toEpochDay());
        List<ConsumedProduct> entries = new ArrayList<>();
        addMeal(entries, date, "breakfast", random,
                pick(random, "demo-skyr", "demo-quark", "demo-greek-yogurt"), 180 + random.nextInt(50),
                pick(random, "demo-oats", "demo-wholegrain-bread", "demo-crispbread"), 65 + random.nextInt(25),
                pick(random, "demo-blueberries", "demo-strawberries", "demo-banana", "demo-apple"), 90 + random.nextInt(70),
                pick(random, "demo-almonds", "demo-peanut-butter"), 12 + random.nextInt(10));
        addMeal(entries, date, "lunch", random,
                pick(random, "demo-chicken", "demo-turkey", "demo-tofu", "demo-lentils"), 95 + random.nextInt(55),
                pick(random, "demo-rice", "demo-potatoes", "demo-pasta", "demo-quinoa"), 230 + random.nextInt(90),
                pick(random, "demo-broccoli", "demo-peppers", "demo-spinach", "demo-tomatoes"), 120 + random.nextInt(100),
                "demo-olive-oil", 15 + random.nextInt(8));
        addMeal(entries, date, "dinner", random,
                pick(random, "demo-salmon", "demo-lean-beef", "demo-chicken", "demo-kidney-beans"), 95 + random.nextInt(55),
                pick(random, "demo-potatoes", "demo-rice", "demo-quinoa", "demo-pasta"), 230 + random.nextInt(80),
                pick(random, "demo-broccoli", "demo-peppers", "demo-avocado", "demo-tomatoes"), 100 + random.nextInt(90),
                pick(random, "demo-hummus", "demo-cream-cheese", "demo-cottage-cheese"), 35 + random.nextInt(35));
        addMeal(entries, date, "snack", random,
                pick(random, "demo-protein-powder", "demo-protein-bar", "demo-milkshake", "demo-cottage-cheese"), 35 + random.nextInt(25),
                pick(random, "demo-banana", "demo-apple", "demo-strawberries"), 80 + random.nextInt(70));
        Macro total = total(entries);
        Daily daily = new Daily(total.energy, total.carbs, total.protein, total.fat, 2200);
        ExerciseSummary exercises = new ExerciseSummary(
                120 + random.nextInt(260),
                random.nextInt(4) == 0 ? 80 + random.nextInt(180) : 0,
                0,
                3500 + random.nextInt(6500)
        );
        return new Day(date, daily, entries, List.of(), exercises);
    }

    private static void addMeal(List<ConsumedProduct> entries, LocalDate date, String meal, Random random, Object... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            String productId = String.valueOf(pairs[i]);
            double amount = ((Number) pairs[i + 1]).doubleValue();
            String id = "demo-" + date + "-" + meal + "-" + i + "-" + Math.abs(random.nextInt());
            entries.add(new ConsumedProduct(id, date + " 12:00:00", meal, "product", productId, amount, "gram", amount));
        }
    }

    private static String pick(Random random, String... ids) {
        return ids[random.nextInt(ids.length)];
    }

    private static Macro total(List<ConsumedProduct> entries) {
        Macro total = new Macro();
        Map<String, DemoFood> foods = new LinkedHashMap<>();
        FOODS.forEach(food -> foods.put(food.id(), food));
        for (ConsumedProduct entry : entries) {
            DemoFood food = foods.get(entry.productId());
            total.energy += food.energy() * entry.amount();
            total.protein += food.protein() * entry.amount();
            total.carbs += food.carbs() * entry.amount();
            total.fat += food.fat() * entry.amount();
        }
        return total;
    }

    private static DemoFood food(String id, String name, String producer, double energy, double protein, double carbs, double fat) {
        return new DemoFood(id, name, producer, energy, protein, carbs, fat);
    }

    private record DemoFood(String id, String name, String producer, double energy, double protein, double carbs, double fat) {
        private Product toProduct() {
            Map<String, Double> nutrients = new LinkedHashMap<>();
            nutrients.put("energy.energy", energy);
            nutrients.put("nutrient.protein", protein);
            nutrients.put("nutrient.carb", carbs);
            nutrients.put("nutrient.fat", fat);
            nutrients.put("nutrient.sugar", 0.02);
            nutrients.put("nutrient.dietaryfiber", 0.02);
            return new Product(id, name, producer, "g", nutrients, List.of(new Serving("gram", 1)));
        }
    }
}

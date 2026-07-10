package de.dazw.yazio.overview.service;

import de.dazw.yazio.overview.TestData;
import de.dazw.yazio.overview.model.Domain.DayReport;
import de.dazw.yazio.overview.model.Domain.FoodItem;
import de.dazw.yazio.overview.model.Domain.MealReport;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {
    private final ReportService reportService = new ReportService();

    @Test
    void buildsMealsWithProductsAndSimpleAiMeals() {
        DayReport report = reportService.buildDayReport(TestData.day(), TestData.dataStore());

        assertNotNull(report);
        assertEquals("Testnotiz", report.note());
        assertEquals("45 Minuten Krafttraining", report.sportNote());
        assertEquals(3, report.meals().size());
        assertEquals("breakfast", report.meals().get(0).key());

        Map<String, MealReport> meals = report.meals().stream()
                .collect(Collectors.toMap(MealReport::key, meal -> meal));
        assertEquals(128.0, meals.get("breakfast").total().energy, 0.0001);
        assertEquals(22.0, meals.get("breakfast").total().protein, 0.0001);
        assertEquals("Haehnchen mit Reis", meals.get("lunch").items().get(0).name());
        assertTrue(meals.get("lunch").items().get(0).aiGenerated());
        assertEquals(1263.2, report.total().energy, 0.0001);
        assertEquals(175.0, report.burnedEnergy(), 0.0001);
        assertEquals(1088.2, report.netEnergy(), 0.0001);
    }

    @Test
    void servingBarWinsOverProteinNameAndStaysFood() {
        DayReport report = reportService.buildDayReport(TestData.day(), TestData.dataStore());
        FoodItem bar = item(report, "entry-bar");

        assertFalse(bar.automaticDrink());
        assertEquals("food", bar.classification());
    }

    @Test
    void productOverrideChangesClassification() {
        DayReport report = reportService.buildDayReport(TestData.day(),
                TestData.dataStoreWithProductOverride("bar", "drink"));
        FoodItem bar = item(report, "entry-bar");

        assertFalse(bar.automaticDrink());
        assertEquals("drink", bar.classification());
    }

    @Test
    void missingDayReturnsNull() {
        assertNull(reportService.buildDayReport(TestData.day().plusDays(1), TestData.dataStore()));
    }

    private static FoodItem item(DayReport report, String itemId) {
        return report.meals().stream()
                .flatMap(meal -> meal.items().stream())
                .filter(item -> item.itemId().equals(itemId))
                .findFirst()
                .orElseThrow();
    }
}

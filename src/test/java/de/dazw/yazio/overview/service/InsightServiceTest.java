package de.dazw.yazio.overview.service;

import de.dazw.yazio.overview.TestData;
import de.dazw.yazio.overview.model.Domain.DayReport;
import de.dazw.yazio.overview.model.Domain.Macro;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InsightServiceTest {
    private final ReportService reportService = new ReportService();
    private final InsightService insightService = new InsightService();

    @Test
    void productSearchIgnoresCaseAndWildcardAsterisks() {
        List<DayReport> reports = List.of(report());

        List<Map<String, Object>> result = insightService.products(reports, "*sKyR*", "amount", 100);

        assertEquals(1, result.size());
        assertEquals("Skyr Natur", result.getFirst().get("name"));
        assertEquals("200 g", result.getFirst().get("amountText"));
    }

    @Test
    void productsCanBeSortedByAmount() {
        List<Map<String, Object>> result = insightService.products(List.of(report()), "", "amount", 100);

        assertEquals("Protein Milkshake", result.getFirst().get("name"));
    }

    @Test
    void productDaysAggregatesSelectedProduct() {
        List<Map<String, Object>> products = insightService.products(List.of(report()), "*shake*", "amount", 100);
        String key = String.valueOf(products.getFirst().get("key"));

        List<Map<String, Object>> days = insightService.productDays(List.of(report()), key, "amount");

        assertEquals(1, days.size());
        assertEquals(TestData.day().toString(), days.getFirst().get("date"));
        assertEquals("330 ml", days.getFirst().get("amountText"));
    }

    @Test
    void daysCanBeSortedByCaloriesDescending() {
        DayReport first = report();
        Macro smallTotal = new Macro();
        smallTotal.energy = 50;
        DayReport second = new DayReport(
                LocalDate.of(2026, 6, 2),
                first.daily(),
                first.meals(),
                smallTotal,
                first.settings(),
                ""
        );

        List<Map<String, Object>> result = insightService.days(List.of(second, first), "energy", "desc");

        assertEquals(TestData.day().toString(), result.getFirst().get("date"));
    }

    @Test
    void productKeysAreStableBase64Values() {
        List<Map<String, Object>> products = insightService.products(List.of(report()), "*skyr*", "amount", 100);
        String key = String.valueOf(products.getFirst().get("key"));

        String decoded = new String(Base64.getUrlDecoder().decode(key), StandardCharsets.UTF_8);

        assertEquals("product:skyr", decoded);
    }

    private DayReport report() {
        return reportService.buildDayReport(TestData.day(), TestData.dataStore());
    }
}

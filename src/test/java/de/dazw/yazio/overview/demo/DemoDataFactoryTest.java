package de.dazw.yazio.overview.demo;

import de.dazw.yazio.overview.model.Domain.AppSettings;
import de.dazw.yazio.overview.model.Domain.DataStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DemoDataFactoryTest {
    @Test
    void emptyStoreContainsDemoSettingsButNoFoodData() {
        DataStore store = DemoDataFactory.emptyStore();

        assertTrue(store.products().isEmpty());
        assertTrue(store.days().isEmpty());
        assertEquals("Demo Benutzer", store.settings().name());
    }

    @Test
    void generateCreatesProductsAndOneDayPerDate() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 3);

        DataStore store = DemoDataFactory.generate(from, to, AppSettings.empty());

        assertTrue(store.products().size() >= 30);
        assertEquals(3, store.days().size());
        assertTrue(store.days().containsKey(from));
        assertTrue(store.days().containsKey(to));
    }

    @Test
    void generateKeepsExistingPersonalSettings() {
        AppSettings settings = new AppSettings("Daniel", "1979-01-28", "demo@example.test", "");

        DataStore store = DemoDataFactory.generate(LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 1), settings);

        assertEquals("Daniel", store.settings().name());
        assertEquals("1979-01-28", store.settings().birthDate());
    }

    @Test
    void generatedMacrosAreInExpectedDemoRange() {
        DataStore store = DemoDataFactory.generate(LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 1), AppSettings.empty());

        double energy = store.days().values().iterator().next().daily().energy();
        double protein = store.days().values().iterator().next().daily().protein();

        assertTrue(energy > 1200 && energy < 2600, "Demo-Kalorien sollen realistisch bleiben");
        assertTrue(protein > 70 && protein < 180, "Demo-Protein soll realistisch bleiben");
    }
}

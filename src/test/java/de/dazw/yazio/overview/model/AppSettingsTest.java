package de.dazw.yazio.overview.model;

import de.dazw.yazio.overview.model.Domain.AppSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppSettingsTest {
    @Test
    void defaultRangeDaysUseSevenDaysWhenMissingOrInvalid() {
        assertEquals(7, AppSettings.empty().defaultRangeDays());
        assertEquals(7, new AppSettings("", "", "", "", 0).defaultRangeDays());
        assertEquals(7, new AppSettings("", "", "", "", -5).defaultRangeDays());
    }

    @Test
    void defaultRangeDaysAreLimitedToOneYear() {
        assertEquals(365, new AppSettings("", "", "", "", 999).defaultRangeDays());
        assertEquals(14, new AppSettings("", "", "", "", 14).defaultRangeDays());
    }
}

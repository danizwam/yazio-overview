package de.dazw.yazio.overview.model;

import de.dazw.yazio.overview.model.Domain.FoodItem;
import de.dazw.yazio.overview.model.Domain.Macro;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LabelsTest {
    @Test
    void amountLabelIncludesServingInformation() {
        assertEquals("45 g (1 x bar)", Labels.amountLabel(45, "g", "bar", 1));
        assertEquals("330 ml (1 x bottle)", Labels.amountLabel(330, "ml", "bottle", 1));
    }

    @Test
    void dateLineValueFormatsIsoDateForExports() {
        assertEquals("28.01.1979", Labels.dateLineValue("1979-01-28", 10));
    }

    @Test
    void proteinBarServingIsDetectedAsFood() {
        FoodItem item = item("Proteinriegel Schoko", "g", "bar", "food");

        assertFalse(Labels.detectedDrink(item));
        assertFalse(Labels.isDrink(item));
    }

    @Test
    void proteinShakeNameCanBeDetectedAsDrinkEvenWithGramUnit() {
        FoodItem item = item("Strawberry Sundae Whey", "g", "gram", "drink");

        assertTrue(Labels.detectedDrink(item));
        assertTrue(Labels.isDrink(item));
    }

    private static FoodItem item(String name, String baseUnit, String serving, String classification) {
        return new FoodItem(
                "item-1",
                name,
                "Test",
                45,
                baseUnit,
                serving,
                1,
                "product-1",
                Labels.amountLabel(45, baseUnit, serving, 1),
                false,
                new Macro(),
                false,
                classification
        );
    }
}

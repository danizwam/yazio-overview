package de.dazw.yazio.overview.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonParserWriterTest {
    @Test
    void parserReadsNestedJsonAndEscapes() {
        Object parsed = new JsonParser("""
                {
                  "name": "Skyr \\\"Natur\\\"",
                  "amount": 200.5,
                  "active": true,
                  "items": ["a", null, 3]
                }
                """).parse();

        assertInstanceOf(Map.class, parsed);
        Map<?, ?> root = (Map<?, ?>) parsed;
        assertEquals("Skyr \"Natur\"", root.get("name"));
        assertEquals(200.5, (Double) root.get("amount"), 0.0001);
        assertEquals(Boolean.TRUE, root.get("active"));
        List<?> items = (List<?>) root.get("items");
        assertEquals("a", items.get(0));
        assertNull(items.get(1));
        assertEquals(3.0, (Double) items.get(2), 0.0001);
    }

    @Test
    void writerOutputCanBeParsedAgain() {
        Map<String, Object> original = Map.of(
                "name", "Proteinriegel",
                "values", List.of(1, 2.5, "bar"),
                "flag", true
        );

        Object parsedAgain = new JsonParser(JsonWriter.write(original)).parse();

        assertInstanceOf(Map.class, parsedAgain);
        Map<?, ?> root = (Map<?, ?>) parsedAgain;
        assertEquals("Proteinriegel", root.get("name"));
        assertEquals(List.of(1.0, 2.5, "bar"), root.get("values"));
        assertEquals(Boolean.TRUE, root.get("flag"));
    }

    @Test
    void parserRejectsInvalidJson() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new JsonParser("{\"broken\": }").parse());

        assertTrue(error.getMessage().contains("Position"));
    }
}

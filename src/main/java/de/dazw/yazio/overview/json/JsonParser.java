package de.dazw.yazio.overview.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Kleiner JSON-Parser für Yazio-Exports und lokale Konfiguration.
 *
 * <p>Er bildet JSON-Objekte auf {@code Map<String, Object>} und Arrays auf
 * {@code List<Object>} ab. Typisierung passiert anschließend in den Parsern der
 * Fachlogik.</p>
 */
public final class JsonParser {
    private final String text;
    private int position;

    public JsonParser(String text) {
        this.text = Objects.requireNonNull(text);
    }

    public Object parse() {
        Object value = value();
        whitespace();
        if (position != text.length()) {
            throw error("Unerwartete Zeichen");
        }
        return value;
    }

    private Object value() {
        whitespace();
        if (position >= text.length()) {
            throw error("Unerwartetes Ende");
        }
        char c = text.charAt(position);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        whitespace();
        if (peek('}')) {
            position++;
            return map;
        }
        while (true) {
            String key = string();
            whitespace();
            expect(':');
            map.put(key, value());
            whitespace();
            if (peek('}')) {
                position++;
                return map;
            }
            expect(',');
        }
    }

    private List<Object> array() {
        expect('[');
        List<Object> list = new ArrayList<>();
        whitespace();
        if (peek(']')) {
            position++;
            return list;
        }
        while (true) {
            list.add(value());
            whitespace();
            if (peek(']')) {
                position++;
                return list;
            }
            expect(',');
        }
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (position < text.length()) {
            char c = text.charAt(position++);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                if (position >= text.length()) {
                    throw error("Unvollständige Escape-Sequenz");
                }
                char escaped = text.charAt(position++);
                switch (escaped) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (position + 4 > text.length()) {
                            throw error("Unvollständige Unicode-Escape-Sequenz");
                        }
                        out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default -> throw error("Ungültige Escape-Sequenz");
                }
            } else {
                out.append(c);
            }
        }
        throw error("Nicht abgeschlossener String");
    }

    private Object number() {
        int start = position;
        if (peek('-')) {
            position++;
        }
        while (position < text.length() && Character.isDigit(text.charAt(position))) {
            position++;
        }
        if (peek('.')) {
            position++;
            while (position < text.length() && Character.isDigit(text.charAt(position))) {
                position++;
            }
        }
        if (position < text.length() && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
            position++;
            if (peek('+') || peek('-')) {
                position++;
            }
            while (position < text.length() && Character.isDigit(text.charAt(position))) {
                position++;
            }
        }
        if (start == position) {
            throw error("Zahl erwartet");
        }
        return Double.parseDouble(text.substring(start, position));
    }

    private Object literal(String literal, Object value) {
        if (text.startsWith(literal, position)) {
            position += literal.length();
            return value;
        }
        throw error("Literal erwartet: " + literal);
    }

    private void whitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private void expect(char expected) {
        whitespace();
        if (position >= text.length() || text.charAt(position) != expected) {
            throw error("'" + expected + "' erwartet");
        }
        position++;
    }

    private boolean peek(char c) {
        return position < text.length() && text.charAt(position) == c;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " bei Position " + position);
    }
}

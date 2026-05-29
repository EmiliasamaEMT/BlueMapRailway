package io.github.emiliasamaemt.bluemaprailway.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimpleJson {

    private SimpleJson() {
    }

    public static Object parse(String input) {
        return new Parser(input).parse();
    }

    public static String string(String value) {
        if (value == null) {
            return "null";
        }

        StringBuilder builder = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }

        return builder.append('"').toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    result.put(key, entry.getValue());
                }
            }
            return result;
        }

        return Map.of();
    }

    public static String text(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value instanceof String text ? text : fallback;
    }

    public static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public static double decimal(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    public static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    public static List<String> stringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String text) {
                result.add(text);
            }
        }

        return result;
    }

    private static final class Parser {

        private final String input;
        private int index;

        private Parser(String input) {
            this.input = input == null ? "" : input;
        }

        private Object parse() {
            Object value = readValue();
            skipWhitespace();
            if (index != input.length()) {
                throw new IllegalArgumentException("Unexpected JSON trailing content");
            }
            return value;
        }

        private Object readValue() {
            skipWhitespace();
            if (index >= input.length()) {
                throw new IllegalArgumentException("Unexpected JSON end");
            }

            char character = input.charAt(index);
            return switch (character) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }

            while (true) {
                String key = readString();
                skipWhitespace();
                expect(':');
                result.put(key, readValue());
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }

            while (true) {
                result.add(readValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < input.length()) {
                char character = input.charAt(index++);
                if (character == '"') {
                    return builder.toString();
                }

                if (character != '\\') {
                    builder.append(character);
                    continue;
                }

                if (index >= input.length()) {
                    throw new IllegalArgumentException("Invalid JSON escape");
                }

                char escape = input.charAt(index++);
                switch (escape) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> {
                        if (index + 4 > input.length()) {
                            throw new IllegalArgumentException("Invalid JSON unicode escape");
                        }
                        String hex = input.substring(index, index + 4);
                        builder.append((char) Integer.parseInt(hex, 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                }
            }

            throw new IllegalArgumentException("Unclosed JSON string");
        }

        private Object readNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
            if (peek('.')) {
                index++;
                while (index < input.length() && Character.isDigit(input.charAt(index))) {
                    index++;
                }
            }
            if (peek('e') || peek('E')) {
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                while (index < input.length() && Character.isDigit(input.charAt(index))) {
                    index++;
                }
            }

            String value = input.substring(start, index);
            if (value.contains(".") || value.contains("e") || value.contains("E")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        }

        private Object readLiteral(String literal, Object value) {
            if (!input.startsWith(literal, index)) {
                throw new IllegalArgumentException("Invalid JSON literal");
            }
            index += literal.length();
            return value;
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private boolean peek(char character) {
            return index < input.length() && input.charAt(index) == character;
        }

        private void expect(char character) {
            skipWhitespace();
            if (!peek(character)) {
                throw new IllegalArgumentException("Expected JSON character: " + character);
            }
            index++;
        }
    }
}

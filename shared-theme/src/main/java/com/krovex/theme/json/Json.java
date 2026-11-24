package com.krovex.theme.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately small, dependency-free JSON reader/writer.
 *
 * Why this exists instead of Gson/Jackson: this module is shared verbatim between
 * the launcher (a plain JDK app with no dependency resolution available in this
 * build environment) and the mod (which runs inside Minecraft, where Gson is present
 * but relocated/shaded differently across Minecraft and Fabric Loader versions).
 * Depending on either would either fail to compile standalone or silently break
 * when the host game updates its bundled Gson. This class supports exactly the
 * JSON subset Krovex's own config/theme/profile files need: objects, arrays,
 * strings, numbers (as double or long), booleans, and null. It is not a general
 * purpose JSON library and makes no attempt to be one.
 */
public final class Json {

    private Json() {}

    // ---------------------------------------------------------------- parsing

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.parseValue();
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw new JsonException("Unexpected trailing content at offset " + p.pos);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new JsonException("Expected a JSON object at top level");
        return (Map<String, Object>) v;
    }

    public static final class JsonException extends RuntimeException {
        public JsonException(String message) { super(message); }
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) { this.s = s; this.pos = 0; }

        boolean atEnd() { return pos >= s.length(); }

        char peek() {
            if (atEnd()) throw new JsonException("Unexpected end of input");
            return s.charAt(pos);
        }

        char next() { return s.charAt(pos++); }

        void skipWhitespace() {
            while (!atEnd()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }

        void expect(char c) {
            if (atEnd() || next() != c) {
                throw new JsonException("Expected '" + c + "' at offset " + (pos - 1));
            }
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        Map<String, Object> parseObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (!atEnd() && peek() == '}') { pos++; return result; }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                result.put(key, value);
                skipWhitespace();
                char c = next();
                if (c == ',') continue;
                if (c == '}') break;
                throw new JsonException("Expected ',' or '}' at offset " + (pos - 1));
            }
            return result;
        }

        List<Object> parseArray() {
            List<Object> result = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (!atEnd() && peek() == ']') { pos++; return result; }
            while (true) {
                Object value = parseValue();
                result.add(value);
                skipWhitespace();
                char c = next();
                if (c == ',') continue;
                if (c == ']') break;
                throw new JsonException("Expected ',' or ']' at offset " + (pos - 1));
            }
            return result;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') break;
                if (c == '\\') {
                    char esc = next();
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            String hex = s.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new JsonException("Unknown escape: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new JsonException("Invalid literal at offset " + pos);
        }

        Object parseNull() {
            if (s.startsWith("null", pos)) { pos += 4; return null; }
            throw new JsonException("Invalid literal at offset " + pos);
        }

        Object parseNumber() {
            int start = pos;
            if (!atEnd() && peek() == '-') pos++;
            while (!atEnd() && Character.isDigit(peek())) pos++;
            boolean isDouble = false;
            if (!atEnd() && peek() == '.') {
                isDouble = true;
                pos++;
                while (!atEnd() && Character.isDigit(peek())) pos++;
            }
            if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
                isDouble = true;
                pos++;
                if (!atEnd() && (peek() == '+' || peek() == '-')) pos++;
                while (!atEnd() && Character.isDigit(peek())) pos++;
            }
            String numStr = s.substring(start, pos);
            if (numStr.isEmpty() || numStr.equals("-")) {
                throw new JsonException("Invalid number at offset " + start);
            }
            return isDouble ? (Object) Double.parseDouble(numStr) : (Object) Long.parseLong(numStr);
        }
    }

    // ---------------------------------------------------------------- writing

    public static String write(Object value, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, pretty, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object value, boolean pretty, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String str) {
            writeString(sb, str);
        } else if (value instanceof Boolean b) {
            sb.append(b.toString());
        } else if (value instanceof Number n) {
            if (n instanceof Double || n instanceof Float) {
                sb.append(n.doubleValue());
            } else {
                sb.append(n.longValue());
            }
        } else if (value instanceof Map) {
            writeObject(sb, (Map<String, Object>) value, pretty, indent);
        } else if (value instanceof List) {
            writeArray(sb, (List<Object>) value, pretty, indent);
        } else {
            throw new JsonException("Cannot serialize value of type " + value.getClass());
        }
    }

    private static void writeString(StringBuilder sb, String str) {
        sb.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static void indent(StringBuilder sb, int level) {
        sb.append("  ".repeat(level));
    }

    private static void writeObject(StringBuilder sb, Map<String, Object> obj, boolean pretty, int indent) {
        if (obj.isEmpty()) { sb.append("{}"); return; }
        sb.append('{');
        if (pretty) sb.append('\n');
        int i = 0;
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            if (pretty) indent(sb, indent + 1);
            writeString(sb, e.getKey());
            sb.append(':');
            if (pretty) sb.append(' ');
            writeValue(sb, e.getValue(), pretty, indent + 1);
            if (++i < obj.size()) sb.append(',');
            if (pretty) sb.append('\n');
        }
        if (pretty) indent(sb, indent);
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<Object> arr, boolean pretty, int indent) {
        if (arr.isEmpty()) { sb.append("[]"); return; }
        sb.append('[');
        if (pretty) sb.append('\n');
        for (int i = 0; i < arr.size(); i++) {
            if (pretty) indent(sb, indent + 1);
            writeValue(sb, arr.get(i), pretty, indent + 1);
            if (i < arr.size() - 1) sb.append(',');
            if (pretty) sb.append('\n');
        }
        if (pretty) indent(sb, indent);
        sb.append(']');
    }
}

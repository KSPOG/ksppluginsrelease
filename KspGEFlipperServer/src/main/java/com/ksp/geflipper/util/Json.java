package com.ksp.geflipper.util;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.time.temporal.TemporalAccessor;
import java.util.*;

/** Small dependency-free JSON codec for the server API and JSONL persistence. */
public final class Json {
    private Json() {}

    public static Object parse(String text) {
        return new Parser(text == null ? "" : text).parse();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String text) {
        Object value = parse(text);
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("JSON root must be an object");
        return (Map<String, Object>) map;
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder(256);
        write(out, value);
        return out.toString();
    }

    private static void write(StringBuilder out, Object value) {
        if (value == null) { out.append("null"); return; }
        if (value instanceof String || value instanceof Character || value instanceof Enum<?> || value instanceof UUID || value instanceof TemporalAccessor) {
            quote(out, String.valueOf(value)); return;
        }
        if (value instanceof Boolean || value instanceof Number) { out.append(value); return; }
        if (value instanceof Optional<?> optional) { write(out, optional.orElse(null)); return; }
        if (value instanceof Map<?, ?> map) {
            out.append('{'); boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(','); first = false;
                quote(out, String.valueOf(entry.getKey())); out.append(':'); write(out, entry.getValue());
            }
            out.append('}'); return;
        }
        if (value instanceof Iterable<?> iterable) {
            out.append('['); boolean first = true;
            for (Object item : iterable) { if (!first) out.append(','); first = false; write(out, item); }
            out.append(']'); return;
        }
        if (value.getClass().isArray()) {
            out.append('['); int length = Array.getLength(value);
            for (int i = 0; i < length; i++) { if (i > 0) out.append(','); write(out, Array.get(value, i)); }
            out.append(']'); return;
        }
        if (value.getClass().isRecord()) {
            out.append('{'); boolean first = true;
            for (RecordComponent component : value.getClass().getRecordComponents()) {
                try {
                    if (!first) out.append(','); first = false;
                    quote(out, component.getName()); out.append(':'); write(out, component.getAccessor().invoke(value));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot serialize record " + value.getClass().getName(), e);
                }
            }
            out.append('}'); return;
        }
        quote(out, String.valueOf(value));
    }

    private static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String s; private int i;
        Parser(String s) { this.s = s; }
        Object parse() { skip(); Object v = value(); skip(); if (i != s.length()) error("Trailing input"); return v; }
        private Object value() {
            skip(); if (i >= s.length()) error("Unexpected end"); char c = s.charAt(i);
            return switch (c) {
                case '{' -> object(); case '[' -> array(); case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE); case 'f' -> literal("false", Boolean.FALSE); case 'n' -> literal("null", null);
                default -> number();
            };
        }
        private Map<String,Object> object() {
            LinkedHashMap<String,Object> map = new LinkedHashMap<>(); expect('{'); skip(); if (take('}')) return map;
            while (true) { skip(); String key = string(); skip(); expect(':'); map.put(key, value()); skip(); if (take('}')) return map; expect(','); }
        }
        private List<Object> array() {
            ArrayList<Object> list = new ArrayList<>(); expect('['); skip(); if (take(']')) return list;
            while (true) { list.add(value()); skip(); if (take(']')) return list; expect(','); }
        }
        private String string() {
            expect('"'); StringBuilder out = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++); if (c == '"') return out.toString();
                if (c == '\\') { if (i >= s.length()) error("Bad escape"); char e = s.charAt(i++); switch (e) {
                    case '"','\\','/' -> out.append(e); case 'b' -> out.append('\b'); case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                    case 'u' -> { if (i + 4 > s.length()) error("Bad unicode escape"); out.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    default -> error("Bad escape");
                }} else out.append(c);
            }
            error("Unterminated string"); return "";
        }
        private Object number() {
            int start = i; if (take('-')) {} while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            boolean decimal = false; if (take('.')) { decimal = true; while (i < s.length() && Character.isDigit(s.charAt(i))) i++; }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) { decimal = true; i++; if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++; while (i < s.length() && Character.isDigit(s.charAt(i))) i++; }
            if (start == i) error("Expected value"); String raw = s.substring(start, i);
            try { return decimal ? Double.parseDouble(raw) : Long.parseLong(raw); } catch (NumberFormatException e) { error("Invalid number"); return 0; }
        }
        private Object literal(String token, Object value) { if (!s.startsWith(token, i)) error("Invalid literal"); i += token.length(); return value; }
        private void skip() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        private boolean take(char c) { if (i < s.length() && s.charAt(i) == c) { i++; return true; } return false; }
        private void expect(char c) { if (!take(c)) error("Expected '" + c + "'"); }
        private void error(String message) { throw new IllegalArgumentException(message + " at offset " + i); }
    }
}

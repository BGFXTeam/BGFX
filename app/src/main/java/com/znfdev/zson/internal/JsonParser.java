package com.znfdev.zson.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.znfdev.zson.JsonSyntaxException;

/**
 * A small hand-written recursive-descent JSON parser. Produces a plain Java object
 * tree: {@link LinkedHashMap} for objects (insertion order preserved), {@link ArrayList}
 * for arrays, {@link String}, {@link Long}/{@link Double} for numbers, {@link Boolean},
 * or {@code null}.
 */
public final class JsonParser {

    private final String json;
    private int pos;
    private final int length;

    public JsonParser(String json) {
        this.json = json;
        this.length = json.length();
        this.pos = 0;
    }

    public Object parse() {
        skipWhitespace();
        Object value = parseValue();
        skipWhitespace();
        if (pos != length) {
            throw new JsonSyntaxException("Unexpected trailing characters at position " + pos);
        }
        return value;
    }

    private Object parseValue() {
        if (pos >= length) {
            throw new JsonSyntaxException("Unexpected end of input");
        }
        char c = json.charAt(pos);
        if (c == '{') {
            return parseObject();
        }
        if (c == '[') {
            return parseArray();
        }
        if (c == '"') {
            return parseString();
        }
        if (c == 't' || c == 'f') {
            return parseBoolean();
        }
        if (c == 'n') {
            return parseNull();
        }
        if (c == '-' || (c >= '0' && c <= '9')) {
            return parseNumber();
        }
        throw new JsonSyntaxException("Unexpected character '" + c + "' at position " + pos);
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        pos++; // consume '{'
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw new JsonSyntaxException("Expected string key at position " + pos);
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            Object value = parseValue();
            result.put(key, value);
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                pos++;
                continue;
            }
            if (next == '}') {
                pos++;
                break;
            }
            throw new JsonSyntaxException("Expected ',' or '}' at position " + pos);
        }
        return result;
    }

    private List<Object> parseArray() {
        List<Object> result = new ArrayList<Object>();
        pos++; // consume '['
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            result.add(parseValue());
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                pos++;
                continue;
            }
            if (next == ']') {
                pos++;
                break;
            }
            throw new JsonSyntaxException("Expected ',' or ']' at position " + pos);
        }
        return result;
    }

    private String parseString() {
        pos++; // consume opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= length) {
                throw new JsonSyntaxException("Unterminated string");
            }
            char c = json.charAt(pos++);
            if (c == '"') {
                break;
            }
            if (c == '\\') {
                if (pos >= length) {
                    throw new JsonSyntaxException("Unterminated escape sequence");
                }
                char escape = json.charAt(pos++);
                switch (escape) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > length) {
                            throw new JsonSyntaxException("Invalid unicode escape");
                        }
                        String hex = json.substring(pos, pos + 4);
                        pos += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                        break;
                    default:
                        throw new JsonSyntaxException("Invalid escape character '" + escape + "'");
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Boolean parseBoolean() {
        if (json.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (json.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new JsonSyntaxException("Invalid literal at position " + pos);
    }

    private Object parseNull() {
        if (json.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new JsonSyntaxException("Invalid literal at position " + pos);
    }

    private Object parseNumber() {
        int start = pos;
        boolean isFloating = false;
        if (peek() == '-') {
            pos++;
        }
        while (pos < length && Character.isDigit(json.charAt(pos))) {
            pos++;
        }
        if (pos < length && json.charAt(pos) == '.') {
            isFloating = true;
            pos++;
            while (pos < length && Character.isDigit(json.charAt(pos))) {
                pos++;
            }
        }
        if (pos < length && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
            isFloating = true;
            pos++;
            if (pos < length && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < length && Character.isDigit(json.charAt(pos))) {
                pos++;
            }
        }
        String number = json.substring(start, pos);
        if (number.length() == 0 || "-".equals(number)) {
            throw new JsonSyntaxException("Invalid number at position " + start);
        }
        if (isFloating) {
            return Double.valueOf(number);
        }
        try {
            return Long.valueOf(number);
        } catch (NumberFormatException e) {
            return Double.valueOf(number);
        }
    }

    private char peek() {
        if (pos >= length) {
            throw new JsonSyntaxException("Unexpected end of input");
        }
        return json.charAt(pos);
    }

    private void expect(char c) {
        if (pos >= length || json.charAt(pos) != c) {
            throw new JsonSyntaxException("Expected '" + c + "' at position " + pos);
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < length) {
            char c = json.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }
}

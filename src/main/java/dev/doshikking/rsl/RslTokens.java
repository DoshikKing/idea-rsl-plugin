package dev.doshikking.rsl;

import java.util.Locale;
import java.util.Set;

final class RslTokens {
    enum Kind {
        SPACE, LINE_COMMENT, BLOCK_COMMENT, OPEN_COMMENT,
        NAME, SPECIAL_NAME, OPEN_NAME, KEYWORD, TYPE, LITERAL,
        STRING, OPEN_STRING, NUMBER, BAD_NUMBER, FORM, OPEN_FORM,
        LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA, COLON, SEMICOLON, DOT, ASSIGN, OPERATOR, BAD, EOF
    }

    private static final Set<String> KEYWORDS = Set.of(
            "and", "array", "break", "class", "const", "continue", "elif", "else", "end", "file",
            "for", "if", "import", "local", "macro", "not", "onerror", "or", "private", "record",
            "return", "this", "var", "while", "with");
    private static final Set<String> TYPES = Set.of(
            "arrayref", "bool", "btfileref", "date", "datetime", "dbffileref", "decimal", "double",
            "doublel", "integer", "memaddr", "methodref", "money", "moneyl", "numeric", "object",
            "procref", "r2m", "specval", "strucref", "string", "time", "txtfileref", "variant");
    private static final Set<String> LITERALS = Set.of("true", "false", "null", "nullval", "optval");

    record Token(Kind kind, String text, int start, int end, boolean lineBreakBefore) {
        String word() {
            return text.toLowerCase(Locale.ROOT);
        }
    }

    record Match(Kind kind, int end) {}

    private RslTokens() {}

    static Match scan(CharSequence text, int start, int end, int state) {
        if (start >= end) return new Match(Kind.EOF, end);
        char first = text.charAt(start);
        int next = start + 1;
        char second = at(text, next, end);
        if (Character.isWhitespace(first)) {
            while (next < end && Character.isWhitespace(text.charAt(next))) next++;
            return new Match(Kind.SPACE, next);
        }
        if (first == '/' && second == '/') {
            while (next < end && !newLine(text.charAt(next))) next++;
            return new Match(Kind.LINE_COMMENT, next);
        }
        if (first == '/' && second == '*') {
            int depth = 1;
            next++;
            while (next < end) {
                char current = text.charAt(next++);
                char following = at(text, next, end);
                if (current == '/' && following == '*') {
                    depth++;
                    next++;
                } else if (current == '*' && following == '/') {
                    next++;
                    if (--depth == 0) return new Match(Kind.BLOCK_COMMENT, next);
                }
            }
            return new Match(Kind.OPEN_COMMENT, end);
        }
        if (first == '"') {
            while (next < end && !newLine(text.charAt(next))) {
                char current = text.charAt(next++);
                if (current == '"') return new Match(Kind.STRING, next);
                if (current == '\\' && next < end && !newLine(text.charAt(next))) next++;
            }
            return new Match(Kind.OPEN_STRING, next);
        }
        if (first == '{' || first == '[' && (state & AFTER_VALUE) == 0) {
            char close = first == '{' ? '}' : ']';
            while (next < end && text.charAt(next) != close) next++;
            if (next < end) return new Match(first == '{' ? Kind.SPECIAL_NAME : Kind.FORM, next + 1);
            return new Match(first == '{' ? Kind.OPEN_NAME : Kind.OPEN_FORM, next);
        }
        if (letter(first)) {
            while (next < end && (letter(text.charAt(next)) || digit(text.charAt(next)))) next++;
            String word = text.subSequence(start, next).toString().toLowerCase(Locale.ROOT);
            if (KEYWORDS.contains(word)) return new Match(Kind.KEYWORD, next);
            if (TYPES.contains(word)) return new Match(Kind.TYPE, next);
            if (LITERALS.contains(word)) return new Match(Kind.LITERAL, next);
            return new Match(Kind.NAME, next);
        }
        if (digit(first) || first == '$' || first == '#' || (first == '.' && digit(second))) {
            return number(text, start, end, first);
        }
        return switch (first) {
            case '(' -> new Match(Kind.LPAREN, next);
            case ')' -> new Match(Kind.RPAREN, next);
            case '[' -> new Match(Kind.LBRACKET, next);
            case ']' -> new Match(Kind.RBRACKET, next);
            case ',' -> new Match(Kind.COMMA, next);
            case ':' -> new Match(Kind.COLON, next);
            case ';' -> new Match(Kind.SEMICOLON, next);
            case '.' -> new Match(Kind.DOT, next);
            case '+', '-', '*', '/', '@' -> new Match(Kind.OPERATOR, next);
            case '<', '>' -> new Match(Kind.OPERATOR, second == '=' ? next + 1 : next);
            case '=' -> second == '=' ? new Match(Kind.OPERATOR, next + 1) : new Match(Kind.ASSIGN, next);
            case '!' -> second == '=' ? new Match(Kind.OPERATOR, next + 1) : new Match(Kind.BAD, next);
            default -> new Match(Kind.BAD, next);
        };
    }

    private static final int AFTER_VALUE = 1;
    private static final int BEFORE_CONDITION = 2;
    private static final int BEFORE_DECLARATION_NAME = 4;
    private static final int AFTER_DECLARATION_NAME = 8;
    private static final int AFTER_PARAMETERS = 16;
    private static final int BEFORE_RETURN_TYPE = 32;
    private static final int HEADER_LEVEL = 64;

    static int nextState(CharSequence text, int start, Match match, int state) {
        int depth = state & -HEADER_LEVEL;
        int header = state & (HEADER_LEVEL - 1) & ~AFTER_VALUE;
        return switch (match.kind()) {
            case SPACE, LINE_COMMENT, BLOCK_COMMENT, OPEN_COMMENT -> {
                for (int i = start; i < match.end(); i++) {
                    if (newLine(text.charAt(i))) yield state & ~AFTER_VALUE;
                }
                yield state;
            }
            case LPAREN -> {
                if (depth != 0) yield depth + HEADER_LEVEL | header;
                yield switch (header) {
                    case BEFORE_CONDITION -> HEADER_LEVEL;
                    case BEFORE_DECLARATION_NAME -> HEADER_LEVEL | BEFORE_DECLARATION_NAME;
                    case AFTER_DECLARATION_NAME -> HEADER_LEVEL | AFTER_PARAMETERS;
                    default -> 0;
                };
            }
            case RPAREN -> {
                if (depth == HEADER_LEVEL) yield header;
                if (depth > HEADER_LEVEL) yield depth - HEADER_LEVEL | header | AFTER_VALUE;
                yield AFTER_VALUE;
            }
            case NAME, SPECIAL_NAME, TYPE -> {
                if (depth != 0) yield depth | header | AFTER_VALUE;
                if (header == BEFORE_DECLARATION_NAME) yield AFTER_DECLARATION_NAME;
                yield header == BEFORE_RETURN_TYPE ? 0 : AFTER_VALUE;
            }
            case COLON -> depth == 0 && (header == AFTER_DECLARATION_NAME || header == AFTER_PARAMETERS)
                    ? BEFORE_RETURN_TYPE : depth | header;
            case RBRACKET -> depth | header | AFTER_VALUE;
            case KEYWORD -> switch (text.subSequence(start, match.end()).toString().toLowerCase(Locale.ROOT)) {
                case "if", "elif", "while", "for", "with", "onerror" -> BEFORE_CONDITION;
                case "macro", "class" -> BEFORE_DECLARATION_NAME;
                case "this" -> depth | header | AFTER_VALUE;
                default -> depth == 0 ? 0 : depth | header;
            };
            default -> depth == 0 ? 0 : depth | header;
        };
    }

    private static Match number(CharSequence text, int start, int end, char first) {
        int next = start;
        if (first == '#') {
            next++;
            while (hex(at(text, next, end))) next++;
            boolean valid = next > start + 1;
            if (letter(at(text, next, end))) {
                do {
                    next++;
                } while (letter(at(text, next, end)) || digit(at(text, next, end)));
                valid = false;
            }
            return new Match(valid ? Kind.NUMBER : Kind.BAD_NUMBER, next);
        }
        boolean money = first == '$';
        if (money) next++;
        int digitsStart = next;
        while (digit(at(text, next, end))) next++;
        boolean hasDigits = next > digitsStart;
        if (at(text, next, end) == '.') {
            next++;
            digitsStart = next;
            while (digit(at(text, next, end))) next++;
            hasDigits |= next > digitsStart;
        }
        if (!money && (at(text, next, end) == 'e' || at(text, next, end) == 'E')) {
            next++;
            if (at(text, next, end) == '+' || at(text, next, end) == '-') next++;
            digitsStart = next;
            while (digit(at(text, next, end))) next++;
            hasDigits &= next > digitsStart;
        }
        return new Match(hasDigits ? Kind.NUMBER : Kind.BAD_NUMBER, next);
    }

    static boolean newLine(char ch) {
        return ch == '\n' || ch == '\r' || ch == '\u2028' || ch == '\u2029';
    }

    static boolean hex(char ch) {
        return digit(ch) || ch >= 'a' && ch <= 'f' || ch >= 'A' && ch <= 'F';
    }

    private static boolean digit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    private static boolean letter(char ch) {
        return ch == '_' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z'
                || ch >= 'А' && ch <= 'я' || ch == 'Ё' || ch == 'ё';
    }

    private static char at(CharSequence text, int index, int end) {
        return index < end ? text.charAt(index) : '\0';
    }
}

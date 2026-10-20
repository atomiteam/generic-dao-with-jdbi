package org.atomiteam.jdbi.generic.query;

import java.util.*;
import java.util.function.Function;

/** Small lexical helper for composition; this is not a general SQL parser. */
final class SqlFragments {
    private SqlFragments() {}

    static String predicate(String text, Object[] values, Function<Object, String> binding) {
        List<Integer> positions = positions(text, '?');
        if (positions.size() != values.length)
            throw new IllegalArgumentException("Predicate placeholder count differs from values");
        StringBuilder result = new StringBuilder();
        int start = 0;
        for (int i = 0; i < positions.size(); i++) {
            int index = positions.get(i);
            result.append(text, start, index).append(':').append(binding.apply(values[i]));
            start = index + 1;
        }
        return result.append(text.substring(start)).toString();
    }

    static String append(String baseSql, String predicate) {
        List<Token> tokens = tokens(baseSql);
        int where = -1, tail = baseSql.length();
        for (Token token : tokens) {
            if (token.word.equals("WHERE")) where = token.start;
            if (Arrays.asList("GROUP", "HAVING", "ORDER", "LIMIT", "OFFSET", "FETCH", "FOR")
                    .contains(token.word)) {
                tail = token.start;
                break;
            }
            if (Arrays.asList("UNION", "INTERSECT", "EXCEPT").contains(token.word))
                throw new IllegalArgumentException(
                        "Wrap set operations in a SELECT before adding filters");
        }
        String suffix = baseSql.substring(tail);
        if (where < 0)
            return baseSql.substring(0, tail).stripTrailing()
                    + "\n WHERE "
                    + predicate
                    + (suffix.isEmpty() ? "" : " " + suffix);
        String existing = baseSql.substring(where + 5, tail).trim();
        return baseSql.substring(0, where)
                + "WHERE ("
                + existing
                + "\n) AND "
                + predicate
                + (suffix.isEmpty() ? "" : " " + suffix);
    }

    private static List<Integer> positions(String sql, char wanted) {
        List<Integer> result = new ArrayList<>();
        scan(
                sql,
                (index, depth) -> {
                    if (sql.charAt(index) == wanted) result.add(index);
                });
        return result;
    }

    private static List<Token> tokens(String sql) {
        List<Token> tokens = new ArrayList<>();
        scan(
                sql,
                (index, depth) -> {
                    if (depth != 0
                            || !Character.isLetter(sql.charAt(index))
                            || (index > 0 && Character.isJavaIdentifierPart(sql.charAt(index - 1))))
                        return;
                    int end = index + 1;
                    while (end < sql.length() && Character.isJavaIdentifierPart(sql.charAt(end)))
                        end++;
                    tokens.add(
                            new Token(sql.substring(index, end).toUpperCase(Locale.ROOT), index));
                });
        return tokens;
    }

    private interface Visitor {
        void visit(int index, int depth);
    }

    private static void scan(String sql, Visitor visitor) {
        int depth = 0;
        for (int i = 0; i < sql.length(); i++) {
            char character = sql.charAt(i);
            if (character == '\'' || character == '"' || character == '`') {
                char quote = character;
                boolean closed = false;
                while (++i < sql.length()) {
                    if (sql.charAt(i) != quote) continue;
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                        i++;
                        continue;
                    }
                    closed = true;
                    break;
                }
                if (!closed) throw new IllegalArgumentException("Unclosed SQL quote");
            } else if (character == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
            } else if (character == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) throw new IllegalArgumentException("Unclosed SQL comment");
                i = end + 1;
            } else if (character == '$') {
                int end = sql.indexOf('$', i + 1);
                if (end >= 0 && sql.substring(i + 1, end).matches("[A-Za-z_][A-Za-z0-9_]*|")) {
                    String quote = sql.substring(i, end + 1);
                    int close = sql.indexOf(quote, end + 1);
                    if (close < 0) throw new IllegalArgumentException("Unclosed SQL dollar quote");
                    i = close + quote.length() - 1;
                } else visitor.visit(i, depth);
            } else {
                if (character == ')') depth--;
                if (depth < 0) throw new IllegalArgumentException("Unbalanced SQL parentheses");
                visitor.visit(i, depth);
                if (character == '(') depth++;
            }
        }
        if (depth != 0) throw new IllegalArgumentException("Unbalanced SQL parentheses");
    }

    private static final class Token {
        final String word;
        final int start;

        Token(String word, int start) {
            this.word = word;
            this.start = start;
        }
    }
}

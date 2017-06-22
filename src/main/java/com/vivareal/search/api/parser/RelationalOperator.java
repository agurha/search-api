package com.vivareal.search.api.parser;

import java.util.HashMap;
import java.util.Map;

import static java.util.Optional.ofNullable;

public enum RelationalOperator {
    DIFFERENT,
    EQUAL,
    GREATER,
    GREATER_EQUAL,
    IN,
    LESS,
    LESS_EQUAL;

    private static final Map<String, RelationalOperator> OPERATORS = new HashMap<>(13);

    static {
        OPERATORS.put("NE", DIFFERENT);
        OPERATORS.put("<>", DIFFERENT);
        OPERATORS.put("EQ", EQUAL);
        OPERATORS.put(":", EQUAL);
        OPERATORS.put("=", EQUAL);
        OPERATORS.put("GT", GREATER);
        OPERATORS.put(">", GREATER);
        OPERATORS.put("GTE", GREATER_EQUAL);
        OPERATORS.put(">=", GREATER_EQUAL);
        OPERATORS.put("IN", IN);
        OPERATORS.put("LT", LESS);
        OPERATORS.put("<", LESS);
        OPERATORS.put("LTE", LESS_EQUAL);
        OPERATORS.put("<=", LESS_EQUAL);
    }

    protected static String[] getOperators() {
        return OPERATORS.keySet().toArray(new String[]{});
    }

    public static RelationalOperator get(final String relation) {
        return ofNullable(relation)
                .map(String::toUpperCase)
                .map(OPERATORS::get)
                .orElseThrow(() -> new IllegalArgumentException("Relational Operator \"" + relation + "\" is not recognized!"));
    }
}
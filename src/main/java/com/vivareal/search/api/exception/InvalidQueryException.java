package com.vivareal.search.api.exception;

import java.util.function.Function;

public class InvalidQueryException extends IllegalArgumentException {

    private static final Function<String, String> QUERY_MESSAGE_FN = query -> String.format("Invalid query: [ %s ]", query);

    public InvalidQueryException(String query) {
        super(QUERY_MESSAGE_FN.apply(query));
    }

    public InvalidQueryException(String query, Throwable cause) {
        super(QUERY_MESSAGE_FN.apply(query), cause);
    }
}

package com.vivareal.search.api.exception;

import java.util.function.Function;

public class IndexNotFoundException extends IllegalArgumentException {

    private static final Function<String, String> INDEX_MESSAGE_FN = index -> String.format("Invalid index: [ %s ]", index);

    public IndexNotFoundException(final String indexName) {
        super(INDEX_MESSAGE_FN.apply(indexName));
    }

    public IndexNotFoundException(String indexName, Throwable cause) {
        super(INDEX_MESSAGE_FN.apply(indexName), cause);
    }
}

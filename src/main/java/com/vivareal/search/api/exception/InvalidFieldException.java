package com.vivareal.search.api.exception;

import java.util.function.BiFunction;

/**
 * Created by leandropereirapinto on 7/12/17.
 */
public class InvalidFieldException extends IllegalArgumentException {

    private static final BiFunction<String, String, String> ERROR_MESSAGE = (field, index) -> String.format("Field %s not found for index %s", field, index);

    public InvalidFieldException(final String field, final String index) {
        super(ERROR_MESSAGE.apply(field, index));
    }

    public InvalidFieldException(String field, final String index, Throwable cause) {
        super(ERROR_MESSAGE.apply(field, index), cause);
    }

    public InvalidFieldException(Throwable cause) {
        super(cause);
    }
}

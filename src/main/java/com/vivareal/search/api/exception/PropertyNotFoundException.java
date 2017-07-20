package com.vivareal.search.api.exception;

import java.util.function.BiFunction;

/**
 * Created by leandropereirapinto on 7/12/17.
 */
public class PropertyNotFoundException extends IllegalArgumentException {

    private static final BiFunction<String, String, String> ERROR_MESSAGE = (property, index) -> String.format("Property %s not found for index %s", property, index);

    public PropertyNotFoundException(final String property, final String index) {
        super(ERROR_MESSAGE.apply(property, index));
    }

    public PropertyNotFoundException(String property, final String index, Throwable cause) {
        super(ERROR_MESSAGE.apply(property, index), cause);
    }

    public PropertyNotFoundException(Throwable cause) {
        super(cause);
    }
}

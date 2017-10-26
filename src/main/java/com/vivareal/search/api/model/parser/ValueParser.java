package com.vivareal.search.api.model.parser;


import com.vivareal.search.api.model.query.GeoPointValue;
import com.vivareal.search.api.model.query.LikeValue;
import com.vivareal.search.api.model.query.RangeValue;
import com.vivareal.search.api.model.query.Value;
import org.jparsec.Parser;
import org.springframework.stereotype.Component;

import static java.lang.String.valueOf;
import static org.jparsec.Parsers.*;
import static org.jparsec.Scanners.*;

@Component
public class ValueParser {

    private final Parser<Value> BOOLEAN = or(stringCaseInsensitive("FALSE").retn(false), stringCaseInsensitive("TRUE").retn(true)).label("boolean").map(Value::new);

    private final Parser<Value> NULL = stringCaseInsensitive("NULL").retn(Value.NULL_VALUE).label("null");

    private final Parser<Value> STRING = or(SINGLE_QUOTE_STRING, DOUBLE_QUOTE_STRING).map(s -> new Value(valueOf(s.replaceAll("\'", "").replaceAll("\"", "").trim()))).label("string");

    private final Parser<Value> NUMBER = or(longer(INTEGER.map(Integer::valueOf), DECIMAL.map(Double::valueOf)), string("-").next(DECIMAL.map(n -> -Double.valueOf(n)))).label("number").map(Value::new);

    private final Parser<Value> VALUE = between(WHITESPACES.skipMany(), or(BOOLEAN, NULL, NUMBER, STRING), WHITESPACES.skipMany());

    private final Parser<Value> VALUE_IN =
        between(isChar('['), VALUE.sepBy(isChar(',')), isChar(']'))
            .label("[]")
            .map(Value::new);

    private final Parser<Value> VALUE_PARSER = or(VALUE_IN, VALUE);

    Parser<Value> get() {
        return VALUE_PARSER;
    }

    private final Parser<Value> VALUE_LIKE = STRING.label("like").map(LikeValue::new);

    Parser<Value> getLikeValue() {
        return VALUE_LIKE;
    }

    private final Parser<Value> VALUE_RANGE = VALUE_IN.label("range").map(RangeValue::new);

    Parser<Value> getRangeValue() {
        return VALUE_RANGE;
    }

    Parser<Value> getGeoPointValue(GeoPoint.Type type) {
        return between(isChar('['), get().sepBy1(between(WHITESPACES.skipMany(), isChar(','), WHITESPACES.skipMany())), isChar(']'))
        .label(type.name())
        .map(values -> new GeoPointValue(values, type));
    }

    public static class GeoPoint {
        public enum Type {
            VIEWPORT(2, 2),
            POLYGON(3, 1000);

            Type(int minSize, int maxSize) {
                this.minSize = minSize;
                this.maxSize = maxSize;
            }

            private final int minSize;
            private final int maxSize;

            public int getMinSize() {
                return minSize;
            }

            public int getMaxSize() {
                return maxSize;
            }
        }
    }
}

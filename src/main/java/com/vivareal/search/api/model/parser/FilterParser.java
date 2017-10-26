package com.vivareal.search.api.model.parser;

import com.vivareal.search.api.model.parser.ValueParser.GeoPoint.Type;
import com.vivareal.search.api.model.query.Filter;
import org.jparsec.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.vivareal.search.api.model.query.RelationalOperator.*;
import static org.jparsec.Parsers.or;
import static org.jparsec.Parsers.sequence;

@Component
public class FilterParser {

    private final Parser<Filter> NORMAL_PARSER;
    private final Parser<Filter> LIKE_PARSER;
    private final Parser<Filter> RANGE_PARSER;
    private final Parser<Filter> VIEWPORT_PARSER;
    private final Parser<Filter> POLYGON_PARSER;
    private final Parser<Filter> FILTER_PARSER;

    @Autowired
    public FilterParser(FieldParser fieldParser, OperatorParser operatorParser, ValueParser valueParser) {
        NORMAL_PARSER = sequence(fieldParser.get(), operatorParser.exact(DIFFERENT, EQUAL, GREATER_EQUAL, GREATER, IN, LESS_EQUAL, LESS), valueParser.get(), Filter::new).label("filter");
        LIKE_PARSER = sequence(fieldParser.get(), operatorParser.exact(LIKE), valueParser.getLikeValue(), Filter::new).label("LIKE filter");
        RANGE_PARSER = sequence(fieldParser.get(), operatorParser.exact(RANGE), valueParser.getRangeValue(), Filter::new).label("RANGE filter");
        VIEWPORT_PARSER = sequence(fieldParser.get(), operatorParser.exact(VIEWPORT), valueParser.getGeoPointValue(Type.VIEWPORT), Filter::new).label("VIEWPORT filter");
        POLYGON_PARSER = sequence(fieldParser.get(), operatorParser.exact(POLYGON), valueParser.getGeoPointValue(Type.POLYGON), Filter::new).label("POLYGON filter");
        FILTER_PARSER = or(RANGE_PARSER, VIEWPORT_PARSER, POLYGON_PARSER, LIKE_PARSER, NORMAL_PARSER);
    }


    Parser<Filter> get() {
        return FILTER_PARSER;
    }
}

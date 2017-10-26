package com.vivareal.search.api.model.parser;

import com.newrelic.api.agent.Trace;
import com.vivareal.search.api.model.query.Field;
import org.jparsec.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.jparsec.Scanners.WHITESPACES;
import static org.jparsec.Scanners.isChar;

@Component
public class FacetParser {

    @Autowired
    private FieldParser fieldParser;

    private final Parser<List<Field>> FACET_PARSER = fieldParser.getWithoutNot().sepBy1(isChar(',').next(WHITESPACES.skipMany())).label("multiple fields");

    @Trace
    public List<Field> parse(String string) {
        return FACET_PARSER.parse(string);
    }
}

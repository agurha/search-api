package com.vivareal.search.api.model.parser;

import com.newrelic.api.agent.Trace;
import com.vivareal.search.api.model.query.OrderOperator;
import com.vivareal.search.api.model.query.Sort;
import org.jparsec.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.jparsec.Parsers.sequence;
import static org.jparsec.Scanners.WHITESPACES;
import static org.jparsec.Scanners.isChar;

@Component
public class SortParser {

    @Autowired
    private FieldParser fieldParser;
    @Autowired
    private OperatorParser operatorParser;

    private final Parser<Sort> SORT_PARSER = sequence(fieldParser.getWithoutNot(), operatorParser.ORDER_OPERATOR_PARSER.optional(OrderOperator.ASC), Sort::new).sepBy(isChar(',').next(WHITESPACES.skipMany())).label("sort").map(Sort::new);

    @Trace
    public Sort parse(String string) {
        return SORT_PARSER.parse(string);
    }
}

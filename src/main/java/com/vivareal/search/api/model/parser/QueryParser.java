package com.vivareal.search.api.model.parser;

import com.newrelic.api.agent.Trace;
import com.vivareal.search.api.model.query.*;
import org.jparsec.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.jparsec.Parser.newReference;
import static org.jparsec.Parsers.or;
import static org.jparsec.Parsers.sequence;
import static org.jparsec.Scanners.isChar;

@Component
public class QueryParser {

    final Parser<QueryFragment> QUERY_PARSER;
    final Parser<QueryFragment> RECURSIVE_QUERY_PARSER;

    @Autowired
    public QueryParser(OperatorParser operatorParser, FilterParser filterParser, NotParser notParser) {
        QUERY_PARSER = sequence(operatorParser.LOGICAL_OPERATOR_PARSER.asOptional(), filterParser.get(), QueryFragmentItem::new);


        Parser.Reference<QueryFragment> ref = newReference();
        Parser<QueryFragment> lazy = ref.lazy();
        RECURSIVE_QUERY_PARSER = lazy.between(isChar('('), isChar(')'))
            .or(or(QUERY_PARSER, operatorParser.LOGICAL_OPERATOR_PARSER.map(QueryFragmentOperator::new), notParser.get().many().map(QueryFragmentNot::new)))
            .many()
            .label("query")
            .map(QueryFragmentList::new);
        ref.set(RECURSIVE_QUERY_PARSER);
    }

    public Parser<QueryFragment> get() {
        return RECURSIVE_QUERY_PARSER;
    }

    @Trace
    public QueryFragment parse(String string) {
        return get().parse(string);
    }
}

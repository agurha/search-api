package com.vivareal.search.api.model.parser;

import com.vivareal.search.api.model.query.LogicalOperator;
import com.vivareal.search.api.model.query.OrderOperator;
import com.vivareal.search.api.model.query.RelationalOperator;
import org.jparsec.*;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.jparsec.Parsers.between;
import static org.jparsec.Parsers.or;
import static org.jparsec.Scanners.WHITESPACES;
import static org.jparsec.Terminals.fragment;
import static org.jparsec.Terminals.operators;

@Component
public class OperatorParser {

    final Parser<LogicalOperator> LOGICAL_OPERATOR_PARSER = get(LogicalOperator::getOperators, LogicalOperator::get, "logical operator");

    final Parser<RelationalOperator> RELATIONAL_OPERATOR_PARSER = get(RelationalOperator::getOperators, RelationalOperator::get, "relational operator");

    final Parser<OrderOperator> ORDER_OPERATOR_PARSER = get(OrderOperator::getOperators, OrderOperator::get, "order operator");

    Parser<String> exact(RelationalOperator operator) {
        return between(WHITESPACES.skipMany(), or(operator.getAlias().stream().map(Scanners::string).toArray(Parser[]::new)), WHITESPACES.skipMany()).retn(operator.name());
    }

    Parser<String> exact(RelationalOperator... operator) {
        return Parsers.or(Stream.of(operator).map(this::exact).toArray(Parser[]::new));
    }

    private <T> Parser<T> get(Supplier<String[]> operators, Function<String, T> getFn, String label) {
        Terminals OPERATORS = operators(operators.get());
        Parser<T> OPERATOR_MAPPER = fragment(Tokens.Tag.RESERVED).label(label).map(getFn);
        return OPERATOR_MAPPER.from(OPERATORS.tokenizer(), WHITESPACES.optional(null)).label(label);
    }
}

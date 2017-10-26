package com.vivareal.search.api.model.parser;

import org.jparsec.Parser;
import org.springframework.stereotype.Component;

import static org.jparsec.Parsers.between;
import static org.jparsec.Scanners.WHITESPACES;
import static org.jparsec.Scanners.string;

@Component
public class NotParser {

    private final Parser<Boolean> NOT_PARSER = between(WHITESPACES.skipMany(), string("NOT").succeeds().label("not"), WHITESPACES.skipMany());

    Parser<Boolean> get() {
        return NOT_PARSER;
    }
}

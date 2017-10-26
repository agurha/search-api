package com.vivareal.search.api.model.parser;

import com.vivareal.search.api.model.query.Field;
import com.vivareal.search.api.service.parser.factory.FieldFactory;
import org.jparsec.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.jparsec.Parsers.sequence;
import static org.jparsec.Scanners.IDENTIFIER;
import static org.jparsec.Scanners.isChar;

@Component
public class FieldParser {

    @Autowired
    private NotParser notParser;

    @Autowired
    private FieldFactory fieldFactory;

    private final Parser<Field> SIMPLE_KEYWORD_PARSER = IDENTIFIER.sepBy1(isChar('.')).label("field").map(fieldFactory::createField);

    private final Parser<Field> SIMPLE_KEYWORD_PARSER_WITH_NOT = sequence(notParser.get(), SIMPLE_KEYWORD_PARSER, fieldFactory::createField);

    Parser<Field> get() {
        return SIMPLE_KEYWORD_PARSER;
    }

    Parser<Field> getWithoutNot() {
        return SIMPLE_KEYWORD_PARSER_WITH_NOT;
    }
}

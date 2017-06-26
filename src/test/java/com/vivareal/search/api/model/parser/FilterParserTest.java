package com.vivareal.search.api.model.parser;

import com.vivareal.search.api.model.query.Filter;
import com.vivareal.search.api.model.query.Value;
import org.jparsec.Parser;
import org.jparsec.error.ParserException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FilterParserTest {
    private static final Parser<Filter> parser = FilterParser.get();

    @Test
    public void testSingleExpressionWithDoubleQuotes() {
        parser.parse("field=\"value\"");
    }

    @Test
    public void testSingleExpressionWithSpacesAndSingleQuotes() {
        Filter filter = parser.parse("field.field2 = 'space value'");
        assertEquals("field.field2 EQUAL space value", filter.toString());
    }

    @Test
    public void testSingleExpressionWithINAndSpaces() {
        Filter filter = parser.parse("list IN [\"a\", 'b']");
        assertEquals("list IN [\"a\", \"b\"]", filter.toString());
    }

    @Test
    public void testEqualsLikeAsIN() {
        Filter filter = parser.parse("list = [\"a\", 'b']");
        assertEquals("list EQUAL [\"a\", \"b\"]", filter.toString());
    }

    @Test(expected = ParserException.class)
    public void testINLikeAsEquals() {
        parser.parse("list IN \"a\", \"b\"");
    }

    @Test
    public void testFilterEmpty() {
        Filter filter = parser.parse("field = \"\"");
        assertEquals("field EQUAL \"\"", filter.toString());
        assertFalse(filter.getValue().equals(Value.NULL_VALUE));
    }

    @Test
    public void testFilterNull() {
        Filter filter = parser.parse("sobrinho = NULL");
        assertEquals("sobrinho EQUAL NULL", filter.toString());
        assertTrue(filter.getValue().equals(Value.NULL_VALUE));
    }

    @Test
    public void testFilterQuotedNull() {
        Filter filter = parser.parse("field = 'NULL'");
        assertFalse(filter.getValue().equals(Value.NULL_VALUE));
    }

    @Test
    public void testFilterBooleanTrue() {
        Filter filterTrue = parser.parse("field = TRUE");
        Filter filterTrueLowerCase = parser.parse("field = true");
        assertEquals("field EQUAL true", filterTrue.toString());
        assertEquals(filterTrue.toString(), filterTrueLowerCase.toString());
    }

    @Test
    public void testFilterBooleanFalse() {
        Filter filterFalse = parser.parse("field = FALSE");
        Filter filterTrueLowerCase = parser.parse("field = false");
        assertEquals("field EQUAL false", filterFalse.toString());
        assertEquals(filterFalse.toString(), filterTrueLowerCase.toString());
    }
}

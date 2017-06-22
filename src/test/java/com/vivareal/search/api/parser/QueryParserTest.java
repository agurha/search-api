package com.vivareal.search.api.parser;

import org.jparsec.Parser;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class QueryParserTest {

    private static Parser<QueryFragment> parser = QueryParser.get();

//    @BeforeClass
//    public static void setup() {
//        parser = QueryParser.get();
//    }

    @Test
    public void nonRecursiveMultipleConditionsTest() {
        QueryFragment query = parser.parse("field1 EQ 'value1' AND field2 NE 'value2' OR field3 GT 123 AND field4 NE 42");
        assertEquals("field1 EQUAL value1 AND field2 DIFFERENT value2 OR field3 GREATER 123 AND field4 DIFFERENT 42", query.toString());
    }

    @Test
    public void inTest() {
        QueryFragment query = parser.parse("banos IN [3,4]");
        assertEquals("banos IN [\"3\", \"4\"]", query.toString());
    }

    @Test
    public void oneRecursionTest() {
        QueryFragment query = parser.parse("rooms:3 OR (parkingLots:1 AND xpto <> 3)");
        assertEquals("rooms EQUAL 3 OR (parkingLots EQUAL 1 AND xpto DIFFERENT 3)", query.toString());
    }

    @Test
    public void oneRecursionAndMultipleConditionsTest() {
        QueryFragment query = parser.parse("rooms:3 AND pimba:2 AND(suites=1 OR (parkingLots IN [1,3] AND xpto <> 3))");
        assertEquals("rooms EQUAL 3 AND pimba EQUAL 2 AND (suites EQUAL 1 OR (parkingLots IN [\"1\", \"3\"] AND xpto DIFFERENT 3))", query.toString());
    }

    @Test
    @Ignore
    public void oneRecursionWithNotTest() {
        QueryFragment query = parser.parse("NOT2 (suites=1)");
        assertEquals("NOT2 suites EQUAL 1", query.toString());
    }

    @Test
    @Ignore
    public void oneRecursionWithManyNotsTestdlkfndfd() {
        QueryFragment query = parser.parse("((a = 2) AND (b = 3))");
        assertEquals("(a EQUAL 2 AND b EQUAL 3)", query.toString());
    }

    @Test
    @Ignore
    public void oneRecursionWithManyNotsTestdlkfndfds() {
        QueryFragment query = parser.parse("(a = 2) AND (b = 3)");
        assertEquals("(a EQUAL 2 AND b EQUAL 3)", query.toString());
    }

    @Test
    @Ignore
    public void oneRecursionWithManyNotsTestdlkfndfddf() {
        QueryFragment query = parser.parse("(a = 2 AND b = 3)");
        assertEquals("(a EQUAL 2 AND b EQUAL 3)", query.toString());
    }

    @Test
    @Ignore
    public void oneRecursionWithManyNotsTestdlkfndfewd() {
        QueryFragment query = parser.parse("(a = 2 AND (b = 3))");
        assertEquals("(a EQUAL 2 AND b EQUAL 3)", query.toString());
    }

    @Test
    @Ignore
    public void oneRecursionWithManyNotsTestdlkfn() {
        QueryFragment query = parser.parse("NOT ((a = 2) AND (b = 3))");
        assertEquals("NOT (a EQUAL 2 AND b EQUAL 3)", query.toString());
    }

    @Test
    @Ignore
    public void oneRecursionWithManyNotsTest() {
        QueryFragment query = parser.parse("NOT2 (NOT suites=1)");
        assertEquals("NOT2 NOT suites EQUAL 1", query.toString());
    }

    @Test
    public void testFilterAndNot() {
        QueryFragment query = parser.parse("suites=1 AND NOT a:\"a\"");
        assertEquals("suites EQUAL 1 AND NOT a EQUAL a", query.toString());
    }

    @Test
    public void testFilterAndNotRecursive() {
        QueryFragment query = parser.parse("suites=1 AND (x=1 OR NOT a:\"a\")");
        assertEquals("suites EQUAL 1 AND (x EQUAL 1 OR NOT a EQUAL a)", query.toString());
    }


    @Test
    public void testFilterAndNotRecursive2() {
        QueryFragment query = parser.parse("suites=1 AND (NOT a:\"a\")");
        assertEquals("suites EQUAL 1 AND NOT a EQUAL a", query.toString());
    }

    @Test
    public void oneRecursionWithInsideNotTest() {
        QueryFragment query = parser.parse("(NOT suites=1)");
        assertEquals("NOT suites EQUAL 1", query.toString());
    }

    @Test
    public void totallyEquality() {
        QueryFragment query1 = parser.parse("rooms:3");
        QueryFragment query2 = parser.parse("(rooms:3)");
        assertEquals("rooms EQUAL 3", query1.toString());
        assertEquals(query1.toString(), query2.toString());
        assertEquals(query1, query2);
    }
}
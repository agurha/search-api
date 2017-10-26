package com.vivareal.search.api.benchmark;

import com.vivareal.search.api.model.parser.QueryParser;
import com.vivareal.search.api.model.query.QueryFragment;
import org.jparsec.Parser;
import org.openjdk.jmh.annotations.*;

public class QueryParserBenchmark {

    @State(Scope.Benchmark)
    public static class QueryState {
        final Parser<QueryFragment> parser = new QueryParser().get();
    }

    @Benchmark
    public void simpleQuery(QueryState state) {
        state.parser.parse("a = 1");
    }

    @Benchmark
    public void recursiveQuery(QueryState state) {
        state.parser.parse("rooms:3 AND pimba:2 AND(suites=1 OR (parkingLots IN [1,\"abc\"] AND xpto <> 3))");
    }
}

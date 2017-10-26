package com.vivareal.search.api.service.parser;

import com.vivareal.search.api.model.parser.QueryParser;
import com.vivareal.search.api.model.query.QueryFragment;
import com.vivareal.search.api.model.search.Filterable;
import org.springframework.stereotype.Component;

@Component
public class ParserService {


    public QueryFragment parseFilter(Filterable filterable) {
        String filter = filterable.getFilter();
        QueryFragment queryFragment = QueryParser.parse(filter);
        return queryFragment;
    }

}

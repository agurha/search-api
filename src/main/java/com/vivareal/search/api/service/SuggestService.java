package com.vivareal.search.api.service;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SuggestService {

    @Autowired
    private TransportClient transportClient;

    public SearchResponse getSuggestions(String text) {
        return transportClient.prepareSearch("discovery-fields")
                .suggest(new SuggestBuilder()
                .addSuggestion("discovery-fields",
                        SuggestBuilders
                        .completionSuggestion("suggest")
                        .prefix(text)
                        .size(10)))
                .execute().actionGet();
    }
}

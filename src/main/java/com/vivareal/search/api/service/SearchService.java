package com.vivareal.search.api.service;

import com.vivareal.search.api.adapter.QueryAdapter;
import com.vivareal.search.api.controller.stream.ElasticSearchStream;
import com.vivareal.search.api.model.http.BaseApiRequest;
import com.vivareal.search.api.model.http.SearchApiRequest;
import com.vivareal.search.api.model.http.SearchApiResponse;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.suggest.Suggest.Suggestion;
import org.elasticsearch.search.suggest.Suggest.Suggestion.Entry;
import org.elasticsearch.search.suggest.Suggest.Suggestion.Entry.Option;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.vivareal.search.api.configuration.environment.RemoteProperties.*;
import static java.util.Collections.synchronizedList;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

@Component
public class SearchService {

    private static Logger LOG = LoggerFactory.getLogger(SearchService.class);

    @Autowired
    @Qualifier("ElasticsearchQuery")
    private QueryAdapter<GetRequestBuilder, SearchRequestBuilder> queryAdapter;

    @Autowired
    private ElasticSearchStream elasticSearch;

    @Autowired
    private FilterInferenceService filterInferenceService;

    @Autowired
    private SuggestService suggestService;

    public Optional<Object> getById(BaseApiRequest request, String id) {
        try {
            GetResponse response = this.queryAdapter.getById(request, id).execute().get(ES_CONTROLLER_SEARCH_TIMEOUT.getValue(request.getIndex()), TimeUnit.MILLISECONDS);
            if (response.isExists())
                return ofNullable(response.getSource());

        } catch (Exception e) {
            LOG.error("Getting id={}, request: {}, error: {}", id, request, e);
        }
        return empty();
    }

    public SearchApiResponse search(SearchApiRequest request) {
        String index = request.getIndex();
        request.setPaginationValues(ES_DEFAULT_SIZE.getValue(index), ES_MAX_SIZE.getValue(index));

        // FIXME - Gambi hackweek
        filterInferenceService.appendInferedFilters(request);

        SearchRequestBuilder requestBuilder = this.queryAdapter.query(request);
        SearchResponse esResponse = requestBuilder.execute().actionGet((Long) ES_CONTROLLER_SEARCH_TIMEOUT.getValue(index));

        return new SearchApiResponse()
                .time(esResponse.getTookInMillis())
                .totalCount(esResponse.getHits().getTotalHits())
                .result(request.getIndex(),
                        Arrays.stream(esResponse.getHits().getHits())
                        .map(SearchHit::getSource)
                        .collect(toCollection(() -> synchronizedList(new ArrayList<>(esResponse.getHits().getHits().length)))))
                .facets(ofNullable(esResponse.getAggregations()));
    }

    public void stream(BaseApiRequest request, OutputStream stream) {
        elasticSearch.stream(request, stream);
    }

    // FIXME - Gambi hackweek
    public Object suggest(String text) {
        SearchResponse esResponse = suggestService.getSuggestions(text);
        CompletionSuggestion suggestion = esResponse.getSuggest().getSuggestion("discovery-fields");
        return suggestion.getOptions().stream().map(opt -> opt.getText().string()).collect(toCollection(() -> new TreeSet<>()));
    }
}

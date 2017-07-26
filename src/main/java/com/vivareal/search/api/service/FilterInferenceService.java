package com.vivareal.search.api.service;

import com.vivareal.search.api.model.http.SearchApiRequest;
import com.vivareal.search.api.model.query.LogicalOperator;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.SpanNearQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;
import static com.vivareal.search.api.model.query.LogicalOperator.AND;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static java.util.stream.IntStream.rangeClosed;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
// FIXME - GAMBI CLASS :'(
public class FilterInferenceService {

    private static Logger LOG = LoggerFactory.getLogger(FilterInferenceService.class);

    private static final String _SEARCH_ALL = "_search_all";
    private static final LogicalOperator DEFAULT_LOGICAL_OP = AND;

    @Autowired
    private TransportClient transportClient;

    public void appendInferedFilters(SearchApiRequest request) {
        if (isBlank(request.getQ()))
            return;

        Set<String> filters = newHashSet(executeRequest(requestForFilterInference(request)));
        Map<String, Set<String>> filtersMap = newHashMap();

        filters.forEach(filter -> {
            String[] filtersArr = filter.split(":");
            String field = filtersArr[0];
            String value = filtersArr[1];

            if (filtersMap.containsKey(field)) {
                filtersMap.get(field).add(value);
            } else {
                filtersMap.put(field, newHashSet(value));
            }
        });

        Set<String> setFiltersByQ = new HashSet<>(Arrays.asList(request.getQ().toLowerCase().split("\\s+")));

        filters.clear();
        filtersMap.forEach((k, v) -> {
            if (v.size() == 1) {
                filters.add(k + ":" + newArrayList(v).get(0));
            } else {
                StringBuilder value = new StringBuilder();
                v.forEach(s -> {
                    if (setFiltersByQ.containsAll(new HashSet<>(Arrays.asList(s.replaceAll("'", "").toLowerCase().split("\\s+")))))
                        value.append(s).append(",");
                });
                if (!value.toString().isEmpty())
                    filters.add(k + " IN [ " + value.toString().replaceAll("(,)*$", "") + " ]");
            }
        });

        String additionalFilters = filters.stream().collect(joining(format(" %s ", AND.name())));
        LOG.debug("Additional filters infered for '{}' : '{}'", request.getQ(), additionalFilters);

        if(isBlank(additionalFilters))
            return;


        String originalFilter = request.getFilter();
        if(isNotBlank(request.getFilter())) {
            request.setFilter(format("%s %s (%s)", originalFilter, AND.name(), additionalFilters));
        } else {
            request.setFilter(additionalFilters);
        }
    }

    private List<List<String>> getQWordsCombinations(String[] splitedWords) {
        List<String> words = stream(splitedWords).collect(toList());

        List<List<String>> combinations = new ArrayList<>();
        range(0, words.size()).forEach(i -> rangeClosed(i + 1, words.size()).forEach(j -> combinations.add(words.subList(i, j))));
        return combinations;
    }

    private BoolQueryBuilder createQueryForCombinations(List<List<String>> combinations) {
        BoolQueryBuilder boolQuery = boolQuery();
        combinations.forEach(combination -> {
            QueryBuilder query = combination.size() > 1 ? spanNearQueryBuilder(combination) : matchQueryBuilder(combination);
            boolQuery.should().add(query);
        });
        return boolQuery;
    }

    private QueryBuilder matchQueryBuilder(List<String> combination) {
        return new MatchQueryBuilder(_SEARCH_ALL + ".keyword", combination.get(0));
    }

    private QueryBuilder spanNearQueryBuilder(List<String> combination) {
        SpanNearQueryBuilder queryBuilder = spanNearQuery(spanTermQuery(_SEARCH_ALL, combination.get(0)), 1);
        range(1, combination.size())
            .boxed()
            .map(combination::get)
            .forEach(clause -> queryBuilder.addClause(spanTermQuery(_SEARCH_ALL, clause)));
        return queryBuilder.inOrder(true).queryName("Combination: " + combination.toString());
    }

    private SearchRequestBuilder requestForFilterInference(SearchApiRequest request) {
        SearchRequestBuilder searchBuilder = transportClient.prepareSearch("discovery-fields");
        searchBuilder.setPreference("_replica_first");
        searchBuilder.setQuery(createQueryForCombinations(getQWordsCombinations(request.getQ().split("\\s"))));
        LOG.debug("Query: {}", searchBuilder);
        return searchBuilder;
    }

    private List<String> executeRequest(SearchRequestBuilder searchBuilder) {
        try {
            SearchResponse searchResponse = searchBuilder.execute().actionGet(500l);
            return stream(searchResponse.getHits().getHits())
                .map(SearchHit::getSource)
                .map(map -> (String) map.get("filter"))
                .filter(StringUtils::isNotBlank)
                .collect(toList());
        } catch (Exception e) {
            LOG.error("Unable to infer filters for {}", searchBuilder);
            return emptyList();
        }
    }
}

package com.vivareal.search.api.service;

import com.vivareal.search.api.model.http.SearchApiRequest;
import com.vivareal.search.api.model.query.LogicalOperator;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.SpanNearQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.vivareal.search.api.model.query.LogicalOperator.AND;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static java.util.stream.IntStream.rangeClosed;
import static org.apache.commons.lang3.StringUtils.*;
import static org.elasticsearch.common.util.set.Sets.newHashSet;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
// FIXME - GAMBI CLASS :'(
public class FilterInferenceService {

    private static Logger LOG = LoggerFactory.getLogger(FilterInferenceService.class);

    private static final String _SEARCH_ALL = "_search_all";
    private static final LogicalOperator DEFAULT_LOGICAL_OP = AND;

    private static final Set<String> BLACKLIST_FOR_SINGLE_WORDS = newHashSet(
        "quarto", "quartos", "area", "areas",
        "banheiro", "banheiros", "vaga", "vagas",
        "suite", "suites", "metro", "metros" );

    @Autowired
    private TransportClient transportClient;

    public void appendInferedFilters(SearchApiRequest request) {
        if (isBlank(request.getQ()))
            return;

        StringBuilder q = new StringBuilder();
        q.append(request.getQ());

        Map<String, Boolean> filterAlias = executeRequest(requestForFilterInference(request));
        List<String> filters = newArrayList();

        composeFilters(filterAlias, filters, q);

        Map<String, List<String>> filtersMap = newHashMap();
        applyINOperatorBySameField(q.toString(), filters, filtersMap);
        composeFiltersBySameParentField(filters, filtersMap);

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

    private void composeFilters(final Map<String, Boolean> filterAlias, List<String> filters, StringBuilder q) {
        filterAlias.forEach((filter, alias) -> {
            if (alias) {
                stream(filter.split(" OR ")).forEach(
                    f -> {
                        q.append(" ").append(f.split(":")[1].replaceAll("'", ""));
                        filters.add(f);
                    }
                );
            } else {
                filters.add(filter);
            }
        });
    }

    private void applyINOperatorBySameField(final String q, List<String> filters, Map<String, List<String>> filtersMap) {

        List<String> filtersClearly = newArrayList();

        filters.forEach(filter -> {
            String[] filtersArr = trimToEmpty(filter).split(":|<>|<=|>=|<|>");
            String field = filtersArr[0];
            String value = filtersArr[1];

            if (filter.contains(":")) {
                if (filtersMap.containsKey(field) && !value.contains("(")) {
                    filtersMap.get(field).add(value);
                } else {
                    filtersMap.put(field, newArrayList(value));
                }
            } else {
                filtersClearly.add(filter);
            }
        });

        Set<String> setFiltersByQ = new HashSet<>(Arrays.asList(q.toLowerCase().split("\\s+")));

        filters.clear();
        filtersMap.forEach((k, v) -> {
            if (v.size() == 1) {
                filters.add(k + ":" + v.get(0));
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
        filters.addAll(filtersClearly);
    }

    private void composeFiltersBySameParentField(List<String> filters, Map<String, List<String>> filtersMap) {
        filtersMap.clear();
        filters.forEach(filter -> {

            String[] filtersArr = trimToEmpty(filter).split(":|<>|<=|>=|<|>");
            String field = filtersArr[0];

            if (field.contains(".")) {
                String parentField = field.split("\\.")[0];
                if (filtersMap.containsKey(parentField)) {
                    filtersMap.get(parentField).add(filter);
                } else {
                    filtersMap.put(parentField, newArrayList(filter));
                }
            } else {
                if (filtersMap.containsKey(field)) {
                    filtersMap.get(field).add(filter);
                } else {
                    filtersMap.put(field, newArrayList(filter));
                }
            }
        });

        filters.clear();
        filtersMap.forEach((field, appliedFilters) -> {
            if (appliedFilters.size() > 1) {
                StringBuilder composeFilters = new StringBuilder();
                composeFilters.append(" ( ");
                for (int i = 0; i < appliedFilters.size(); i++) {
                    composeFilters.append(appliedFilters.get(i));
                    if ((i + 1) < appliedFilters.size()) {
                        if (field.startsWith("pricingInfos")) {
                            composeFilters.append(" AND ");
                        } else {
                            composeFilters.append(" OR ");
                        }
                    }
                }
                composeFilters.append(" ) ");
                filters.add(composeFilters.toString());
            } else {
                filters.add(appliedFilters.get(0));
            }
        });
    }

    private static final int WORD_GROUPS_MAX_SIZE = 6;

    private List<List<String>> getQWordsCombinations(String[] splitedWords) {
        List<String> words = stream(splitedWords).collect(toList());

        List<List<String>> combinations = new ArrayList<>();
        range(0, words.size()).forEach(i -> rangeClosed(i + 1, min(i + WORD_GROUPS_MAX_SIZE, words.size())).forEach(j -> combinations.add(words.subList(i, j))));
        return combinations;
    }

    private BoolQueryBuilder createQueryForCombinations(List<List<String>> combinations) {
        BoolQueryBuilder boolQuery = boolQuery();
        combinations.forEach(combination -> {
            Optional<QueryBuilder> query = combination.size() > 1 ? spanNearQueryBuilder(combination) : matchQueryBuilder(combination);
            query.ifPresent(boolQuery.should()::add);
        });
        return boolQuery;
    }

    private Optional<QueryBuilder> matchQueryBuilder(List<String> combination) {
        if(combination.size() != 1 || BLACKLIST_FOR_SINGLE_WORDS.contains(combination.get(0).toLowerCase())) {
            return empty();
        }

        return of(new MatchQueryBuilder(_SEARCH_ALL + ".keyword", combination.get(0)));
    }

    private Optional<QueryBuilder> spanNearQueryBuilder(List<String> combination) {
        SpanNearQueryBuilder queryBuilder = spanNearQuery(spanTermQuery(_SEARCH_ALL, combination.get(0)), 1);
        range(1, combination.size())
            .boxed()
            .map(combination::get)
            .forEach(clause -> queryBuilder.addClause(spanTermQuery(_SEARCH_ALL, clause)));
        return Optional.of(queryBuilder.inOrder(true).queryName("Combination: " + combination.toString()));
    }

    private SearchRequestBuilder requestForFilterInference(SearchApiRequest request) {
        SearchRequestBuilder searchBuilder = transportClient.prepareSearch("discovery-fields");
        searchBuilder.setPreference("_replica_first");
        searchBuilder.setQuery(createQueryForCombinations(getQWordsCombinations(request.getQ().split("\\s"))));
        LOG.debug("Query: {}", searchBuilder);
        return searchBuilder;
    }

    private Map<String, Boolean> executeRequest(SearchRequestBuilder searchBuilder) {
        try {
            SearchResponse searchResponse = searchBuilder.execute().actionGet(500L);
            LOG.info("Inference filter took: " + searchResponse.getTookInMillis() + "ms");
            Map<String, Boolean> result = newHashMap();
            stream(searchResponse.getHits().getHits()).forEach(
                searchHitFields -> {
                    Map<String, Object> source = searchHitFields.getSource();
                    result.put(valueOf(source.get("filter")), Boolean.parseBoolean(valueOf(source.get("alias"))));
                }
            );
            return result;

        } catch (Exception e) {
            LOG.error("Unable to infer filters for {}", searchBuilder);
            return emptyMap();
        }
    }
}

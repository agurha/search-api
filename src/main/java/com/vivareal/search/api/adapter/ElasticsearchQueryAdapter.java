package com.vivareal.search.api.adapter;

import com.newrelic.api.agent.Trace;
import com.vivareal.search.api.exception.UnsupportedFieldException;
import com.vivareal.search.api.model.http.BaseApiRequest;
import com.vivareal.search.api.model.http.FilterableApiRequest;
import com.vivareal.search.api.model.http.SearchApiRequest;
import com.vivareal.search.api.model.parser.FacetParser;
import com.vivareal.search.api.model.parser.QueryParser;
import com.vivareal.search.api.model.parser.SortParser;
import com.vivareal.search.api.model.query.*;
import com.vivareal.search.api.model.search.*;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiConsumer;

import static com.google.common.collect.Maps.newHashMap;
import static com.vivareal.search.api.adapter.ElasticsearchSettingsAdapter.SHARDS;
import static com.vivareal.search.api.configuration.environment.RemoteProperties.*;
import static com.vivareal.search.api.model.mapping.MappingType.*;
import static com.vivareal.search.api.model.query.LogicalOperator.AND;
import static com.vivareal.search.api.model.query.RelationalOperator.*;
import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import static java.util.Optional.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.ArrayUtils.contains;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.lucene.search.join.ScoreMode.None;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;
import static org.springframework.util.CollectionUtils.isEmpty;

@Component
@Scope(SCOPE_SINGLETON)
@Qualifier("ElasticsearchQuery")
public class ElasticsearchQueryAdapter implements QueryAdapter<GetRequestBuilder, SearchRequestBuilder> {

    private static Logger LOG = LoggerFactory.getLogger(ElasticsearchQueryAdapter.class);

    @Autowired
    private TransportClient transportClient;

    @Autowired
    @Qualifier("elasticsearchSettings")
    private SettingsAdapter<Map<String, Map<String, Object>>, String> settingsAdapter;

    private static final String NOT_NESTED = "not_nested";

    @Override
    @Trace
    public GetRequestBuilder getById(BaseApiRequest request, String id) {
        settingsAdapter.checkIndex(request);

        Pair<String[], String[]> fetchSourceFields = getFetchSourceFields(request);
        GetRequestBuilder requestBuilder = transportClient.prepareGet()
            .setIndex(request.getIndex())
            .setType(request.getIndex())
            .setId(id)
            .setFetchSource(fetchSourceFields.getLeft(), fetchSourceFields.getRight());
        LOG.debug("Query getById {}", requestBuilder != null ? requestBuilder.request() : null);

        return requestBuilder;
    }

    @Override
    @Trace
    public SearchRequestBuilder query(FilterableApiRequest request) {
        return prepareQuery(request, (searchBuilder, queryBuilder) -> buildQueryByFilterableApiRequest(request, searchBuilder, queryBuilder));
    }

    @Override
    @Trace
    public SearchRequestBuilder query(SearchApiRequest request) {
        return prepareQuery(request, (searchBuilder, queryBuilder) -> buildQueryBySearchApiRequest(request, searchBuilder, queryBuilder));
    }

    private SearchRequestBuilder prepareQuery(BaseApiRequest request, BiConsumer<SearchRequestBuilder, BoolQueryBuilder> builder) {
        settingsAdapter.checkIndex(request);
        SearchRequestBuilder searchBuilder = transportClient.prepareSearch(request.getIndex());

        BoolQueryBuilder queryBuilder = boolQuery();
        builder.accept(searchBuilder, queryBuilder);
        searchBuilder.setQuery(queryBuilder);

        LOG.debug("Request: {} - Query: {}", request, searchBuilder);
        return searchBuilder;
    }

    private void buildQueryByFilterableApiRequest(FilterableApiRequest request, SearchRequestBuilder searchBuilder, BoolQueryBuilder queryBuilder) {
        applyPage(searchBuilder, request);
        addFieldList(searchBuilder, request);
        applySort(searchBuilder, request);
        applyQueryString(queryBuilder, request);
        applyFilterQuery(queryBuilder, request);
    }

    private void buildQueryBySearchApiRequest(SearchApiRequest request, SearchRequestBuilder searchBuilder, BoolQueryBuilder queryBuilder) {
        buildQueryByFilterableApiRequest(request, searchBuilder, queryBuilder);
        applyFacets(searchBuilder, request);
    }

    private void applyPage(SearchRequestBuilder searchBuilder, FilterableApiRequest request) {
        String index = request.getIndex();
        request.setPaginationValues(ES_DEFAULT_SIZE.getValue(index), ES_MAX_SIZE.getValue(index));
        searchBuilder.setFrom(request.getFrom());
        searchBuilder.setSize(request.getSize());
    }

    private void applyFilterQuery(BoolQueryBuilder queryBuilder, final Filterable filter) {
        ofNullable(filter.getFilter()).ifPresent(f -> applyFilterQuery(queryBuilder, QueryParser.parse(f), filter.getIndex(), newHashMap()));
    }

    private void applyFilterQuery(BoolQueryBuilder queryBuilder, final QueryFragment queryFragment, final String indexName, Map<String, BoolQueryBuilder> nestedQueries) {
        if (queryFragment != null && queryFragment instanceof QueryFragmentList) {
            QueryFragmentList queryFragmentList = (QueryFragmentList) queryFragment;
            LogicalOperator logicalOperator = AND;

            for (int index = 0; index < queryFragmentList.size(); index++) {
                QueryFragment queryFragmentFilter = queryFragmentList.get(index);

                if (queryFragmentFilter instanceof QueryFragmentList) {
                    BoolQueryBuilder recursiveQueryBuilder = boolQuery();
                    logicalOperator = getLogicalOperatorByQueryFragmentList(queryFragmentList, index, logicalOperator);
                    addFilterQuery(queryBuilder, recursiveQueryBuilder, logicalOperator, isNotBeforeCurrentQueryFragment(queryFragmentList, index), false, null, nestedQueries);
                    applyFilterQuery(recursiveQueryBuilder, queryFragmentFilter, indexName, newHashMap());

                } else if (queryFragmentFilter instanceof QueryFragmentItem) {
                    QueryFragmentItem queryFragmentItem = (QueryFragmentItem) queryFragmentFilter;
                    Filter filter = queryFragmentItem.getFilter();

                    String fieldName = filter.getField().getName();
                    String fieldFirstName = filter.getField().firstName();
                    settingsAdapter.checkFieldName(indexName, fieldName, false);
                    boolean nested = settingsAdapter.isTypeOf(indexName, fieldFirstName, FIELD_TYPE_NESTED);

                    final boolean not = isNotBeforeCurrentQueryFragment(queryFragmentList, index);
                    logicalOperator = getLogicalOperatorByQueryFragmentList(queryFragmentList, index, logicalOperator);
                    RelationalOperator operator = filter.getRelationalOperator();

                    if (!filter.getValue().isEmpty()) {
                        Value filterValue = filter.getValue();

                        switch (operator) {
                            case DIFFERENT:
                                addFilterQuery(queryBuilder, matchQuery(fieldName, filterValue.value()), logicalOperator, !not, nested, fieldFirstName, nestedQueries);
                                break;

                            case EQUAL:
                                addFilterQuery(queryBuilder, matchQuery(fieldName, filterValue.value()), logicalOperator, not, nested, fieldFirstName, nestedQueries);
                                break;

                            case RANGE:
                                addFilterQuery(queryBuilder, rangeQuery(fieldName).from(filterValue.value(0)).to(filterValue.value(1)).includeLower(true).includeUpper(true), logicalOperator, not, nested, fieldFirstName, nestedQueries);
                                break;

                            case GREATER:
                                addFilterQuery(queryBuilder, rangeQuery(fieldName).from(filterValue.value()).includeLower(false), logicalOperator, not, nested, fieldFirstName, nestedQueries);
                                break;

                            case GREATER_EQUAL:
                                addFilterQuery(queryBuilder, rangeQuery(fieldName).from(filterValue.value()).includeLower(true), logicalOperator, not, nested, fieldFirstName, nestedQueries);
                                break;

                            case LESS:
                                addFilterQuery(queryBuilder, rangeQuery(fieldName).to(filterValue.value()).includeUpper(false), logicalOperator, not, nested, fieldFirstName, nestedQueries);
                                break;

                            case LESS_EQUAL:
                                addFilterQuery(queryBuilder, rangeQuery(fieldName).to(filterValue.value()).includeUpper(true), logicalOperator, not, nested, fieldFirstName, nestedQueries);
                                break;

                            case LIKE:
                                if(!settingsAdapter.isTypeOf(indexName, fieldName, FIELD_TYPE_KEYWORD))
                                    throw new UnsupportedFieldException(fieldName, settingsAdapter.getFieldType(indexName, fieldName), FIELD_TYPE_KEYWORD.toString(), LIKE);

                                addFilterQuery(queryBuilder, wildcardQuery(fieldName, filter.getValue().first()), logicalOperator, not, nested, fieldFirstName, nestedQueries);
                                break;

                            case IN:
                                Object[] values = filterValue.stream().map(contents -> ((com.vivareal.search.api.model.query.Value) contents).value(0)).toArray();
                                addFilterQuery(queryBuilder, termsQuery(fieldName, values), logicalOperator, not, nested, fieldFirstName, nestedQueries);
                                break;

                            case POLYGON:
                                if(!settingsAdapter.isTypeOf(indexName, fieldName, FIELD_TYPE_GEOPOINT))
                                    throw new UnsupportedFieldException(fieldName, settingsAdapter.getFieldType(indexName, fieldName), FIELD_TYPE_GEOPOINT.toString(), POLYGON);

                                List<GeoPoint> points = filterValue
                                    .stream()
                                    .map(point -> new GeoPoint(((Value) point).<Double>value(1), ((Value) point).<Double>value(0)))
                                    .collect(toList());

                                addFilterQuery(queryBuilder, geoPolygonQuery(fieldName, points), logicalOperator, not, nested, fieldFirstName, nestedQueries);
                                break;

                            case VIEWPORT:
                                if(!settingsAdapter.isTypeOf(indexName, fieldName, FIELD_TYPE_GEOPOINT))
                                    throw new UnsupportedFieldException(fieldName, settingsAdapter.getFieldType(indexName, fieldName), FIELD_TYPE_GEOPOINT.toString(), VIEWPORT);

                                GeoPoint topRight = new GeoPoint(filterValue.value(0, 1), filterValue.value(0, 0));
                                GeoPoint bottomLeft = new GeoPoint(filterValue.value(1, 1), filterValue.value(1, 0));

                                addFilterQuery(queryBuilder, geoBoundingBoxQuery(fieldName).setCornersOGC(bottomLeft, topRight), logicalOperator, not, nested, fieldFirstName, nestedQueries);
                                break;

                            default:
                                throw new UnsupportedOperationException("Unknown Relational Operator " + operator.name());
                        }
                    } else {
                        addFilterQuery(queryBuilder, existsQuery(fieldName), logicalOperator, DIFFERENT.equals(operator) == not, nested, fieldFirstName, nestedQueries);
                    }
                }
            }
        }
    }

    private void addFilterQuery(BoolQueryBuilder boolQueryBuilder, final QueryBuilder queryBuilder, final LogicalOperator logicalOperator, final boolean not, final boolean nested, final String fieldFirstName, Map<String, BoolQueryBuilder> nestedQueries) {
        Optional<QueryBuilder> optionalQuery = (nested ? buildNestedQuery(fieldFirstName, queryBuilder, logicalOperator, not, nestedQueries) : ofNullable(queryBuilder));
        optionalQuery.ifPresent(query -> addFilterQueryByLogicalOperator(boolQueryBuilder, query, logicalOperator, not, nested));
    }

    private Optional<QueryBuilder> buildNestedQuery(final String parentFieldName, final QueryBuilder query, final LogicalOperator logicalOperator, final boolean not, Map<String, BoolQueryBuilder> nestedQueries) {
        BoolQueryBuilder boolQueryBuilder;
        Optional<QueryBuilder> nestedQuery = empty();

        if (nestedQueries.containsKey(parentFieldName)) {
            boolQueryBuilder = nestedQueries.get(parentFieldName);
        } else {
            boolQueryBuilder = boolQuery();
            nestedQueries.put(parentFieldName, boolQueryBuilder);
            nestedQuery = of(nestedQuery(parentFieldName, boolQueryBuilder, None));
        }
        addFilterQueryByLogicalOperator(boolQueryBuilder, query, logicalOperator, not, false);
        return nestedQuery;
    }

    private void addFilterQueryByLogicalOperator(BoolQueryBuilder boolQueryBuilder, final QueryBuilder query, final LogicalOperator logicalOperator, final boolean not, final boolean nested) {
        if(logicalOperator.equals(AND)) {
            if (!not || nested)
                boolQueryBuilder.must(query);
            else
                boolQueryBuilder.mustNot(query);
        } else if(logicalOperator.equals(LogicalOperator.OR)) {
            if (!not || nested)
                boolQueryBuilder.should(query);
            else
                boolQueryBuilder.should(boolQuery().mustNot(query));
        }
    }

    private boolean isNotBeforeCurrentQueryFragment(final QueryFragmentList queryFragmentList, final int index) {
        if (index - 1 >= 0) {
            QueryFragment before = queryFragmentList.get(index - 1);
            if (before instanceof QueryFragmentNot) {
                QueryFragmentNot before1 = (QueryFragmentNot) before;
                return before1.isNot();
            }
        }
        return false;
    }

    private LogicalOperator getLogicalOperatorByQueryFragmentList(final QueryFragmentList queryFragmentList, int index, LogicalOperator logicalOperator) {
        if (index + 1 < queryFragmentList.size()) {
            QueryFragment next = queryFragmentList.get(index + 1);
            if (next instanceof QueryFragmentItem)
                return ((QueryFragmentItem) next).getLogicalOperator();

            if (next instanceof QueryFragmentOperator)
                return ((QueryFragmentOperator) next).getOperator();
        }
        return logicalOperator;
    }

    private void applyQueryString(BoolQueryBuilder queryBuilder, final Queryable request) {
        if (!isEmpty(request.getQ())) {
            String indexName = request.getIndex();

            String mm = isEmpty(request.getMm()) ? QS_MM.getValue(indexName) : request.getMm();
            checkMM(mm, request);

            Map<String, AbstractQueryBuilder> queryStringQueries = new HashMap<>();

            QS_DEFAULT_FIELDS.getValue(request.getFields(), indexName).forEach(field -> {
                String[] boostFieldValues = field.split(":");
                String fieldName = boostFieldValues[0];

                if (settingsAdapter.isTypeOf(indexName, fieldName.split("\\.")[0], FIELD_TYPE_NESTED)) {
                    String nestedField = fieldName.split("\\.")[0];

                    if (queryStringQueries.containsKey(nestedField)) {
                        buildQueryStringQuery((QueryStringQueryBuilder) ((NestedQueryBuilder) queryStringQueries.get(nestedField)).query(), indexName, request.getQ(), boostFieldValues, mm);
                    } else {
                        queryStringQueries.put(nestedField, nestedQuery(nestedField, buildQueryStringQuery(null, indexName, request.getQ(), boostFieldValues, mm), None));
                    }
                } else {
                    if (queryStringQueries.containsKey(NOT_NESTED)) {
                        buildQueryStringQuery((QueryStringQueryBuilder) queryStringQueries.get(NOT_NESTED), indexName, request.getQ(), boostFieldValues, mm);
                    } else {
                        queryStringQueries.put(NOT_NESTED, buildQueryStringQuery(null, indexName, request.getQ(), boostFieldValues, mm));
                    }
                }
            });
            queryStringQueries.forEach((nestedPath, nestedQuery) -> queryBuilder.should().add(nestedQuery));
        }
    }

    private QueryStringQueryBuilder buildQueryStringQuery(QueryStringQueryBuilder queryStringQueryBuilder, final String indexName, final String q, final String[] boostFieldValues, final String mm) {
        if (queryStringQueryBuilder == null)
            queryStringQueryBuilder = queryStringQuery(q);

        String fieldName = boostFieldValues[0];

        if (settingsAdapter.isTypeOf(indexName, fieldName, FIELD_TYPE_STRING) && !fieldName.contains(".raw")) {
            fieldName = fieldName.concat(".raw");
        }

        float boost = (boostFieldValues.length == 2 ? Float.parseFloat(boostFieldValues[1]) : 1.0f);
        queryStringQueryBuilder.field(fieldName, boost).minimumShouldMatch(mm).tieBreaker(0.2f).phraseSlop(2);
        return queryStringQueryBuilder;
    }

    private void checkMM(final String mm, final Queryable request) {
        String errorMessage = ("Minimum Should Match (mm) should be a valid integer number (-100 <> +100). Request: " + request.toString());

        if (mm.contains(".") || mm.contains("%") && ((mm.length() - 1) > mm.indexOf('%')))
            throw new NumberFormatException(errorMessage);

        String mmNumber = mm.replace("%", "");

        if (!NumberUtils.isCreatable(mmNumber))
            throw new NumberFormatException(errorMessage);

        int number = NumberUtils.toInt(mmNumber);

        if (number < -100 || number > 100)
            throw new IllegalArgumentException(errorMessage);
    }

    private void applySort(SearchRequestBuilder searchRequestBuilder, final Sortable request) {
        Set<String> value = ES_DEFAULT_SORT.getValue(request.getSort(), request.getIndex());
        if (!isEmpty(value)) {
            Sort sort = SortParser.parse(value.stream().collect(joining(",")));
            sort.forEach(item -> {
                String fieldName = item.getField().getName();
                settingsAdapter.checkFieldName(request.getIndex(), fieldName, false);
                searchRequestBuilder.addSort(fieldName, SortOrder.valueOf(item.getOrderOperator().name()));
            });
        }
    }

    private void applyFacets(SearchRequestBuilder searchRequestBuilder, final Facetable request) {
        Set<String> value = request.getFacets();
        if (!isEmpty(value)) {
            final String indexName = request.getIndex();
            request.setFacetingValues(ES_FACET_SIZE.getValue(indexName));

            final int facetSize = request.getFacetSize();
            final int shardSize = parseInt(valueOf(settingsAdapter.settingsByKey(request.getIndex(), SHARDS)));

            FacetParser.parse(value.stream().collect(joining(","))).forEach(facet -> {
                final String fieldName = facet.getName();
                final String firstName = facet.firstName();
                settingsAdapter.checkFieldName(indexName, fieldName, false);

                AggregationBuilder agg = terms(fieldName)
                    .field(fieldName)
                    .size(facetSize)
                    .shardSize(shardSize)
                    .order(Terms.Order.count(false));

                if (settingsAdapter.isTypeOf(indexName, firstName, FIELD_TYPE_NESTED)) {
                    applyFacetsByNestedFields(searchRequestBuilder, firstName, agg);
                } else {
                    searchRequestBuilder.addAggregation(agg);
                }
            });
        }
    }

    private void applyFacetsByNestedFields(SearchRequestBuilder searchRequestBuilder, final String name, final AggregationBuilder agg) {
        Optional<AggregationBuilder> nestedAgg = ofNullable(searchRequestBuilder.request().source().aggregations())
            .flatMap(builder ->
                builder.getAggregatorFactories()
                .stream()
                .filter(aggregationBuilder -> aggregationBuilder instanceof NestedAggregationBuilder && name.equals(aggregationBuilder.getName()))
                .findFirst()
            );

        if (nestedAgg.isPresent()) {
            nestedAgg.get().subAggregation(agg);
        } else {
            searchRequestBuilder.addAggregation(nested(name, name).subAggregation(agg));
        }
    }

    private void addFieldList(SearchRequestBuilder searchRequestBuilder, final Fetchable request) {
        Pair<String[], String[]> fetchSource = getFetchSourceFields(request);
        searchRequestBuilder.setFetchSource(fetchSource.getLeft(), fetchSource.getRight());
    }

    private Pair<String[], String[]> getFetchSourceFields(Fetchable request) {
        String indexName = request.getIndex();

        String[] includes = SOURCE_INCLUDES.getValue(request.getIncludeFields(), indexName)
            .stream()
            .filter(field -> settingsAdapter.checkFieldName(indexName, field, true))
            .toArray(String[]::new);

        String[] excludes = SOURCE_EXCLUDES.getValue(request.getExcludeFields(), indexName)
            .stream()
            .filter(field -> !contains(includes, field))
            .filter(field -> settingsAdapter.checkFieldName(indexName, field, true))
            .toArray(String[]::new);

        return Pair.of(includes, excludes);
    }
}

package com.vivareal.search.api.model.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vivareal.search.api.model.query.RelationalOperator;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.InternalMappedTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SearchApiResponse {

    private long time;
    private long totalCount;
    private Map<String, Object> result;

    public SearchApiResponse time(final long time) {
        this.time = time;
        return this;
    }

    public SearchApiResponse totalCount(final long totalCount) {
        this.totalCount = totalCount;
        return this;
    }

    public SearchApiResponse result(final String elementName, final Object object) {
        if (this.result == null)
            this.result = new LinkedHashMap<>();

        this.result.put(elementName, object);
        return this;
    }

    public SearchApiResponse facets(final Optional<Aggregations> aggregationsOptional) {
        aggregationsOptional.ifPresent(
            aggregations -> {
                Map<String, Object> facets = new LinkedHashMap<>();
                aggregations.asList().forEach(agg -> facets.put((agg).getName(), addBuckets(((InternalMappedTerms)agg).getBuckets())));
                result("facets", facets);
            }
        );
        return this;
    }


    private static final Set<String> RELATIONAL_OPERATORS = Stream.of(RelationalOperator.getOperators()).sorted((o1, o2) -> {
            int comparationByLength = Integer.compare(o2.length(), o1.length());
            return comparationByLength != 0 ? comparationByLength : o1.compareTo(o2);
        })
        .filter(op -> !StringUtils.isAlphanumeric(op))
        .collect(Collectors.toCollection(LinkedHashSet::new));
    static {
        RELATIONAL_OPERATORS.add(" IN ");
    }
    private static final String RELATIONAL_OPERATOR_SPLITTER = RELATIONAL_OPERATORS.stream().collect(joining("|"));

    private Map<String, Set> filter;
    public Map<String, Set> getFilter() { return filter; }

    public SearchApiResponse filter(final String filterRaw) {
        String[] filterChunks = StringUtils.trimToEmpty(filterRaw).split("( AND | OR | NOT |NOT |\\(|\\))");
        Map<String, Set> requestFilters = new LinkedHashMap<>();
        Stream.of(filterChunks)
            .filter(this::hasRelationalOperator)
            .forEach(filter -> {
                String[] item = filter.split(RELATIONAL_OPERATOR_SPLITTER);
                if(item.length != 2)
                    return;

                String field = item[0], value = item[1], ro = substringAfter(substringBefore(filter, value), field);
                if(!requestFilters.containsKey(field))
                    requestFilters.put(field, new LinkedHashSet());
                requestFilters.get(field).add(ro + " " + value);
            });
        this.filter = requestFilters;
        return this;
    }

    private boolean hasRelationalOperator(String filter) {
        for (String ro : RELATIONAL_OPERATORS) {
            if(filter.contains(ro))
                return true;
        }
        return false;
    }

    private static Map<String, Long> addBuckets(List<?> objBuckets) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        objBuckets.forEach(obj -> {
            if (obj instanceof Terms.Bucket) {
                Terms.Bucket bucket = (Terms.Bucket) obj;
                String key = bucket.getKeyAsString();
                long count = bucket.getDocCount();
                buckets.put(key, count);

            }
        });
        return buckets;
    }

    public long getTime() {
        return time;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public Map<String, Object> getResult() {
        return result;
    }
}

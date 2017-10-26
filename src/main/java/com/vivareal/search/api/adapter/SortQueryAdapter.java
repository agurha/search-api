package com.vivareal.search.api.adapter;

import com.vivareal.search.api.model.parser.SortParser;
import com.vivareal.search.api.model.search.Sortable;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.vivareal.search.api.configuration.environment.RemoteProperties.ES_DEFAULT_SORT;
import static com.vivareal.search.api.model.mapping.MappingType.FIELD_TYPE_NESTED;
import static org.elasticsearch.search.sort.SortBuilders.fieldSort;
import static org.elasticsearch.search.sort.SortOrder.DESC;
import static org.elasticsearch.search.sort.SortOrder.valueOf;

@Component
public class SortQueryAdapter {

    private final SettingsAdapter<Map<String, Map<String, Object>>, String> settingsAdapter;

    @Autowired
    public SortParser sortParser;

    public SortQueryAdapter(@Qualifier("elasticsearchSettings") SettingsAdapter<Map<String, Map<String, Object>>, String> settingsAdapter) {
        this.settingsAdapter = settingsAdapter;
    }

    public void apply(SearchRequestBuilder searchRequestBuilder, final Sortable request) {
        if (request.getSort() != null && "".equals(request.getSort().trim()))
            return;

        sortParser.parse(ES_DEFAULT_SORT.getValue(request.getSort(), request.getIndex())).forEach(item -> {
            String fieldName = item.getField().getName();

            FieldSortBuilder fieldSortBuilder = fieldSort(fieldName).order(valueOf(item.getOrderOperator().name()));
            String parentField = fieldName.split("\\.")[0];

            if (settingsAdapter.isTypeOf(request.getIndex(), parentField, FIELD_TYPE_NESTED))
                fieldSortBuilder.setNestedPath(parentField);

            searchRequestBuilder.addSort(fieldSortBuilder);
        });
        searchRequestBuilder.addSort(fieldSort("_uid").order(DESC));
    }
}

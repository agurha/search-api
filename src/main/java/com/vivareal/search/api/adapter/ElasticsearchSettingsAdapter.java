package com.vivareal.search.api.adapter;

import com.carrotsearch.hppc.cursors.ObjectCursor;
import com.vivareal.search.api.exception.IndexNotFoundException;
import com.vivareal.search.api.exception.InvalidFieldException;
import com.vivareal.search.api.exception.PropertyNotFoundException;
import com.vivareal.search.api.model.mapping.MappingType;
import com.vivareal.search.api.model.search.Fetchable;
import com.vivareal.search.api.model.search.Indexable;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newConcurrentMap;
import static com.vivareal.search.api.configuration.environment.RemoteProperties.SOURCE_EXCLUDES;
import static com.vivareal.search.api.configuration.environment.RemoteProperties.SOURCE_INCLUDES;
import static com.vivareal.search.api.utils.FlattenMapUtils.flat;
import static java.lang.String.valueOf;
import static java.util.Arrays.stream;
import static org.apache.commons.lang3.ArrayUtils.contains;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

@Scope(SCOPE_SINGLETON)
@Component("elasticsearchSettings")
public class ElasticsearchSettingsAdapter implements SettingsAdapter<Map<String, Map<String, Object>>, String> {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchSettingsAdapter.class);

    public static final String SHARDS = "index.number_of_shards";
    public static final String REPLICAS = "index.number_of_replicas";

    private Map<String, Map<String, Object>> structuredIndices;

    private final ESClient esClient;

    private final Map<String, String[]> defaultSourceIncludes;
    private final Map<String, String[]> defaultSourceExcludes;

    @Autowired
    public ElasticsearchSettingsAdapter(ESClient esClient) {
        this.esClient = esClient;
        this.structuredIndices = new HashMap<>();

        defaultSourceIncludes = new HashMap<>();
        defaultSourceExcludes = new HashMap<>();
    }

    @Override
    public Map<String, Map<String, Object>> settings() {
        return structuredIndices;
    }

    @Override
    public String settingsByKey(final String index, final String property) {
        if (!structuredIndices.get(index).containsKey(property))
            throw new PropertyNotFoundException(property, index);

        return valueOf(structuredIndices.get(index).get(property));
    }

    @Override
    public void checkIndex(final Indexable request) {
        if (!structuredIndices.containsKey(request.getIndex()))
            throw new IndexNotFoundException(request.getIndex());
    }

    @Override
    public boolean checkFieldName(final String index, final String fieldName, final boolean acceptAsterisk) {
        if (acceptAsterisk && "*".equals(fieldName))
            return true;

        if (!structuredIndices.get(index).containsKey(fieldName))
            throw new InvalidFieldException(fieldName, index);

        return true;
    }

    @Override
    public String getFieldType(final String index, final String fieldName) {
        checkFieldName(index, fieldName, false);
        return valueOf(structuredIndices.get(index).get(fieldName));
    }

    @Override
    public boolean isTypeOf(final String index, final String fieldName, final MappingType type) {
        return type.typeOf(getFieldType(index, fieldName));
    }

    @Scheduled(fixedRateString = "${es.settings.refresh.rate.ms}", initialDelayString = "${es.settings.refresh.initial.ms}")
    private void getSettingsInformationFromCluster() {
        GetIndexResponse getIndexResponse = esClient.getIndexResponse();
        Map<String, Map<String, Object>> structuredIndicesAux = new HashMap<>();

        stream(getIndexResponse.getIndices())
        .filter(index -> !startsWith(index, "."))
        .forEach(index -> {
            Map<String, Object> indexInfo = newConcurrentMap();
            indexInfo.putAll(getIndexResponse.getSettings().get(index).filter(newArrayList(SHARDS, REPLICAS)::contains).getAsMap());

            ImmutableOpenMap<String, MappingMetaData> immutableIndexMapping = getIndexResponse.getMappings().get(index);
            immutableIndexMapping.keys().iterator().forEachRemaining(obj -> getMappingFromType(obj, indexInfo, immutableIndexMapping, index));

            structuredIndicesAux.put(index, indexInfo);
        }
        );

        if (!structuredIndicesAux.isEmpty()) {
            structuredIndices = structuredIndicesAux;
        }

        LOG.debug("Refresh getting information from cluster settings executed with success");

        refreshDefaultSourceFields();
    }

    private void getMappingFromType(ObjectCursor<String> stringObjectCursor, Map<String, Object> indexInfo, ImmutableOpenMap<String, MappingMetaData> immutableIndexMapping, String index) {
        String type = stringObjectCursor.value;
        try {
            indexInfo.putAll(flat(immutableIndexMapping.get(type).getSourceAsMap(), newArrayList("mappings", "properties", "type", "fields", index, type)));
            indexInfo.entrySet().stream()
                .filter(stringObjectEntry -> stringObjectEntry.getKey().contains("."))
                .map(stringObjectEntry -> {
                    Set<String> missingParams = new HashSet<>();
                    StringBuilder composeMissingParams = new StringBuilder();
                    Stream.of(stringObjectEntry.getKey().split("\\."))
                        .forEach(
                            param -> {
                                composeMissingParams.append(param);
                                missingParams.add(composeMissingParams.toString());
                                composeMissingParams.append(".");
                            }
                        );
                    return missingParams;
                })
                .forEach(params -> params
                    .forEach(param -> indexInfo.putIfAbsent(param, "_obj"))
                );
        } catch (Exception e) {
            LOG.error("Error on get mapping from index {} and type {}", index, type, e);
        }
    }

    // TODO: include this behavior into SourceFieldAdapter
    public String[] getFetchSourceIncludeFields(final Fetchable request) {
        return request.getIncludeFields() == null ? defaultSourceIncludes.get(request.getIndex()) : getFetchSourceIncludeFields(request.getIncludeFields(), request.getIndex());
    }

    private String[] getFetchSourceIncludeFields(Set<String> fields, String indexName) {
        return SOURCE_INCLUDES.getValue(fields, indexName)
        .stream()
        .filter(field -> checkFieldName(indexName, field, true))
        .toArray(String[]::new);
    }

    public String[] getFetchSourceExcludeFields(final Fetchable request, String[] includeFields) {
        return request.getExcludeFields() == null && includeFields.length == 0 ? defaultSourceExcludes.get(request.getIndex()) : getFetchSourceExcludeFields(request.getExcludeFields(), includeFields, request.getIndex());
    }

    private String[] getFetchSourceExcludeFields(Set<String> fields, String[] includeFields, String indexName) {
        return SOURCE_EXCLUDES.getValue(fields, indexName)
        .stream()
        .filter(field -> !contains(includeFields, field) && checkFieldName(indexName, field, true))
        .toArray(String[]::new);
    }

    public void refreshDefaultSourceFields() {
        stream(esClient.getIndexResponse().getIndices())
        .filter(index -> !startsWith(index, "."))
        .forEach(index -> {
            String[] includes = getFetchSourceIncludeFields(null, index);
            defaultSourceIncludes.put(index, includes);
            defaultSourceExcludes.put(index, getFetchSourceExcludeFields(null, includes, index));
        });
    }
}

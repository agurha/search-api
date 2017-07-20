package com.vivareal.search.api.configuration.environment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.vivareal.search.api.configuration.environment.RemoteProperties.DEFAULT_INDEX;
import static java.util.Arrays.stream;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * Created by leandropereirapinto on 6/29/17.
 */
@Component
@Scope(SCOPE_SINGLETON)
public class SearchApiEnv {

    private static Logger LOG = LoggerFactory.getLogger(SearchApiEnv.class);

    private static final String SEARCH_API_CONFIG_ENDPOINT = "/search-api-properties/properties/_search";
    private static final Header SEARCH_API_HEADERS = new BasicHeader("Content-Type", "application/json; charset=UTF-8");

    private final RestClient restClient;

    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Object> localProperties = new HashMap<>();
    private Map<String, Object> remoteProperties = new HashMap<>();

    public SearchApiEnv(final Environment env, final RestClient restClient) {
        this.restClient = restClient;

        loadLocalProperties((AbstractEnvironment) env);
        loadRemoteProperties();
    }

    private void loadLocalProperties(AbstractEnvironment environment) {
        StreamSupport.stream(environment.getPropertySources().spliterator(), false)
            .filter(propertySource -> propertySource instanceof MapPropertySource)
            .map(mapPropertySource -> (Map<String, Object>) mapPropertySource.getSource())
            .forEach(sourceMap -> sourceMap
                .forEach((k, v) -> {
                    String propertyValue = String.valueOf(environment.getProperty(k));
                    this.localProperties.put(k, propertyValue);
                }
            ));
        loadEnvironmentProperties(DEFAULT_INDEX, this.localProperties);
    }

    @Scheduled(cron = "${application.properties.refresh.cron}")
    private void loadRemoteProperties() {
        try {
            HttpEntity entity = restClient.performRequest(GET.name(), SEARCH_API_CONFIG_ENDPOINT, SEARCH_API_HEADERS).getEntity();

            if (entity != null) {
                String retSrc = EntityUtils.toString(entity);
                HashMap<String, Object> response = mapper.readValue(retSrc, new TypeReference<HashMap<String, Object>>() {});
                if (!isEmpty(response) && response.containsKey("hits")) {

                    @SuppressWarnings("unchecked")
                    Map hitsMap = (HashMap<String, Object>) response.get("hits");

                    if (hitsMap.containsKey("hits")) {
                        ((List<Map>) hitsMap.get("hits")).forEach(hit -> {
                            Map<String, Object> source = (Map<String, Object>) hit.get("_source");

                            LOG.debug("Remote properties loaded with success. Endpoint: {}", SEARCH_API_CONFIG_ENDPOINT);
                            String index = hit.get("_id").toString();
                            loadEnvironmentProperties(index, source);
                            this.remoteProperties.put(index, source);
                        });
                    }
                }
            }
        } catch (ResponseException e) {
            LOG.error("Error to get response from endpoint {}. ErrorMessage: {}", SEARCH_API_CONFIG_ENDPOINT, e.getMessage());
        } catch (IOException e) {
            LOG.error("Generic error to get response from endpoint {}. ErrorMessage: {}", SEARCH_API_CONFIG_ENDPOINT, e.getMessage());
        }
    }

    private void loadEnvironmentProperties(final String index, final Map<String, Object> properties) {
        stream(RemoteProperties.values())
            .parallel()
            .filter(remoteProperty -> properties.containsKey(remoteProperty.getProperty()))
            .forEach(env -> env.setValue(index, String.valueOf(properties.get(env.getProperty()))));
        LOG.debug("Environment Properties loaded with success");
    }

    public Map<String, Object> getLocalProperties() {
        return localProperties;
    }

    public Map<String, Object> getRemoteProperties() {
        return remoteProperties;
    }
}

package com.vivareal.search.api.service.parser;


import com.vivareal.search.api.adapter.SettingsAdapter;
import com.vivareal.search.api.model.query.Field;
import com.vivareal.search.api.model.search.Indexable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Map;

import static java.util.Objects.isNull;

@RequestScope
@Component
public class IndexValidator {

    private static final Logger LOG = LoggerFactory.getLogger(IndexValidator.class);

    private SettingsAdapter<Map<String, Map<String, Object>>, String> settingsAdapter;
    private String index;

    @Autowired
    public IndexValidator(SettingsAdapter settingsAdapter) {
        this.settingsAdapter = settingsAdapter;
    }

    public void validateIndex(Indexable indexable) {
        settingsAdapter.checkIndex(indexable);
        this.index = indexable.getIndex();
    }

    public void validateFieldName(String fieldName) {
        if(isNull(index)) {
            LOG.warn("Before validate the field, the index must be validated");
        }

        settingsAdapter.checkFieldName(index, fieldName, false);
    }

    public void validateField(Field field) {
        settingsAdapter.checkFieldName(index, field.getName(), false);
    }
}

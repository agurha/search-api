package com.vivareal.search.api.adapter;

import com.vivareal.search.api.model.search.Indexable;

public interface SettingsAdapter<T, U> {

    T settings();

    U settingsByKey(String index, String property);

    void checkIndex(Indexable request);

    String getFieldType(String index, String fieldName);
}

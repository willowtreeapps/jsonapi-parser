package com.birbit.jsonapi;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.util.Map;

public class JsonApiDataMeta {

    private Map<String, Map<String, Object>> dataMeta;

    public JsonApiDataMeta(@NonNull final Map<String, Map<String, Object>> dataMeta) {
        this.dataMeta = dataMeta;
    }

    @NonNull
    public Map<String, Map<String, Object>> getDataMetaMap() {
        return dataMeta;
    }

    @Nullable
    public Map<String, Object> get(@NonNull final String key) {
        return dataMeta.get(key);
    }
}

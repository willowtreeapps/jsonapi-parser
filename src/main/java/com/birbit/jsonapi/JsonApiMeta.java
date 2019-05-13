package com.birbit.jsonapi;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

public class JsonApiMeta {

    private final JsonDeserializationContext context;
    private final JsonObject metaData;

    public JsonApiMeta(@Nullable final JsonDeserializationContext context, @Nullable final JsonObject metaData) {
        this.context = context;
        this.metaData = metaData;
    }

    /**
     * Deserialize the data contained within a {@link JsonApiResponse} to the passed in type
     *
     * @param tClass The type of the object to deseralize to
     * @return The type filled with the data of the meta object
     */
    @Nullable
    public <T> T deserialize(@NonNull Class<T> tClass) {
        if (metaData == null) {
            return null;
        }

        return context.deserialize(metaData, tClass);
    }
}

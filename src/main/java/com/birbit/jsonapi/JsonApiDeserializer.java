/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.birbit.jsonapi;

import com.android.annotations.Nullable;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class JsonApiDeserializer implements JsonDeserializer<JsonApiResponse> {
    Map<String, JsonApiResourceDeserializer<?>> deserializerMap;
    final Map<Class, String> typeMapping;

    public JsonApiDeserializer(JsonApiResourceDeserializer... deserializers) {
        deserializerMap = new HashMap<>((int) (deserializers.length * 1.25));
        typeMapping = new HashMap<>();
        for (JsonApiResourceDeserializer deserializer : deserializers) {
            deserializerMap.put(deserializer.apiType, deserializer);
            String previous = typeMapping.put(deserializer.klass, deserializer.apiType);
            if (previous != null) {
                throw new IllegalArgumentException("multiple types map to klass " + deserializer.klass + ". This is " +
                        "not supported. To workaround it, you can create a class that extends the other one. " +
                        "Conflicting types:" + previous + ", " + deserializer.apiType);
            }
        }

    }

    public JsonApiResponse deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        if (!json.isJsonObject()) {
            throw new JsonParseException("JSON API response root should be a json object");
        }
        if (!(typeOfT instanceof ParameterizedType)) {
            throw new JsonParseException("JSON API response should be requested with a parameterized type where the" +
                    " type parameter represents the `data` field's type");
        }
        ParameterizedType parameterizedType = (ParameterizedType) typeOfT;
        JsonObject jsonObject = json.getAsJsonObject();

        JsonApiLinks links = parseLinks(context, jsonObject);

        Object data = parseData(context, parameterizedType, jsonObject);
        List<JsonApiError> errors = parserErrors(context, jsonObject);
        JsonApiMeta meta = parseMeta(context, jsonObject);
        if ((data == null) == (errors == null)) {
            throw new JsonParseException("The JSON API response should have data or errors");
        }
        if (errors != null) {
            return new JsonApiResponse(errors, meta, typeMapping, links);
        }
        Map<String, Map<String, Object>> included = parseIncluded(context, jsonObject);
        //noinspection unchecked
        return new JsonApiResponse(data, meta, included, typeMapping, links);
    }

    protected List<JsonApiError> parserErrors(JsonDeserializationContext context, JsonObject jsonObject) {
        JsonElement errors = jsonObject.get("errors");
        if (errors == null || !errors.isJsonArray()) {
            return null;
        }
        JsonArray asJsonArray = errors.getAsJsonArray();
        int size = asJsonArray.size();
        List<JsonApiError> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(context.<JsonApiError>deserialize(asJsonArray.get(i), JsonApiError.class));
        }
        return result;
    }

    protected JsonApiLinks parseLinks(JsonDeserializationContext context, JsonObject jsonObject) {
        JsonElement links = jsonObject.get("links");
        if (links == null || !links.isJsonObject()) {
            return JsonApiLinks.EMPTY;
        }
        return context.deserialize(links, JsonApiLinks.class);
    }

    protected JsonApiMeta parseMeta(JsonDeserializationContext context, JsonObject jsonObject) {
        JsonElement metaElm = jsonObject.get("meta");

        if (metaElm != null && metaElm.isJsonObject()) {
            return new JsonApiMeta(context, metaElm.getAsJsonObject());
        }

        return null;
    }

    protected Map<String, Map<String, Object>> parseIncluded(JsonDeserializationContext context, JsonObject jsonObject) {
        JsonElement includedElm = jsonObject.get("included");
        Map<String, Map<String, Object>> included;
        if (includedElm != null && includedElm.isJsonArray()) {
            included = new LinkedHashMap<>();
            JsonArray includedArray = includedElm.getAsJsonArray();
            final int size = includedArray.size();
            for (int i = 0; i < size; i++) {
                ResourceWithIdAndType parsed = parseResource(includedArray.get(i), context);
                if (parsed.resource != null) {
                    Map<String, Object> itemMap = included.get(parsed.apiType);
                    if (itemMap == null) {
                        itemMap = new LinkedHashMap<>();
                        included.put(parsed.apiType, itemMap);
                    }
                    itemMap.put(parsed.id, parsed.resource);
                }
            }
        } else {
            included = Collections.emptyMap();
        }
        return included;
    }

    protected Object parseData(JsonDeserializationContext context, ParameterizedType parameterizedType, JsonObject jsonObject) {
        JsonElement dataElm = jsonObject.get("data");
        if (dataElm != null) {
            Type typeArg = parameterizedType.getActualTypeArguments()[0];
            if (dataElm.isJsonArray()) {
                JsonArray jsonArray = dataElm.getAsJsonArray();
                final int size = jsonArray.size();
                boolean isArray = typeArg instanceof GenericArrayType;
                if (isArray) {
                    TypeToken<?> typeToken = TypeToken.get(typeArg);
                    Object[] result = (Object[]) Array.newInstance(typeToken.getRawType().getComponentType(), size);
                    for (int i = 0; i < size; i++) {
                        ResourceWithIdAndType resourceWithIdAndType = parseResource(jsonArray.get(i), context);
                        result[i] = resourceWithIdAndType.resource;
                    }
                    return result;
                } else {
                    List result = new ArrayList(size);
                    for (int i = 0; i < size; i++) {
                        ResourceWithIdAndType resourceWithIdAndType = parseResource(jsonArray.get(i), context);
                        //noinspection unchecked
                        result.add(resourceWithIdAndType.resource);
                    }
                    return result;
                }
            } else if (dataElm.isJsonObject()) {
                return parseResource(dataElm, context).resource;
            }
        }
        return null;
    }

    protected ResourceWithIdAndType parseResource(JsonElement jsonElement, JsonDeserializationContext context) {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        String apiType = jsonObject.get("type").getAsString();
        String id = jsonObject.get("id").getAsString();
        // Added by WillowTree
        JsonApiDataMeta dataMeta = parseDataMeta(jsonObject.get("meta"));
        JsonApiResourceDeserializer<?> deserializer = deserializerMap.get(apiType);
        Object resource;
        if (deserializer != null) {
            resource = deserializer.deserialize(id, dataMeta, jsonElement, context);
        } else {
            resource = null;
        }
        return new ResourceWithIdAndType(apiType, id, dataMeta, resource);
    }

    // Added by WillowTree

    /**
     * This method is here to handle a specific case when there is a "meta" object
     * contained within the top level "data" object in the JSON response.
     * <p/>
     * Note: This <strong>does not</strong> conform to the JsonApi spec!
     */
    @Nullable
    protected JsonApiDataMeta parseDataMeta(JsonElement jsonElement) {
        if (jsonElement == null || jsonElement.isJsonNull() || !jsonElement.isJsonObject()) {
            return null;
        }

        final JsonObject jsonObject = jsonElement.getAsJsonObject();
        final Map<String, Map<String, Object>> dataMetaMap = new HashMap<>();
        for (String key : jsonObject.keySet()) {
            if (jsonObject.get(key).isJsonObject()) {
                final JsonObject inner = jsonObject.get(key).getAsJsonObject();
                final Map<String, Object> stringMap = new HashMap<>();
                for (String oKey : inner.keySet()) {
                    if (inner.get(oKey).isJsonArray()) {
                        stringMap.put(oKey, inner.get(oKey).getAsJsonArray());
                    } else {
                        stringMap.put(oKey, inner.get(oKey).getAsString());
                    }
                }
                dataMetaMap.put(key, stringMap);
            }
        }
        return new JsonApiDataMeta(dataMetaMap);
    }

    public static GsonBuilder register(GsonBuilder builder, JsonApiResourceDeserializer... deserializers) {
        return builder.registerTypeAdapter(JsonApiResponse.class, new JsonApiDeserializer(deserializers))
                .registerTypeAdapter(JsonApiLinks.class, JsonApiLinksDeserializer.INSTANCE);
    }

    static class ResourceWithIdAndType {
        final String apiType;
        final String id;
        final JsonApiDataMeta dataMeta; // Added by WillowTree
        final Object resource;

        public ResourceWithIdAndType(String apiType, String id, JsonApiDataMeta dataMeta, Object resource) {
            this.apiType = apiType;
            this.id = id;
            this.dataMeta = dataMeta;
            this.resource = resource;
        }
    }
}
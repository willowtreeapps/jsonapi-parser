package com.birbit.jsonapi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is here to handle a specific case when there is a "meta" object
 * contained within the top level "data" object in the JSON response.
 * <p />
 * Note: This <strong>does not</strong> conform to the JsonApi spec!
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface DataMeta {
    // Added by WillowTree
}

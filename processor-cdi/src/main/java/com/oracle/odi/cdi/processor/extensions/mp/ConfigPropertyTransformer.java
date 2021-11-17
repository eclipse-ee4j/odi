/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oracle.odi.cdi.processor.extensions.mp;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.inject.annotation.NamedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * A {@link io.micronaut.inject.annotation.NamedAnnotationTransformer} that adds support for MicroProfile config.
 */
public class ConfigPropertyTransformer implements NamedAnnotationTransformer {
    @Override
    public String getName() {
        return "org.eclipse.microprofile.config.inject.ConfigProperty";
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final String defaultValue = annotation.stringValue("defaultValue").orElse("");
        return Collections.singletonList(
                AnnotationValue.builder(Property.class)
                    .member("name", annotation.stringValue("name").orElse(""))
                    .member("defaultValue", defaultValue)
                    .stereotype(AnnotationValue.builder(Bindable.class).member("defaultValue", defaultValue).build())
                    .build()
        );
    }
}

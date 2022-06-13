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
package org.eclipse.odi.cdi.annotation.meta;

import java.lang.annotation.Annotation;
import java.util.Set;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;

/**
 * Models the runtime representation of a meta-annotation.
 */
@Indexed(RuntimeMetaAnnotation.class)
public class RuntimeMetaAnnotation {
    private final Class<? extends Annotation> annotationType;
    private final Set<String> nonBinding;
    private final MetaAnnotationKind kind;
    private AnnotationMetadata annotationMetadata = AnnotationMetadata.EMPTY_METADATA;

    public RuntimeMetaAnnotation(Class<? extends Annotation> annotationType,
                                 Set<String> nonBinding,
                                 MetaAnnotationKind kind) {
        this.annotationType = annotationType;
        this.nonBinding = nonBinding;
        this.kind = kind;
    }

    /**
     * @return The annotation metadata.
     */
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    /**
     * Sets the annotation metadata.
     * @param annotationMetadata The annotation metadata
     */
    public void setAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata != null) {
            this.annotationMetadata = annotationMetadata;
        }
    }

    /**
     * @return The annotation type
     */
    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }

    /**
     * @return The non-binding members
     */
    public Set<String> getNonBinding() {
        return nonBinding;
    }

    /**
     * @return The kind of meta annotation it is
     */
    public MetaAnnotationKind getKind() {
        return kind;
    }

    /**
     * Different meta annotation kinds.
     */
    public enum MetaAnnotationKind {
        NORMAL_SCOPE,
        SCOPE,
        QUALIFIER,
        STEREOTYPE,
        INTERCEPTOR_BINDING
    }

    /**
     * The synthetic creator.
     */
    public static final class Creator implements SyntheticBeanCreator<RuntimeMetaAnnotation> {

        @Override
        public RuntimeMetaAnnotation create(Instance<Object> lookup, Parameters params) {
            final Class<? extends Annotation> annotationType = params.get("annotationType", Class.class);
            final MetaAnnotationKind kind = params.get("kind", MetaAnnotationKind.class);
            String[] nonBindings = params.get("nonBinding", String[].class);
            if (nonBindings == null) {
                nonBindings = StringUtils.EMPTY_STRING_ARRAY;
            }
            if (annotationType == null) {
                throw new IllegalStateException("Invalid runtime meta annotation state. Report a bug.");
            }
            if (kind == null) {
                throw new IllegalStateException("Invalid runtime meta annotation state. Report a bug.");
            }
            return new RuntimeMetaAnnotation(
                    annotationType,
                    CollectionUtils.setOf(nonBindings),
                    kind
            );
        }
    }
}

/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package org.eclipse.odi.cdi;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.inject.Default;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Implementation of {@link Default} qualifier.
 *
 * @param <T> The qualified type
 * @author Denis Stepanov
 */
@Internal
public final class DefaultQualifier<T> implements Qualifier<T> {

    public static final DefaultQualifier<?> INSTANCE = new DefaultQualifier<>();

    private static final io.micronaut.context.Qualifier DEFAULT_QUALIFIER = Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, Default.class);

    private DefaultQualifier() {
    }

    public static <T> Qualifier<T> instance() {
        return (Qualifier<T>) INSTANCE;
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> {
            if (!beanType.isAssignableFrom(candidate.getBeanType())) {
                return false;
            }
            if (candidate instanceof BeanDefinition) {
                Qualifier<?> declaredQualifier = ((BeanDefinition<?>) candidate).getDeclaredQualifier();
                return declaredQualifier == null
                        || declaredQualifier.contains(DEFAULT_QUALIFIER)
                        || declaredQualifier instanceof io.micronaut.core.naming.Named; // CDI applies @Default also on @Named
            }
            return false;
        });
    }

    private Qualifier<T> getDeclaredQualifier(AnnotationMetadata annotationMetadata) {
        annotationMetadata = annotationMetadata.getDeclaredMetadata();
        final List<String> annotations = annotationMetadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER);
        if (CollectionUtils.isNotEmpty(annotations)) {
            if (annotations.size() == 1) {
                final String annotation = annotations.iterator().next();
                if (annotation.equals(Qualifier.PRIMARY)) {
                    // primary is the same as null
                    return null;
                }
//                AnnotationValue<Annotation> declaredAnnotation = annotationMetadata.getDeclaredAnnotation(annotation);
//                if (declaredAnnotation != null) {
//                    return Qualifiers.
//                }
                return Qualifiers.byAnnotation(annotationMetadata, annotation);
            } else {
                Qualifier<T>[] qualifiers = new Qualifier[annotations.size()];
                int i = 0;
                for (String name : annotations) {
                    qualifiers[i++] = Qualifiers.byAnnotation(annotationMetadata, name);
                }
                return Qualifiers.byQualifiers(qualifiers);
            }
        }
        return null;
    }

    @Override
    public boolean contains(Qualifier<T> qualifier) {
        if (DEFAULT_QUALIFIER.contains(qualifier)) {
            return true;
        }
        return Qualifier.super.contains(qualifier);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(toString());
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public String toString() {
        return "@Default";
    }
}

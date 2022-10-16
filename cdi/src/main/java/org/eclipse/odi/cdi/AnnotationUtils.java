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
package org.eclipse.odi.cdi;

import org.eclipse.odi.cdi.annotation.reflect.AnnotationReflection;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.literal.NamedLiteral;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The internal annotation utils class.
 */
@Internal
public final class AnnotationUtils {

    private AnnotationUtils() {
    }

    /**
     * Creates new {@link java.lang.annotation.Annotation} instances from all qualifiers.
     * @param annotationMetadata The annotation metadata, never {@code null}
     * @return The synthesized annotations
     */
    public static Set<Annotation> synthesizeQualifierAnnotations(AnnotationMetadata annotationMetadata, ClassLoader classLoader) {
        return annotationMetadata
                .getAnnotationNamesByStereotype(MetaAnnotationSupport.META_ANNOTATION_QUALIFIER)
                .stream()
                .map(name -> {
                    if (name.equals(Any.NAME)) {
                        return jakarta.enterprise.inject.Any.Literal.INSTANCE;
                    }
                    if (name.equals(Default.class.getName())) {
                        return Default.Literal.INSTANCE;
                    }
                    if (name.equals(MetaAnnotationSupport.META_ANNOTATION_NAMED)) {
                        return NamedLiteral.of(annotationMetadata.stringValue(AnnotationUtil.NAMED).get());
                    } else {
                        final Class<? extends Annotation> annotationClass = annotationMetadata.getAnnotationType(name, classLoader)
                                .orElse(null);
                        if (annotationClass != null) {
                            return annotationMetadata.synthesize(annotationClass);
                        }
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Creates a qualifier from the array of {@link Annotation}.
     * @param annotations The annotations
     * @param <U> The qualifier type
     * @return The qualifier
     */
    @Nullable
    public static <U> Qualifier<U> qualifierFromQualifierAnnotations(@Nullable Annotation[] annotations) {
        if (annotations == null || annotations.length == 0) {
            return null;
        }
        AnnotationMetadata annotationMetadata = annotationMetadataFromQualifierAnnotations(annotations);
        return qualifierFromQualifierAnnotations(annotationMetadata, annotations);
    }

    /**
     * Convert the annotations array into {@link AnnotationMetadata} instance.
     * @param annotations The annotations
     * @return an instance of {@link AnnotationMetadata}
     */
    public static AnnotationMetadata annotationMetadataFromQualifierAnnotations(Annotation[] annotations) {
        if (annotations.length == 0) {
            return AnnotationMetadata.EMPTY_METADATA;
        }
        MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
        for (Annotation annotation : annotations) {
            if (isQualifier(annotation)) {
                if (isAny(annotation)) {
                    annotationMetadata.addDeclaredAnnotation(Any.NAME, Collections.emptyMap());
                    annotationMetadata.addDeclaredStereotype(
                            List.of(Any.NAME),
                            MetaAnnotationSupport.META_ANNOTATION_QUALIFIER, Collections.emptyMap()
                    );
                } else {
                    AnnotationValue<Annotation> value = AnnotationReflection.toAnnotationValue(annotation);
                    annotationMetadata.addDeclaredAnnotation(value.getAnnotationName(), value.getValues());
                    annotationMetadata.addDeclaredStereotype(
                            List.of(value.getAnnotationName()),
                            MetaAnnotationSupport.META_ANNOTATION_QUALIFIER, value.getValues()
                    );
                }
            }
        }
        return annotationMetadata;
    }

    /**
     * Creates a qualifier from the array of {@link Annotation}.
     * @param annotationMetadata The annotation metadata
     * @param annotations The annotations
     * @param <U> The qualifier type
     * @return The qualifier
     */
    public static <U> Qualifier<U> qualifierFromQualifierAnnotations(
            AnnotationMetadata annotationMetadata,
            Annotation... annotations) {
        if (annotations.length > 0) {
            if (annotations.length == 1) {
                Annotation annotation = annotations[0];
                if (isQualifier(annotation)) {
                    checkValidAnnotation(annotation, annotations);
                    return (Qualifier<U>) byAnnotation(annotationMetadata, annotation.annotationType());
                }
            } else {
                Qualifier[] qualifiers = new Qualifier[annotations.length];
                for (int i = 0; i < annotations.length; i++) {
                    Annotation annotation = annotations[i];
                    if (isQualifier(annotation)) {
                        checkValidAnnotation(annotation, annotations);
                        qualifiers[i] = byAnnotation(annotationMetadata, annotation.annotationType());
                    }
                }
                return Qualifiers.byQualifiers(qualifiers);
            }
        }
        return null;
    }

    static <T extends Annotation> Qualifier<T> byAnnotation(AnnotationMetadata annotationMetadata, Class<T> annotation) {
        if (isAny(annotation)) {
            //noinspection unchecked
            return AnyQualifier.INSTANCE;
        }
        return Qualifiers.byAnnotation(annotationMetadata, annotation);
    }

    public static <T extends Annotation> boolean isAny(Class<T> annotation) {
        return annotation == jakarta.enterprise.inject.Any.class;
    }

    static <T extends Annotation> boolean isAny(T annotation) {
        return findAnnotationClass(annotation) == jakarta.enterprise.inject.Any.class;
    }

    private static boolean isQualifier(Annotation annotation) {
        return findAnnotationClass(annotation).isAnnotationPresent(jakarta.inject.Qualifier.class);
    }

    private static void checkValidAnnotation(Annotation annotation, Annotation[] allAnnotations) {
        Class<? extends Annotation> annotationClass = findAnnotationClass(annotation);
        for (Annotation ann : allAnnotations) {
            if (ann == annotation) {
                continue;
            }
            Class<? extends Annotation> annAnnotationClass = findAnnotationClass(ann);
            if (annAnnotationClass.equals(annotationClass)) {
                throw new IllegalArgumentException("Duplicate annotation detected: " + annAnnotationClass);
            }
        }
        if (!annotationClass.isAnnotationPresent(jakarta.inject.Qualifier.class)) {
            throw new IllegalArgumentException("Incorrect annotation: " + annotation.annotationType());
        }
        Retention retention = annotationClass.getAnnotation(Retention.class);
        if (retention.value() != RetentionPolicy.RUNTIME) {
            throw new IllegalArgumentException("Incorrect annotation retention: " + retention.value());
        }
    }

    static Class<? extends Annotation> findAnnotationClass(Annotation annotation) {
        if (annotation.annotationType().isAnnotation()) {
            return annotation.annotationType();
        }
        throw new IllegalArgumentException("Cannot find annotation class for: " + annotation.annotationType());
    }
}

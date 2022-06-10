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
package com.oracle.odi.cdi;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.odi.cdi.annotation.reflect.AnnotationReflection;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Singleton;

import static com.oracle.odi.cdi.AnnotationUtils.byAnnotation;

@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
@Singleton
final class OdiAnnotationsImpl implements OdiAnnotations {
    private final BeanProvider<RuntimeMetaAnnotations> metaAnnotations;

    OdiAnnotationsImpl(BeanProvider<RuntimeMetaAnnotations> metaAnnotations) {
        this.metaAnnotations = metaAnnotations;
    }

    @Override
    public boolean isDependent(Class<? extends Annotation> annotationType) {
        return annotationType == Dependent.class;
    }

    @Override
    public boolean isScope(Class<? extends Annotation> annotationType) {
        return metaAnnotations.get().isScope(annotationType);
    }

    @Override
    public boolean isNormalScope(Class<? extends Annotation> annotationType) {
        return metaAnnotations.get().isNormalScope(annotationType);
    }

    @Override
    public boolean isQualifier(Class<? extends Annotation> annotationType) {
        return metaAnnotations.get().isQualifier(annotationType);
    }

    @Override
    public boolean isInterceptorBinding(Class<? extends Annotation> annotationType) {
        return metaAnnotations.get().isInterceptorBinding(annotationType);
    }

    @Override
    public boolean isStereotype(Class<? extends Annotation> annotationType) {
        return metaAnnotations.get().isStereotype(annotationType);
    }

    @Override
    @Nullable
    public <T1> Qualifier<T1> resolveQualifier(Annotation... annotations) {
        if (annotations == null || annotations.length == 0) {
            return null;
        }
        AnnotationMetadata annotationMetadata = annotationMetadataFromQualifierAnnotations(annotations);
        return qualifierFromQualifierAnnotations(annotationMetadata, annotations);
    }

    /**
     * Creates a qualifier from the array of {@link Annotation}.
     * @param annotationMetadata The annotation metadata
     * @param annotations The annotations
     * @param <U> The qualifier type
     * @return The qualifier
     */
    @SuppressWarnings("unchecked")
    private <U> Qualifier<U> qualifierFromQualifierAnnotations(
            AnnotationMetadata annotationMetadata,
            Annotation... annotations) {
        if (annotations.length > 0) {
            if (annotations.length == 1) {
                Annotation annotation = annotations[0];
                Class<? extends Annotation> annotationClass = AnnotationUtils.findAnnotationClass(annotation);
                if (isQualifier(annotationClass)) {
                    return (Qualifier<U>) byAnnotation(annotationMetadata, annotation.annotationType());
                } else {
                    throw new IllegalArgumentException("Not a valid qualifier annotation type: " + annotationClass.getName());
                }
            } else {
                Qualifier[] qualifiers = new Qualifier[annotations.length];
                Set<Class<? extends Annotation>> qualifierTypes = new HashSet<>(qualifiers.length);
                for (int i = 0; i < annotations.length; i++) {
                    Annotation annotation = annotations[i];
                    Class<? extends Annotation> annotationClass = AnnotationUtils.findAnnotationClass(annotation);
                    if (!qualifierTypes.add(annotationClass)) {
                        throw new IllegalArgumentException("Qualifier cannot be duplicated for type: " + annotationClass.getName());
                    }
                    if (isQualifier(annotationClass)) {
                        qualifiers[i] = byAnnotation(annotationMetadata, annotation.annotationType());
                    }
                }
                return Qualifiers.byQualifiers(qualifiers);
            }
        }
        return null;
    }

    private AnnotationMetadata annotationMetadataFromQualifierAnnotations(Annotation[] annotations) {
        if (annotations.length == 0) {
            return AnnotationMetadata.EMPTY_METADATA;
        }
        MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
        for (Annotation annotation : annotations) {
            if (metaAnnotations.get().isQualifier(annotation)) {
                if (AnnotationUtils.isAny(annotation)) {
                    annotationMetadata.addDeclaredAnnotation(Any.NAME, Collections.emptyMap());
                    annotationMetadata.addDeclaredStereotype(
                            List.of(Any.NAME),
                            MetaAnnotationSupport.META_ANNOTATION_QUALIFIER, Collections.emptyMap()
                    );
                } else {
                    String[] nonBinding = metaAnnotations.get().getQualifierNonBinding(annotation).toArray(new String[0]);
                    AnnotationValue<Annotation> value = AnnotationReflection.toAnnotationValue(annotation);
                    final Map<CharSequence, Object> values = new LinkedHashMap<>(value.getValues());
                    annotationMetadata.addDeclaredAnnotation(value.getAnnotationName(), values);
                    annotationMetadata.addDeclaredStereotype(
                            List.of(value.getAnnotationName()),
                            MetaAnnotationSupport.META_ANNOTATION_QUALIFIER, Collections.singletonMap(
                                    "nonBinding", nonBinding
                            )
                    );
                }
            }
        }
        return annotationMetadata;
    }
}

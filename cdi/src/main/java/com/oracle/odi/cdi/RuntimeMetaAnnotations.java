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
package com.oracle.odi.cdi;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.odi.cdi.annotation.meta.RuntimeMetaAnnotation;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.AnnotationMetadata;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.inject.Stereotype;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import jakarta.interceptor.InterceptorBinding;

/**
 * Models runtime meta annotations like qualifiers, stereotypes etc.
 */
@Singleton
final class RuntimeMetaAnnotations {
    private final Map<Class<? extends Annotation>, RuntimeMetaAnnotation> normalScopes = new HashMap<>(10);
    private final Map<Class<? extends Annotation>, RuntimeMetaAnnotation> scopes = new HashMap<>(10);
    private final Map<Class<? extends Annotation>, RuntimeMetaAnnotation> stereotypes = new HashMap<>(10);
    private final Map<Class<? extends Annotation>, RuntimeMetaAnnotation> interceptorBindings = new HashMap<>(10);
    private final Map<Class<? extends Annotation>, RuntimeMetaAnnotation> qualifiers = new HashMap<>(10);

    public RuntimeMetaAnnotations(List<BeanRegistration<RuntimeMetaAnnotation>> metaAnnotations) {
        for (BeanRegistration<RuntimeMetaAnnotation> metaAnnotation : metaAnnotations) {
            final RuntimeMetaAnnotation bean = metaAnnotation.getBean();
            final AnnotationMetadata annotationMetadata = metaAnnotation.getAnnotationMetadata();
            bean.setAnnotationMetadata(annotationMetadata);
            switch (bean.getKind()) {
            case QUALIFIER:
                this.qualifiers.put(bean.getAnnotationType(), bean);
                break;
            case STEREOTYPE:
                this.stereotypes.put(bean.getAnnotationType(), bean);
                break;
            case NORMAL_SCOPE:
                this.normalScopes.put(bean.getAnnotationType(), bean);
                break;
            case SCOPE:
                this.scopes.put(bean.getAnnotationType(), bean);
                break;
            case INTERCEPTOR_BINDING:
                this.interceptorBindings.put(bean.getAnnotationType(), bean);
                break;
            default:
                // no-op
            }
        }
    }

    /**
     * Test the given annotation type to determine if it is a {@linkplain jakarta.enterprise.context scope type}.
     *
     * @param annotationType the annotation type
     * @return true if the annotation type is a {@linkplain jakarta.enterprise.context scope type}
     */
    boolean isScope(Class<? extends Annotation> annotationType) {
        if (annotationType != null) {
            return annotationType.isAnnotationPresent(Scope.class) || this.scopes.containsKey(annotationType);
        }
        return false;
    }

    /**
     * Test the given annotation type to determine if it is a {@linkplain jakarta.enterprise.context normal scope type}.
     *
     * @param annotationType the annotation type
     * @return <code>true</code> if the annotation type is a {@linkplain jakarta.enterprise.context normal scope type}
     */
    boolean isNormalScope(Class<? extends Annotation> annotationType) {
        if (annotationType != null) {
            return annotationType.isAnnotationPresent(NormalScope.class) || this.normalScopes.containsKey(annotationType);
        }
        return false;
    }

    /**
     * Test the given annotation type to determine if it is a {@linkplain jakarta.inject.Qualifier qualifier type}.
     *
     * @param annotationType the annotation type
     * @return <code>true</code> if the annotation type is a {@linkplain jakarta.inject.Qualifier qualifier type}
     */
    boolean isQualifier(Class<? extends Annotation> annotationType) {
        if (annotationType != null) {
            return annotationType.isAnnotationPresent(Qualifier.class) || this.qualifiers.containsKey(annotationType);
        }
        return false;
    }

    /**
     * Test the given annotation type to determine if it is a {@linkplain jakarta.enterprise.inject.Stereotype stereotype}.
     *
     * @param annotationType the annotation type
     * @return <code>true</code> if the annotation type is a {@linkplain jakarta.enterprise.inject.Stereotype stereotype}
     */
    boolean isStereotype(Class<? extends Annotation> annotationType) {
        if (annotationType != null) {
            return annotationType.isAnnotationPresent(Stereotype.class) || this.stereotypes.containsKey(annotationType);
        }
        return false;
    }

    /**
     * Test the given annotation type to determine if it is an {@linkplain jakarta.interceptor.InterceptorBinding interceptor
     * binding type} .
     *
     * @param annotationType the annotation to test
     * @return <code>true</code> if the annotation type is a {@linkplain jakarta.interceptor.InterceptorBinding interceptor
     * binding
     * type}
     */
    boolean isInterceptorBinding(Class<? extends Annotation> annotationType) {
        if (annotationType != null) {
            return annotationType.isAnnotationPresent(InterceptorBinding.class) ||
                    this.interceptorBindings.containsKey(annotationType) ||
                    annotationType.isAssignableFrom(io.micronaut.aop.InterceptorBinding.class);
        }
        return false;
    }

    /**
     * Return whether the given annotation is a qualifier.
     * @param annotation The annotation
     * @return True if iti s a qualifier
     */
    public boolean isQualifier(Annotation annotation) {
        if (annotation != null) {
            final Class<? extends Annotation> t = AnnotationUtils.findAnnotationClass(annotation);
            return isQualifier(t);
        }
        return false;
    }

    public Set<String> getQualifierNonBinding(Annotation annotation) {
        if (annotation != null) {
            final Class<? extends Annotation> t = AnnotationUtils.findAnnotationClass(annotation);
            final RuntimeMetaAnnotation runtimeMetaAnnotation = this.qualifiers.get(t);
            if (runtimeMetaAnnotation != null) {
                return runtimeMetaAnnotation.getNonBinding();
            }
        }
        return Collections.emptySet();
    }
}

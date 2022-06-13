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

import java.lang.annotation.Annotation;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Nullable;

/**
 * Interface for methods to resolve qualifiers.
 */
interface OdiAnnotations {
    /**
     * Is the given annotation dependent.
     * @param annotationType The annotation type
     * @return True if it is
     */
    boolean isDependent(Class<? extends Annotation> annotationType);

    /**
     * Test the given annotation type to determine if it is a {@linkplain jakarta.enterprise.context scope type}.
     *
     * @param annotationType the annotation type
     * @return true if the annotation type is a {@linkplain jakarta.enterprise.context scope type}
     */
    boolean isScope(Class<? extends Annotation> annotationType);

    /**
     * Test the given annotation type to determine if it is a {@linkplain jakarta.enterprise.context normal scope type}.
     *
     * @param annotationType the annotation type
     * @return <code>true</code> if the annotation type is a {@linkplain jakarta.enterprise.context normal scope type}
     */
    boolean isNormalScope(Class<? extends Annotation> annotationType);

    /**
     * Test the given annotation type to determine if it is a {@linkplain jakarta.inject.Qualifier qualifier type}.
     *
     * @param annotationType the annotation type
     * @return <code>true</code> if the annotation type is a {@linkplain jakarta.inject.Qualifier qualifier type}
     */
    boolean isQualifier(Class<? extends Annotation> annotationType);

    /**
     * Test the given annotation type to determine if it is a {@linkplain jakarta.enterprise.inject.Stereotype stereotype}.
     *
     * @param annotationType the annotation type
     * @return <code>true</code> if the annotation type is a {@linkplain jakarta.enterprise.inject.Stereotype stereotype}
     */
    boolean isStereotype(Class<? extends Annotation> annotationType);

    /**
     * Test the given annotation type to determine if it is an {@linkplain jakarta.interceptor.InterceptorBinding interceptor
     * binding type} .
     *
     * @param annotationType the annotation to test
     * @return <code>true</code> if the annotation type is a {@linkplain jakarta.interceptor.InterceptorBinding interceptor binding
     * type}
     */
    boolean isInterceptorBinding(Class<? extends Annotation> annotationType);

    /**
     * Resolve a qualifier for the given annotations.
     * @param annotations The annotations
     * @return The Micronaut qualifier, or {@code null} if not present
     * @param <T1> The qualifier bean type
     */
    @Nullable
    <T1> Qualifier<T1> resolveQualifier(Annotation... annotations);
}

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
import java.util.ArrayList;
import java.util.List;

import io.micronaut.core.annotation.AnnotationMetadata;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Stereotype;
import jakarta.inject.Singleton;

final class MetaAnnotationSupport {
    public static final String META_ANNOTATION_SCOPE = "javax.inject.Scope";
    public static final String META_ANNOTATION_SINGLETON = "javax.inject.Singleton";
    public static final String META_ANNOTATION_NAMED = "javax.inject.Named";
    public static final String META_ANNOTATION_QUALIFIER = "javax.inject.Qualifier";

    private MetaAnnotationSupport() {
    }

    static Class<? extends Annotation> resolveDeclaredScope(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA) {
            return Dependent.class;
        }
        final List<String> stereotypes = annotationMetadata.getAnnotationNamesByStereotype(Stereotype.class);
        final List<String> scopeStereotypes = new ArrayList<>(annotationMetadata.getDeclaredAnnotationNamesByStereotype(
                META_ANNOTATION_SCOPE));
        purgeInternalScopes(stereotypes, scopeStereotypes);
        String n = null;
        if (!scopeStereotypes.isEmpty()) {
            n = scopeStereotypes.iterator().next();
        } else {
            scopeStereotypes.addAll(annotationMetadata.getAnnotationNamesByStereotype(META_ANNOTATION_SCOPE));
            purgeInternalScopes(stereotypes, scopeStereotypes);
            if (!scopeStereotypes.isEmpty()) {
                n = scopeStereotypes.iterator().next();
            }
        }
        Class<? extends Annotation> scope;
        if (n == null) {
            scope = annotationMetadata
                    .getAnnotationTypeByStereotype(META_ANNOTATION_SCOPE)
                    .orElse(Dependent.class);
        } else if (META_ANNOTATION_SINGLETON.equals(n)) {
            scope = Singleton.class;
        } else {
            scope = annotationMetadata
                    .getAnnotationType(n)
                    .orElse(Dependent.class);
        }
        return scope;
    }

    private static void purgeInternalScopes(List<String> stereotypes, List<String> scopeStereotypes) {
        // deal with stereotypes with scopes
        scopeStereotypes.removeAll(stereotypes);
        // filter internal annotations
        scopeStereotypes.remove("io.micronaut.runtime.context.scope.ScopedProxy");
    }
}

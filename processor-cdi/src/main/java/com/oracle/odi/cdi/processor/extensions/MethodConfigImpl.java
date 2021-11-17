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
package com.oracle.odi.cdi.processor.extensions;

import java.lang.annotation.Annotation;
import java.util.function.Predicate;

import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.types.Type;

final class MethodConfigImpl implements MethodConfig, ElementAnnotationConfig {
    private final MethodInfoImpl methodInfo;

    MethodConfigImpl(ClassInfoImpl declaringClassInfo,
                            MethodElement element,
                            Types types,
                            VisitorContext visitorContext) {
        this.methodInfo = new MethodInfoImpl(declaringClassInfo, element, types, visitorContext);
    }

    @Override
    public MethodElement getElement() {
        return methodInfo.getElement();
    }

    @Override
    public VisitorContext getVisitorContext() {
        return methodInfo.getVisitorContext();
    }

    @Override
    public MethodInfo info() {
        return methodInfo;
    }

    @Override
    public MethodConfig addAnnotation(Class<? extends Annotation> annotationType) {
        return (MethodConfig) ElementAnnotationConfig.super.addAnnotation(annotationType);
    }

    @Override
    public MethodConfig addAnnotation(AnnotationInfo annotation) {
        return (MethodConfig) ElementAnnotationConfig.super.addAnnotation(annotation);
    }

    @Override
    public MethodConfig addAnnotation(Annotation annotation) {
        return (MethodConfig) ElementAnnotationConfig.super.addAnnotation(annotation);
    }

    @Override
    public MethodConfig removeAnnotation(Predicate<AnnotationInfo> predicate) {
        return (MethodConfig) ElementAnnotationConfig.super.removeAnnotation(predicate);
    }

    @Override
    public MethodConfig removeAllAnnotations() {
        return (MethodConfig) ElementAnnotationConfig.super.removeAllAnnotations();
    }

    @Override
    public boolean isDeclaration() {
        return true;
    }

    @Override
    public boolean isType() {
        return false;
    }

    @Override
    public DeclarationInfo asDeclaration() {
        return methodInfo;
    }

    @Override
    public Type asType() {
        throw new IllegalStateException("Not a type");
    }
}

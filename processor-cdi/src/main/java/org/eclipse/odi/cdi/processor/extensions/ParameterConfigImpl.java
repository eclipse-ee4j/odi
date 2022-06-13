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
package org.eclipse.odi.cdi.processor.extensions;

import java.lang.annotation.Annotation;
import java.util.function.Predicate;

import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.ParameterConfig;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;
import jakarta.enterprise.lang.model.types.Type;

final class ParameterConfigImpl implements ParameterConfig, ElementAnnotationConfig {
    private final ParameterInfoImpl parameterInfo;

    ParameterConfigImpl(ParameterInfoImpl parameterInfo) {
        this.parameterInfo = parameterInfo;
    }

    @Override
    public Element getElement() {
        return parameterInfo.getElement();
    }

    @Override
    public VisitorContext getVisitorContext() {
        return parameterInfo.getVisitorContext();
    }

    @Override
    public ParameterInfo info() {
        return parameterInfo;
    }

    @Override
    public ParameterConfig addAnnotation(Class<? extends Annotation> annotationType) {
        return (ParameterConfig) ElementAnnotationConfig.super.addAnnotation(annotationType);
    }

    @Override
    public ParameterConfig addAnnotation(AnnotationInfo annotation) {
        return (ParameterConfig) ElementAnnotationConfig.super.addAnnotation(annotation);
    }

    @Override
    public ParameterConfig addAnnotation(Annotation annotation) {
        return (ParameterConfig) ElementAnnotationConfig.super.addAnnotation(annotation);
    }

    @Override
    public ParameterConfig removeAnnotation(Predicate<AnnotationInfo> predicate) {
        return (ParameterConfig) ElementAnnotationConfig.super.removeAnnotation(predicate);
    }

    @Override
    public ParameterConfig removeAllAnnotations() {
        return (ParameterConfig) ElementAnnotationConfig.super.removeAllAnnotations();
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
        return parameterInfo;
    }

    @Override
    public Type asType() {
        throw new IllegalStateException("Not a type");
    }
}

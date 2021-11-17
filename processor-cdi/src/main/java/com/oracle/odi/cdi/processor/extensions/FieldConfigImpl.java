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

import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.FieldConfig;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.types.Type;

final class FieldConfigImpl implements FieldConfig, ElementAnnotationConfig {
    private final FieldInfoImpl fieldInfo;

    FieldConfigImpl(ClassInfoImpl declaringInfo,
                           FieldElement element,
                           Types types,
                           VisitorContext visitorContext) {
        this.fieldInfo = new FieldInfoImpl(
                declaringInfo,
                element,
                types,
                visitorContext
        );
    }

    @Override
    public Element getElement() {
        return null;
    }

    @Override
    public VisitorContext getVisitorContext() {
        return null;
    }

    @Override
    public FieldInfo info() {
        return null;
    }

    @Override
    public FieldConfig addAnnotation(Class<? extends Annotation> annotationType) {
        return (FieldConfig) ElementAnnotationConfig.super.addAnnotation(annotationType);
    }

    @Override
    public FieldConfig addAnnotation(AnnotationInfo annotation) {
        return (FieldConfig) ElementAnnotationConfig.super.addAnnotation(annotation);
    }

    @Override
    public FieldConfig addAnnotation(Annotation annotation) {
        return (FieldConfig) ElementAnnotationConfig.super.addAnnotation(annotation);
    }

    @Override
    public FieldConfig removeAnnotation(Predicate<AnnotationInfo> predicate) {
        return (FieldConfig) ElementAnnotationConfig.super.removeAnnotation(predicate);
    }

    @Override
    public FieldConfig removeAllAnnotations() {
        return (FieldConfig) ElementAnnotationConfig.super.removeAllAnnotations();
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
        return fieldInfo;
    }

    @Override
    public Type asType() {
        throw new IllegalStateException("Not a type");
    }
}

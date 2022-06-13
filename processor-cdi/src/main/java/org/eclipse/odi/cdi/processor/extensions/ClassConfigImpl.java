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

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.FieldConfig;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.types.Type;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class ClassConfigImpl implements ClassConfig, ElementAnnotationConfig {

    private final VisitorContext visitorContext;
    private final ClassInfoImpl classInfo;

    ClassConfigImpl(ClassElement element, Types types, VisitorContext visitorContext) {
        this.classInfo = new ClassInfoImpl(element, types, visitorContext);
        this.visitorContext = visitorContext;
    }

    @Override
    public ClassElement getElement() {
        return classInfo.getElement();
    }

    @Override
    public VisitorContext getVisitorContext() {
        return visitorContext;
    }

    @Override
    public Collection<MethodConfig> constructors() {
        return getElement().getEnclosedElements(ElementQuery.of(ConstructorElement.class))
                .stream().map(methodElement ->
                      new MethodConfigImpl(classInfo, methodElement, classInfo.types, visitorContext)
                )
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Collection<MethodConfig> methods() {
        return getElement().getEnclosedElements(ElementQuery.ALL_METHODS)
                .stream().map(methodElement ->
                      new MethodConfigImpl(classInfo, methodElement, classInfo.types, visitorContext)
                )
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Collection<FieldConfig> fields() {
        return getElement().getEnclosedElements(ElementQuery.ALL_FIELDS)
                .stream().map(fieldElement ->
                      new FieldConfigImpl(classInfo, fieldElement, classInfo.types, visitorContext)
                )
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ClassConfigImpl other = (ClassConfigImpl) o;
        return classInfo.equals(other.classInfo);
    }

    @Override
    public int hashCode() {
        return classInfo.hashCode();
    }

    @Override
    public ClassInfo info() {
        return classInfo;
    }

    @Override
    public ClassConfig addAnnotation(Class<? extends Annotation> annotationType) {
        return (ClassConfig) ElementAnnotationConfig.super.addAnnotation(annotationType);
    }

    @Override
    public ClassConfig addAnnotation(AnnotationInfo annotation) {
        return (ClassConfig) ElementAnnotationConfig.super.addAnnotation(annotation);
    }

    @Override
    public ClassConfig addAnnotation(Annotation annotation) {
        return (ClassConfig) ElementAnnotationConfig.super.addAnnotation(annotation);
    }

    @Override
    public ClassConfig removeAnnotation(Predicate<AnnotationInfo> predicate) {
        return (ClassConfig) ElementAnnotationConfig.super.removeAnnotation(predicate);
    }

    @Override
    public ClassConfig removeAllAnnotations() {
        return (ClassConfig) ElementAnnotationConfig.super.removeAllAnnotations();
    }

    @Override
    public boolean isDeclaration() {
        return true;
    }

    @Override
    public boolean isType() {
        return true;
    }

    @Override
    public DeclarationInfo asDeclaration() {
        return classInfo;
    }

    @Override
    public Type asType() {
        return TypeFactory.createType(
                classInfo.getElement(),
                classInfo.types,
                visitorContext
        );
    }
}

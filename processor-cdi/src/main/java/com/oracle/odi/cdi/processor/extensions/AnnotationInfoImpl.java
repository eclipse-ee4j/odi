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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationMember;
import jakarta.enterprise.lang.model.declarations.ClassInfo;

import java.util.Map;
import java.util.stream.Collectors;

class AnnotationInfoImpl implements AnnotationInfo {
    @Nullable
    private final Types types;
    @Nullable
    private final VisitorContext visitorContext;
    private final AnnotationValue<?> annotationValue;

    protected AnnotationInfoImpl(AnnotationValue<?> annotationValue) {
        this(null, annotationValue);
    }

    protected AnnotationInfoImpl(VisitorContext visitorContext, AnnotationValue<?> annotationValue) {
        this.types = visitorContext == null ? null : new TypesImpl(visitorContext);
        this.visitorContext = visitorContext;
        this.annotationValue = annotationValue;
    }

    protected AnnotationInfoImpl(Types types, VisitorContext visitorContext, AnnotationValue<?> annotationValue) {
        this.types = types;
        this.visitorContext = visitorContext;
        this.annotationValue = annotationValue;
    }

    @Override
    public ClassInfo declaration() {
        VisitorContext visitorContext = this.visitorContext == null ? ActiveVisitorContext.currentVisitorContext() : this.visitorContext;
        Types types = this.types == null ? new TypesImpl(visitorContext) : this.types;
        ClassElement classElement = visitorContext.getClassElement(annotationValue.getAnnotationName()).orElse(null);
        if (classElement == null) {
            final String message = "The declaration for annotation ["
                    + annotationValue.getAnnotationName()
                    + "] cannot be retrieved because the type does not exist on the application classpath";
            throw new IllegalStateException(message);
        }
        return new ClassInfoImpl(classElement, types, visitorContext);
    }

    @Override
    public String name() {
        return annotationValue.getAnnotationName();
    }

    @Override
    public boolean hasMember(String name) {
        return annotationValue.get(name, Object.class).isPresent();
    }

    @Override
    public AnnotationMember member(String name) {
        return annotationValue.get(name, Object.class)
                .map(val -> new AnnotationMemberImpl(types, visitorContext, annotationValue.getAnnotationName(), name, val))
                .orElse(null);
    }

    @Override
    public Map<String, AnnotationMember> members() {
        return annotationValue.getValues().entrySet()
                .stream().collect(Collectors.toMap(
                        (entry) -> entry.getKey().toString(),
                        (entry) -> new AnnotationMemberImpl(types, visitorContext, annotationValue.getAnnotationName(), entry.getKey().toString(), entry.getValue())
                ));
    }

    public AnnotationValue<?> getAnnotationValue() {
        return annotationValue;
    }
}

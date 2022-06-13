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

import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.enterprise.lang.model.types.TypeVariable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class MethodInfoImpl extends DeclarationInfoImpl implements MethodInfo {
    private final VisitorContext visitorContext;
    private final MethodElement methodElement;
    private final ClassInfoImpl declaringClassInfo;

    MethodInfoImpl(ClassInfoImpl declaringClassInfo, MethodElement element, Types types, VisitorContext visitorContext) {
        super(element, types, visitorContext);
        this.methodElement = element;
        this.visitorContext = visitorContext;
        this.declaringClassInfo = declaringClassInfo;
    }

    @Override
    public MethodElement getElement() {
        return (MethodElement) super.getElement();
    }

    public VisitorContext getVisitorContext() {
        return visitorContext;
    }

    @Override
    public String name() {
        if (isConstructor()) {
            return methodElement.getOwningType().getName();
        }
        return methodElement.getName();
    }

    @Override
    public List<ParameterInfo> parameters() {
        return Arrays.stream(methodElement.getParameters())
                .map(pe -> new ParameterInfoImpl(this, pe, getTypes(), visitorContext))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Type returnType() {
        if (isConstructor()) {
            return declaringClassInfo.asType().asClass();
        }
        return TypeFactory.createType(methodElement.getReturnType(), getTypes(), visitorContext);
    }

    @Override
    public Type receiverType() {
        if (isStatic() || isConstructor()) {
            return null;
        }
        return methodElement.getReceiverType()
                .map(ce -> TypeFactory.createType(ce, getTypes(), visitorContext))
                .orElseGet(declaringClassInfo::asType);

    }

    @Override
    public List<Type> throwsTypes() {
        return Arrays.stream(methodElement.getThrownTypes())
                .map(ce -> TypeFactory.createType(ce, getTypes(), visitorContext))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<TypeVariable> typeParameters() {
        return methodElement.getDeclaredTypeVariables()
                .stream()
                .map(ce -> TypeFactory.createTypeVariable(ce, getTypes(), visitorContext))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public boolean isConstructor() {
        return methodElement instanceof ConstructorElement;
    }

    @Override
    public boolean isStatic() {
        return methodElement.isStatic();
    }

    @Override
    public boolean isAbstract() {
        return methodElement.isAbstract();
    }

    @Override
    public boolean isFinal() {
        return methodElement.isFinal();
    }

    @Override
    public int modifiers() {
        return toReflectModifiers(methodElement.getModifiers());
    }

    @Override
    public ClassInfo declaringClass() {
        return declaringClassInfo;
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
        MethodInfoImpl other = (MethodInfoImpl) o;
        return annotationMetadata.equals(other.annotationMetadata);
    }

    @Override
    public int hashCode() {
        return annotationMetadata.hashCode();
    }
}

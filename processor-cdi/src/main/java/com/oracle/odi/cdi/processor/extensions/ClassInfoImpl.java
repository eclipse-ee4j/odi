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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.PackageInfo;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.enterprise.lang.model.types.TypeVariable;

final class ClassInfoImpl extends DeclarationInfoImpl implements ClassInfo {
    private final ClassElement classElement;
    private final VisitorContext visitorContext;

    ClassInfoImpl(ClassElement element, Types types, VisitorContext visitorContext) {
        super(element, types);
        this.classElement = element;
        this.visitorContext = visitorContext;
    }

    @Override
    public Type asType() {
        return TypeFactory.createType(
                classElement,
                getTypes(),
                visitorContext
        );
    }

    @Override
    public ClassElement getElement() {
        return classElement;
    }

    @Override
    public String name() {
        return element.getName();
    }

    @Override
    public String simpleName() {
        return classElement.getSimpleName();
    }

    @Override
    public PackageInfo packageInfo() {
        PackageElement aPackage = classElement.getPackage();
        return new PackageInfoImpl(aPackage, types);
    }

    @Override
    public List<TypeVariable> typeParameters() {
        final Map<String, ClassElement> typeArguments = classElement.getTypeArguments();
        if (typeArguments.isEmpty()) {
            return Collections.emptyList();
        } else {
            return typeArguments.entrySet().stream().map(entry ->
                new TypeVariableImpl(entry.getKey(), entry.getValue(), visitorContext, getTypes())
            ).collect(Collectors.toUnmodifiableList());
        }
    }

    @Override
    public Type superClass() {
        return classElement.getSuperType()
                .map(e -> TypeFactory.createType(
                        e,
                        types,
                        visitorContext
                ))
                .orElse(null);
    }

    @Override
    public ClassInfo superClassDeclaration() {
        final Type type = superClass();
        if (type != null) {
            return type.asDeclaration().asClass();
        }
        return null;
    }

    @Override
    public List<Type> superInterfaces() {
        Collection<ClassElement> interfaces = classElement.getInterfaces();
        return interfaces.stream().map(e -> TypeFactory.createType(e, types, visitorContext))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<ClassInfo> superInterfacesDeclarations() {
        return superInterfaces().stream().map(t -> t.asDeclaration().asClass())
                    .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public boolean isPlainClass() {
        return classElement.getFirstTypeArgument().isEmpty();
    }

    @Override
    public boolean isInterface() {
        return classElement.isInterface();
    }

    @Override
    public boolean isEnum() {
        return classElement.isEnum();
    }

    @Override
    public boolean isAnnotation() {
        return classElement.isAssignable(Annotation.class);
    }

    @Override
    public boolean isAbstract() {
        return classElement.isAbstract();
    }

    @Override
    public boolean isFinal() {
        return classElement.isFinal();
    }

    @Override
    public int modifiers() {
        return toReflectModifiers(classElement.getModifiers());
    }

    @Override
    public Collection<MethodInfo> constructors() {
        return classElement.getEnclosedElements(ElementQuery.of(ConstructorElement.class))
                .stream().map(methodElement ->
                      new MethodInfoImpl(this, methodElement, types, visitorContext)
                )
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Collection<MethodInfo> methods() {
        return classElement.getEnclosedElements(ElementQuery.ALL_METHODS)
                    .stream().map(methodElement ->
                          new MethodInfoImpl(this, methodElement, types, visitorContext)
                )
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Collection<FieldInfo> fields() {
        return classElement.getEnclosedElements(ElementQuery.ALL_FIELDS)
                    .stream().map(fieldElement -> new FieldInfoImpl(this, fieldElement, types, visitorContext))
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
        ClassInfoImpl classInfo = (ClassInfoImpl) o;
        return element.equals(classInfo.element);
    }

    @Override
    public int hashCode() {
        return element.hashCode();
    }
}

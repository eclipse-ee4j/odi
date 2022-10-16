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
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.PackageInfo;
import jakarta.enterprise.lang.model.declarations.RecordComponentInfo;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.enterprise.lang.model.types.TypeVariable;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ClassInfoImpl extends DeclarationInfoImpl implements ClassInfo {
    private final ClassElement classElement;
    private final VisitorContext visitorContext;

    ClassInfoImpl(ClassElement element, Types types, VisitorContext visitorContext) {
        super(element, types, visitorContext);
        this.classElement = element;
        this.visitorContext = visitorContext;
    }

    @Override
    public ClassInfo asClass() {
        return this;
    }

    @Override
    public Type asType() {
        return TypeFactory.createType(classElement, getTypes(), visitorContext);
    }

    public ClassElement getElement() {
        return classElement;
    }

    @Override
    public String name() {
        return classElement.getName();
    }

    @Override
    public String simpleName() {
        return classElement.getSimpleName();
    }

    @Override
    public PackageInfo packageInfo() {
        PackageElement aPackage = classElement.getPackage();
        return new PackageInfoImpl(aPackage, types, visitorContext);
    }

    @Override
    public List<TypeVariable> typeParameters() {
        final Map<String, ClassElement> typeArguments = classElement.getTypeArguments();
        if (typeArguments.isEmpty()) {
            return Collections.emptyList();
        } else {
            return typeArguments.values().stream()
                    .map(v -> TypeFactory.createTypeVariable(v, types, visitorContext))
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    @Override
    public Type superClass() {
        return classElement.getSuperType()
                .map(e -> TypeFactory.createType(e, types, visitorContext))
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
        return !isInterface() && !isEnum() && !isAnnotation() && !isRecord();
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
    public boolean isRecord() {
        return classElement.isRecord();
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
        return classElement.getEnclosedElements(ElementQuery.CONSTRUCTORS)
                .stream().map(methodElement -> new MethodInfoImpl(this, methodElement, types, visitorContext))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Collection<MethodInfo> methods() {
        return classElement.getEnclosedElements(ElementQuery.ALL_METHODS.includeOverriddenMethods().includeHiddenElements())
                .stream()
                .map(methodElement -> new MethodInfoImpl(getDeclaringType(methodElement.getDeclaringType()), methodElement, types, visitorContext))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Collection<FieldInfo> fields() {
        return classElement.getEnclosedElements(ElementQuery.ALL_FIELDS.includeEnumConstants().includeHiddenElements())
                .stream()
                .map((MemberElement memberElement) -> {
                    if (memberElement instanceof EnumConstantElement) {
                        return new EnumConstantInfoImpl(getDeclaringType(memberElement.getDeclaringType()), (EnumConstantElement) memberElement, types, visitorContext);
                    }
                    return new FieldInfoImpl(getDeclaringType(memberElement.getDeclaringType()), (FieldElement) memberElement, types, visitorContext);
                })
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Collection<RecordComponentInfo> recordComponents() {
        // TODO
        return Collections.emptyList();
    }

    private ClassInfoImpl getDeclaringType(ClassElement declaringType) {
        ClassInfoImpl declaringClassInfo;
        if (classElement.equals(declaringType)) {
            declaringClassInfo = this;
        } else {
            declaringClassInfo = new ClassInfoImpl(declaringType, types, visitorContext);
        }
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
        ClassInfoImpl classInfo = (ClassInfoImpl) o;
        return annotationMetadata.equals(classInfo.annotationMetadata);
    }

    @Override
    public int hashCode() {
        return annotationMetadata.hashCode();
    }
}

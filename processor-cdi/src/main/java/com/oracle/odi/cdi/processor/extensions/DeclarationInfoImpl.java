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

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;

import javax.lang.model.element.PackageElement;
import java.util.Set;

class DeclarationInfoImpl extends AnnotationTargetImpl implements DeclarationInfo {

    protected DeclarationInfoImpl(Element element, Types types, VisitorContext visitorContext) {
        super(element, types, visitorContext);
    }

    @Override
    public DeclarationInfo asDeclaration() {
        return this;
    }

    @Override
    public Kind kind() {
        if (element instanceof FieldElement) {
            return Kind.FIELD;
        }
        if (element instanceof MethodElement) {
            return Kind.METHOD;
        }
        if (element instanceof ParameterElement) {
            return Kind.PARAMETER;
        }
        if (element instanceof PackageElement) {
            return Kind.PACKAGE;
        }
        if (element instanceof ClassElement) {
            return Kind.CLASS;
        }
        throw new IllegalStateException("Unknown kind for element: " + element.getClass());
    }

    protected int toReflectModifiers(Set<ElementModifier> modifierSet) {
        int modifiers = 0;
        for (ElementModifier modifier : modifierSet) {
            switch (modifier) {
                case FINAL:
                    modifiers = modifiers | java.lang.reflect.Modifier.FINAL;
                break;
                case STATIC:
                    modifiers = modifiers | java.lang.reflect.Modifier.STATIC;
                break;
                case PUBLIC:
                    modifiers = modifiers | java.lang.reflect.Modifier.PUBLIC;
                break;
                case ABSTRACT:
                    modifiers = modifiers | java.lang.reflect.Modifier.ABSTRACT;
                break;
                case PROTECTED:
                    modifiers = modifiers | java.lang.reflect.Modifier.PROTECTED;
                break;
                case PRIVATE:
                    modifiers = modifiers | java.lang.reflect.Modifier.PRIVATE;
                break;
                case TRANSIENT:
                    modifiers = modifiers | java.lang.reflect.Modifier.TRANSIENT;
                break;
                case VOLATILE:
                    modifiers = modifiers | java.lang.reflect.Modifier.VOLATILE;
                break;
                case NATIVE:
                    modifiers = modifiers | java.lang.reflect.Modifier.NATIVE;
                break;
                case SYNCHRONIZED:
                    modifiers = modifiers | java.lang.reflect.Modifier.SYNCHRONIZED;
                break;
                default:
                    // Ignore
            }
        }
        return modifiers;
    }

    @Override
    public int hashCode() {
        return element.hashCode();
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
        DeclarationInfoImpl other = (DeclarationInfoImpl) o;
        return element.equals(other.element);
    }

    @Override
    public String toString() {
        return element.getDescription(true);
    }
}

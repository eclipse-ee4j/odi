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

import io.micronaut.annotation.processing.visitor.JavaClassElementHelper;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.enterprise.lang.model.types.TypeVariable;

final class TypeFactory {

    private TypeFactory() {
    }

    public static Type createType(ClassElement classElement, Types types, VisitorContext visitorContext) {
        if (classElement.isWildcard()) {
            return createWildType((WildcardElement) classElement, types, visitorContext);
        }
        if (classElement.isGenericPlaceholder()) {
            return createTypeVariable(classElement, types, visitorContext);
        }
        if (classElement.isArray()) {
            return new ArrayTypeImpl(classElement, types, visitorContext);
        }
        if (classElement.isPrimitive()) {
            if (classElement.equals(PrimitiveElement.VOID)) {
                return types.ofVoid();
            } else {
                return new PrimitiveTypeImpl(classElement, types, visitorContext);
            }
        }
        if (classElement.getTypeArguments().isEmpty()) {
            return new ClassTypeImpl(classElement, types, visitorContext);
        } else {
            return new ParameterizedTypeImpl(classElement, types, visitorContext);
        }
    }

    public static WildcardTypeImpl createWildType(WildcardElement classElement, Types types, VisitorContext visitorContext) {
        return new WildcardTypeImpl(classElement, types, visitorContext);
    }

    public static WildcardTypeImpl createWildType(WildcardElement classElement, Types types, VisitorContext visitorContext,
                                                  ClassElement rootElement,
                                                  int boundIndex) {
        boolean isUpperEmpty = JavaClassElementHelper.isEmptyUpperBoundOfWildcard(rootElement, boundIndex);
        boolean isLowerEmpty = JavaClassElementHelper.isEmptyLowerBoundOfWildcard(rootElement, boundIndex);
        return new WildcardTypeImpl(classElement, types, visitorContext, isUpperEmpty, isLowerEmpty);
    }

    public static TypeVariable createTypeVariable(ClassElement classElement, Types types, VisitorContext visitorContext) {
        GenericPlaceholderElement genericPlaceholderElement = (GenericPlaceholderElement) classElement;
        return new TypeVariableImpl(genericPlaceholderElement.getVariableName(), genericPlaceholderElement, visitorContext, types);
    }

}

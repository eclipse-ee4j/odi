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
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;

final class ClassTypeImpl extends AnnotationTargetImpl implements jakarta.enterprise.lang.model.types.ClassType {
    private final ClassElement classElement;

    ClassTypeImpl(ClassElement element, Types types, VisitorContext visitorContext) {
        super(element, types, visitorContext);
        this.classElement = element;
    }

    public ClassElement getClassElement() {
        return classElement;
    }

    @Override
    public DeclarationInfo asDeclaration() {
        return declaration();
    }

    @Override
    public ClassInfo declaration() {
        return new ClassInfoImpl(
                visitorContext.getClassElement(classElement.getName())
                .orElseThrow(() -> new IllegalStateException("Class not found on classpath: " + classElement.getName())),
                getTypes(),
                visitorContext);
    }

    @Override
    public String toString() {
        return declaration().name();
    }
}

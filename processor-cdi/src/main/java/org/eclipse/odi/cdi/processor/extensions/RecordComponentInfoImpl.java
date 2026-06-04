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

import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.RecordComponentInfo;
import jakarta.enterprise.lang.model.types.Type;

final class RecordComponentInfoImpl extends AnnotationTargetImpl implements RecordComponentInfo {
    private final ClassInfoImpl declaringRecord;
    private final PropertyElement propertyElement;

    RecordComponentInfoImpl(ClassInfoImpl declaringRecord,
                            PropertyElement propertyElement,
                            Types types,
                            VisitorContext visitorContext) {
        super(propertyElement, types, visitorContext);
        this.declaringRecord = declaringRecord;
        this.propertyElement = propertyElement;
    }

    @Override
    public DeclarationInfo asDeclaration() {
        return this;
    }

    @Override
    public Kind kind() {
        return Kind.RECORD_COMPONENT;
    }

    @Override
    public String name() {
        return propertyElement.getName();
    }

    @Override
    public Type type() {
        FieldInfo field = field();
        if (field != null) {
            return field.type();
        }
        MethodInfo accessor = accessor();
        if (accessor != null) {
            return accessor.returnType();
        }
        return TypeFactory.createType(propertyElement.getGenericType(), types, visitorContext);
    }

    @Override
    public FieldInfo field() {
        return propertyElement.getField()
                .map(fieldElement -> new FieldInfoImpl(declaringRecord, fieldElement, types, visitorContext))
                .orElse(null);
    }

    @Override
    public MethodInfo accessor() {
        MethodElement accessor = propertyElement.getReadMethod().orElse(null);
        if (accessor == null) {
            return null;
        }
        return new MethodInfoImpl(declaringRecord, accessor, types, visitorContext);
    }

    @Override
    public ClassInfo declaringRecord() {
        return declaringRecord;
    }
}

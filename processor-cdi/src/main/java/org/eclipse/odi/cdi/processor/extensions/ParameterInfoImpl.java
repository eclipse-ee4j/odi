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


import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;
import jakarta.enterprise.lang.model.types.Type;

final class ParameterInfoImpl extends AnnotationTargetImpl implements ParameterInfo {
    private final MethodInfo methodInfo;
    private final ParameterElement parameterElement;
    private final VisitorContext visitorContext;

    ParameterInfoImpl(MethodInfo methodInfo,
                             ParameterElement parameterElement,
                             Types types,
                             VisitorContext visitorContext) {
        super(parameterElement, types, visitorContext);
        this.methodInfo = methodInfo;
        this.parameterElement = parameterElement;
        this.visitorContext = visitorContext;
    }

    public ParameterElement getElement() {
        return parameterElement;
    }

    public VisitorContext getVisitorContext() {
        return visitorContext;
    }

    @Override
    public String name() {
        return parameterElement.getName();
    }

    @Override
    public Type asType() {
        return type();
    }

    @Override
    public Type type() {
        return TypeFactory.createType(
                parameterElement.getType(),
                getTypes(),
                visitorContext
        );
    }

    @Override
    public MethodInfo declaringMethod() {
        return methodInfo;
    }
}

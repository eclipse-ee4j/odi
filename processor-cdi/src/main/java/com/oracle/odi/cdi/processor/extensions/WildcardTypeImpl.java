/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.enterprise.lang.model.types.WildcardType;

import java.util.List;

final class WildcardTypeImpl extends AnnotationTargetImpl implements WildcardType {

    private final WildcardElement wildcardElement;
    private final VisitorContext visitorContext;
    private final boolean isUpperEmpty;
    private final boolean isLowerEmpty;

    WildcardTypeImpl(WildcardElement wildcardElement, Types types, VisitorContext visitorContext) {
        this(wildcardElement, types, visitorContext, false, false);
    }

    WildcardTypeImpl(WildcardElement wildcardElement, Types types, VisitorContext visitorContext,
                     boolean isUpperEmpty, boolean isLowerEmpty) {
        super(wildcardElement, types, visitorContext);
        this.wildcardElement = wildcardElement;
        this.visitorContext = visitorContext;
        this.isUpperEmpty = isUpperEmpty && !isLowerEmpty; // Upper can be empty only if lower is defined!
        this.isLowerEmpty = isLowerEmpty;
    }

    @Override
    public Type upperBound() {
        if (isUpperEmpty) {
            return null;
        }
        List<? extends ClassElement> upperBounds = wildcardElement.getUpperBounds();
        if (upperBounds.isEmpty()) {
            return null;
        } else if (upperBounds.size() > 1) {
            throw new IllegalStateException("Unimplemented case of multiple bounds!");
        }
        return TypeFactory.createType(upperBounds.iterator().next(), types, visitorContext);
    }

    @Override
    public Type lowerBound() {
        if (isLowerEmpty) {
            return null;
        }
        List<? extends ClassElement> lowerBound = wildcardElement.getLowerBounds();
        if (lowerBound.isEmpty()) {
            return null;
        } else if (lowerBound.size() > 1) {
            throw new IllegalStateException("Unimplemented case of multiple bounds!");
        }
        return TypeFactory.createType(lowerBound.iterator().next(), types, visitorContext);
    }

}

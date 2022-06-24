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
package org.eclipse.odi.cdi.processor.visitors;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import org.eclipse.odi.cdi.processor.AnnotationUtil;
import org.eclipse.odi.cdi.processor.CdiUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Validates disposes methods.
 */
public class DisposesMethodVisitor extends ParameterAnnotationInjectableMethodVisitor<Disposes> implements TypeElementVisitor<Object, Object> {

    private final List<MethodElement> disposerMethods = new ArrayList<>();

    @Override
    protected Class<Disposes> getParameterAnnotation() {
        return Disposes.class;
    }

    @Override
    public void handleMatch(MethodElement methodElement, ParameterElement parameterElement, VisitorContext context) {
        final ClassElement disposedType = parameterElement.getType();

        // Skip validating for beans with qualifiers
        if (!parameterElement.hasDeclaredStereotype(io.micronaut.core.annotation.AnnotationUtil.QUALIFIER)) {
            if (validateMatchingProduces(methodElement, context, disposedType)) {
                return;
            }
        }

        if (methodElement.isStatic() || methodElement.isPrivate()) {
            // TODO: support static and private methods
            return;
        }

        this.disposerMethods.add(methodElement);
        methodElement.annotate(AnnotationUtil.ANN_DISPOSER_METHOD);
    }

    private boolean validateMatchingProduces(MethodElement element, VisitorContext context, ClassElement disposedType) {
        if (element.isStatic()) {
            return false;
        }
        if (!disposerMethods.isEmpty()) {
            for (MethodElement disposerMethod : disposerMethods) {
                final Optional<ParameterElement> disposerParam = Arrays.stream(disposerMethod.getParameters())
                        .filter(p -> p.hasDeclaredAnnotation(Disposes.class))
                        .findFirst();
                if (disposerParam.isPresent()) {
                    if (disposerParam.get().getType().getName().equals(disposedType.getName())) {
                        final String methodDesc = Stream.of(element, disposerMethod)
                                .map((me) -> me.getDescription(true))
                                .collect(Collectors.joining(" and "));
                        context.fail("Only a single @Disposes method is permitted, found: " + methodDesc, element);
                        return true;
                    }
                }
            }
        }

        // now validate if a bean producing method is present
        final Optional<MethodElement> producerMethod = currentClass.getEnclosedElement(
                ElementQuery.ALL_METHODS
                        .onlyInstance()
                        .onlyAccessible()
                        .onlyConcrete()
                        .annotated((annotationMetadata -> annotationMetadata.hasDeclaredAnnotation(Produces.class)))
                        .filter((methodElement -> disposedType.isAssignable(methodElement.getGenericReturnType())))
        );

        if (producerMethod.isEmpty()) {
            context.fail(
                    "No associated @Produces method found for @Disposes method. A method with a @Disposes parameter"
                            + " must declare a method annotated with @Produces that has the same type as the "
                            + "parameter. See " + CdiUtil.SPEC_LOCATION + "#disposer_method_resolution",
                    element);
        }
        return false;
    }
}

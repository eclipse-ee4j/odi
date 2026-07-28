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
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.context.annotation.Executable;
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
    protected boolean validateParameter(MethodElement methodElement, ParameterElement parameterElement, VisitorContext context) {
        return CdiUtil.validateDisposerInjectionPointMetadata(context, parameterElement);
    }

    @Override
    public void handleMatch(MethodElement methodElement, ParameterElement parameterElement, VisitorContext context) {
        final ClassElement disposedType = parameterElement.getType();

        // Skip validating for beans with qualifiers
        if (!parameterElement.hasDeclaredStereotype(io.micronaut.core.annotation.AnnotationUtil.QUALIFIER)) {
            Optional<MemberElement> producer = validateMatchingProduces(methodElement, context, disposedType);
            if (producer.isEmpty()) {
                return;
            }
            producer.get().annotate(AnnotationUtil.ANN_DISPOSER_METHOD);
        }

        this.disposerMethods.add(methodElement);
        methodElement.annotate(AnnotationUtil.ANN_DISPOSER_METHOD, builder -> builder.member("staticMethod", methodElement.isStatic()));
        if (methodElement.isStatic()) {
            methodElement.annotate(Executable.class, builder -> builder.member("processOnStartup", true));
        }
    }

    private Optional<MemberElement> validateMatchingProduces(MethodElement element, VisitorContext context, ClassElement disposedType) {
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
                        return Optional.empty();
                    }
                }
            }
        }

        // now validate if a bean producing method is present
        Optional<MemberElement> producer = currentClass.getEnclosedElement(
                ElementQuery.ALL_METHODS
                        .onlyConcrete()
                        .annotated((annotationMetadata -> annotationMetadata.hasDeclaredAnnotation(Produces.class)))
                        .filter((methodElement -> disposedType.isAssignable(methodElement.getGenericReturnType())))
        ).map(method -> (MemberElement) method);

        if (producer.isEmpty()) {
            producer = currentClass.getEnclosedElement(
                    ElementQuery.ALL_FIELDS
                            .annotated((annotationMetadata -> annotationMetadata.hasDeclaredAnnotation(Produces.class)))
                            .filter((fieldElement -> disposedType.isAssignable(fieldElement.getGenericField())))
            ).map(field -> (MemberElement) field);
        }

        if (producer.isEmpty()) {
            context.fail(
                    "No associated @Produces method found for @Disposes method. A method with a @Disposes parameter"
                            + " must declare a method or field annotated with @Produces that has the same type as the "
                            + "parameter. See " + CdiUtil.SPEC_LOCATION + "#disposer_method_resolution",
                    element);
        }
        return producer;
    }
}

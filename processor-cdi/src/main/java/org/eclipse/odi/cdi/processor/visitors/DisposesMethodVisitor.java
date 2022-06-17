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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.interceptor.Interceptor;
import org.eclipse.odi.cdi.processor.AnnotationUtil;
import org.eclipse.odi.cdi.processor.CdiUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Validates disposes methods.
 */
public class DisposesMethodVisitor implements TypeElementVisitor<Object, Object> {

    private final List<MethodElement> disposerMethods = new ArrayList<>();
    private ClassElement currentClass;

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.singleton(io.micronaut.core.annotation.AnnotationUtil.SCOPE);
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        this.disposerMethods.clear();
        this.currentClass = element;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (currentClass == null) {
            return;
        }
        for (ParameterElement parameter : element.getParameters()) {
            if (CdiUtil.validateInjectedType(context, parameter.getGenericType(), parameter)) {
                return;
            }
        }
        final List<ParameterElement> disposesParameters = Arrays
                .stream(element.getParameters())
                .filter(p -> p.hasDeclaredAnnotation(Disposes.class))
                .collect(Collectors.toList());
        if (!disposesParameters.isEmpty()) {
            if (element.isPrivate()) {
                context.fail("Methods with parameters annotated with @Disposes cannot be private.", element);
            } else if (element.isStatic()) {
                context.fail("Methods with parameters annotated with @Disposes cannot be static.", element);
            } else if (element.isAbstract()) {
                context.fail("Methods with parameters annotated with @Disposes cannot be abstract.", element);
            } else if (element.hasDeclaredAnnotation(Produces.class)) {
                context.fail("Methods with parameters annotated with @Disposes cannot be annotated with @Produces", element);
            } else if (element.hasDeclaredAnnotation(io.micronaut.core.annotation.AnnotationUtil.INJECT)) {
                context.fail("Methods annotated with @Inject cannot define parameters annotated with @Disposes", element);
            } else if (this.currentClass.hasDeclaredAnnotation(Interceptor.class)) {
                context.fail("Interceptors cannot declare @Disposes methods", element);
            } else {
                final int disposesParametersCount = disposesParameters.size();
                if (disposesParametersCount == 1) {
                    ParameterElement disposesParameter = disposesParameters.get(0);
                    final ClassElement disposedType = disposesParameter.getType();
                    // Skip validating for beans with qualifiers
                    if (!disposerMethods.isEmpty() && !disposesParameter.hasDeclaredStereotype(io.micronaut.core.annotation.AnnotationUtil.QUALIFIER)) {
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
                                    return;
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

                    this.disposerMethods.add(element);
                    element.annotate(AnnotationUtil.ANN_DISPOSER_METHOD);
                } else {
                    context.fail("Only a single @Disposes parameter is permitted, "
                                         + disposesParametersCount
                                         + " are defined: "
                                         + disposesParameters.stream().map(Element::getName)
                            .collect(Collectors.joining(" and ")), element);
                }
            }
        }
    }

    @Override
    @NonNull
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}

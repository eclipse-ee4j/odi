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
package com.oracle.odi.cdi.processor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Produces;
import jakarta.interceptor.Interceptor;

import static com.oracle.odi.cdi.processor.AnnotationUtil.ANN_OBSERVES_METHOD;

/**
 * The visitor of methods that are declared with {@link Observes} or {@link ObservesAsync}.
 */
public class ObservesMethodVisitor implements TypeElementVisitor<Object, Object> {

    private ClassElement currentClass;

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
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
        final List<ParameterElement> observesParameters = Arrays
                .stream(element.getParameters())
                .filter(p -> p.hasDeclaredAnnotation(Observes.class) || p.hasDeclaredAnnotation(ObservesAsync.class))
                .collect(Collectors.toList());
        if (observesParameters.size() > 1) {
            context.fail("Method cannot have multiple parameters annotated with @Observes or @ObservesAsync.", element);
        } else if (!observesParameters.isEmpty()) {
            Optional<ParameterElement> observesAnnotated = observesParameters.stream()
                    .filter(p -> p.hasDeclaredAnnotation(Observes.class))
                    .findFirst();
            Optional<ParameterElement> observesAsyncAnnotated = observesParameters.stream()
                    .filter(p -> p.hasDeclaredAnnotation(ObservesAsync.class))
                    .findFirst();
            if (observesAnnotated.isPresent() && observesAsyncAnnotated.isPresent()) {
                context.fail("Method parameter cannot define both @Observes and @ObservesAsync.", observesAnnotated.get());
            } else if (element.isPrivate()) {
                context.fail("Methods with parameters annotated with @Observes cannot be private.", element);
            } else if (element.isStatic()) {
                context.fail("Methods with parameters annotated with @Observes cannot be static.", element);
            } else if (element.isAbstract()) {
                context.fail("Methods with parameters annotated with @Observes cannot be abstract.", element);
            } else if (element.hasDeclaredAnnotation(Produces.class)) {
                context.fail("Methods with parameters annotated with @Observes cannot be annotated with @Produces", element);
            } else if (element.hasDeclaredAnnotation(Inject.class)) {
                context.fail("Methods annotated with @Inject cannot define parameters annotated with @Observes", element);
            } else if (this.currentClass.hasDeclaredAnnotation(Interceptor.class)) {
                context.fail("Interceptors cannot declare @Observes methods", element);
            } else {
                if (!AnnotationUtil.hasBeanDefiningAnnotation(currentClass)) {
                    currentClass.annotate(ApplicationScoped.class);
                }
                element.annotate(ANN_OBSERVES_METHOD, annotationValueBuilder -> {
                    observesAnnotated.ifPresent(p -> {
                        annotationValueBuilder.member("eventArgumentIndex", Arrays.asList(element.getParameters()).indexOf(p));
                        p.enumValue("notifyObserver", Reception.class).ifPresent(reception -> {
                            annotationValueBuilder.member("notifyObserver", reception);
                        });
                        p.enumValue("during", TransactionPhase.class).ifPresent(during -> {
                            annotationValueBuilder.member("during", during);
                        });
//                        if (p.getAnnotationNamesByStereotype("javax.inject.Qualifier").isEmpty()) {
//                            p.annotate(Any.class);
//                        }
                    });
                    observesAsyncAnnotated.ifPresent(p -> {
                        annotationValueBuilder.member("eventArgumentIndex", Arrays.asList(element.getParameters()).indexOf(p));
                        p.enumValue("notifyObserver", Reception.class).ifPresent(reception -> {
                            annotationValueBuilder.member("notifyObserver", reception);
                        });
//                        if (p.getAnnotationNamesByStereotype("javax.inject.Qualifier").isEmpty()) {
//                            p.annotate(Any.class);
//                        }
                    });
                });
            }
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}

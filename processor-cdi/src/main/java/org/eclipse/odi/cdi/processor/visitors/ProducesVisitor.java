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

import org.eclipse.odi.cdi.processor.AnnotationUtil;
import org.eclipse.odi.cdi.processor.CdiUtil;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.interceptor.Interceptor;

import java.lang.annotation.Annotation;
import java.util.Arrays;

/**
 * Validates elements annotated with {@link jakarta.enterprise.inject.Produces}.
 */
public class ProducesVisitor implements TypeElementVisitor<Object, Produces> {
    private ClassElement currentClass;

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        this.currentClass = element;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (!AnnotationUtil.hasBeanDefiningAnnotation(currentClass)) {
            context.fail(
                    "Class with a producer method must specify a bean definition annotation. See "
                            + CdiUtil.SPEC_LOCATION
                            + "#bean_defining_annotations",
                    element
            );
            return;
        }
        if (element.hasAnnotation(io.micronaut.core.annotation.AnnotationUtil.INJECT)) {
            context.fail(
                    "Produces methods cannot be annotated with @Inject. See "
                            + CdiUtil.SPEC_LOCATION
                            + "#declaring_producer_method",
                    element
            );
            return;
        }
        if (currentClass.hasAnnotation(Interceptor.class)) {
            context.fail(
                    "Interceptors cannot define methods annotated with @Produces. See "
                            + CdiUtil.SPEC_LOCATION
                            + "#declaring_producer_method",
                    element
            );
            return;
        }
        if (!this.currentClass.hasAnnotation(Factory.class)) {
            this.currentClass.annotate(Factory.class);
        }

        for (ParameterElement parameter : element.getParameters()) {
            for (Class<? extends Annotation> annotation : Arrays.asList(Observes.class, ObservesAsync.class, Disposes.class)) {
                if (parameter.hasAnnotation(annotation)) {
                    context.fail(
                            "Produces methods cannot contain parameters annotated with @" + annotation
                                    .getSimpleName() + ". See " + CdiUtil.SPEC_LOCATION + "#declaring_producer_method", element
                    );
                    return;
                }
            }
            if (CdiUtil.visitInjectPoint(context, parameter)) {
                return;
            }
        }
        CdiUtil.visitBeanDefinition(context, element);
        TypeElementVisitor.super.visitMethod(element, context);
        element.annotate(Bean.class);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        if (!AnnotationUtil.hasBeanDefiningAnnotation(currentClass)) {
            context.fail(
                    "Class with a producer field must specify a bean definition annotation. See "
                            + CdiUtil.SPEC_LOCATION
                            + "#bean_defining_annotations",
                    element
            );
            return;
        }
        if (element.hasAnnotation(io.micronaut.core.annotation.AnnotationUtil.INJECT)) {
            context.fail("Produces field cannot be annotated with @Inject. See " + CdiUtil.SPEC_LOCATION + "#producer_field", element);
            return;
        }
        if (!this.currentClass.hasAnnotation(Factory.class)) {
            this.currentClass.annotate(Factory.class);
        }
        CdiUtil.visitBeanDefinition(context, element);
        element.annotate(Bean.class);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}

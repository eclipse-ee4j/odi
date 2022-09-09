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

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.Produces;
import org.eclipse.odi.cdi.processor.CdiUtil;

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
        if (CdiUtil.validateBeanDefinition(context, Produces.class, currentClass)) {
            return;
        }
        if (CdiUtil.validateNoInterceptor(context, Produces.class, element)) {
            return;
        }
        if (CdiUtil.validateMethodExtraAnnotations(context, Produces.class, element)) {
            return;
        }
        for (ParameterElement parameter : element.getParameters()) {
            if (CdiUtil.validateMethodNoSpecialParameters(context, Produces.class.getSimpleName(), element, parameter)) {
                return;
            }
            if (CdiUtil.visitInjectPoint(context, parameter)) {
                return;
            }
        }
        makeBean(element, context);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        if (CdiUtil.validateBeanDefinition(context, Produces.class, currentClass)) {
            return;
        }
        if (CdiUtil.validateField(context, Produces.class, element)) {
            return;
        }
        makeBean(element, context);
    }

    private void makeBean(MemberElement element, VisitorContext context) {
        if (!element.getDeclaringType().equals(currentClass)) {
            // Producers aren't inherited
            return;
        }
        if (element.isStatic()) {
            element.annotate(Executable.class);
        }
        if (element.isPrivate()) {
            element.annotate(ReflectiveAccess.class);
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

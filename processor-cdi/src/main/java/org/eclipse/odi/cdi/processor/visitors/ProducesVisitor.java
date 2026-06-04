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
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Reserve;
import org.eclipse.odi.cdi.processor.CdiUtil;

import java.lang.annotation.Annotation;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Validates elements annotated with {@link jakarta.enterprise.inject.Produces}.
 */
public class ProducesVisitor implements TypeElementVisitor<Object, Produces> {
    private static final AnnotationClassValue<Object> SELECTED_ALTERNATIVE_CONDITION =
            new AnnotationClassValue<>("org.eclipse.odi.cdi.condition.SelectedAlternativeCondition");
    private static final AnnotationClassValue<Object> UNSELECTED_RESERVE_CONDITION =
            new AnnotationClassValue<>("org.eclipse.odi.cdi.condition.UnselectedReserveCondition");

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
    public Set<String> getSupportedOptions() {
        return Set.of(CdiUtil.BEAN_CLASSES_OPTION, CdiUtil.BUILD_COMPATIBLE_EXTENSIONS_OPTION);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (CdiUtil.validateBeanDefinition(context, Produces.class, currentClass)) {
            return;
        }
        if (CdiUtil.validateMultipleScopes(context, element)) {
            return;
        }
        if (CdiUtil.validateProducerType(context, element.getGenericReturnType(), element)) {
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
        if (CdiUtil.validateMultipleScopes(context, element)) {
            return;
        }
        if (CdiUtil.validateProducerType(context, element.getGenericField(), element)) {
            return;
        }
        if (CdiUtil.validateNoInterceptor(context, Produces.class, element)) {
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
        if (!CdiUtil.isBeanClass(currentClass)) {
            return;
        }
        if (element.isPrivate()) {
            element.annotate(ReflectiveAccess.class);
        }
        if (!this.currentClass.hasAnnotation(Factory.class)) {
            this.currentClass.annotate(Factory.class);
        }
        inheritAlternativeMetadata(element);
        inheritReserveMetadata(element, context);
        if (CdiUtil.hasDependentScope(element, context) && !element.hasDeclaredAnnotation(Dependent.class)) {
            element.annotate(Dependent.class);
        }
        CdiUtil.visitBeanDefinition(context, element);
        if (CdiUtil.hasDependentScope(element, context)) {
            element.annotate(Nullable.class);
        }
        element.annotate(Bean.class);
    }

    private void inheritAlternativeMetadata(MemberElement element) {
        ClassElement declaringType = element.getDeclaringType();
        if (!declaringType.hasAnnotation(Alternative.class) && !declaringType.hasStereotype(Alternative.class)) {
            return;
        }
        element.annotate(Alternative.class);
        OptionalInt priority = declaringType.intValue(Priority.class);
        if (priority.isPresent()) {
            int value = priority.getAsInt();
            element.annotate(Priority.class, builder -> builder.value(value));
            element.annotate(Order.class, builder -> builder.value(-value));
        } else {
            copyAnnotation(declaringType, element, Order.class);
            element.annotate(Requires.class, builder -> builder.member("condition", SELECTED_ALTERNATIVE_CONDITION));
        }
    }

    private void inheritReserveMetadata(MemberElement element, VisitorContext context) {
        if (!CdiUtil.isReserve(element)) {
            return;
        }
        OptionalInt priority = CdiUtil.resolvePriority(context, element);
        if (priority.isPresent()) {
            int value = priority.getAsInt();
            element.annotate(Priority.class, builder -> builder.value(value));
            element.annotate(Order.class, builder -> builder.value(-value));
            element.annotate(Secondary.class);
        } else {
            element.annotate(Requires.class, builder -> builder.member("condition", UNSELECTED_RESERVE_CONDITION));
        }
    }

    private <A extends Annotation> void copyAnnotation(ClassElement source, MemberElement target, Class<A> annotationType) {
        AnnotationValue<A> annotation = source.getAnnotation(annotationType);
        if (annotation != null) {
            target.annotate(annotation);
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}

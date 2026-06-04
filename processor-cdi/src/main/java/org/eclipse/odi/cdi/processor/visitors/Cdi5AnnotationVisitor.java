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
import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.AnnotationElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.AutoClose;
import jakarta.enterprise.context.Eager;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Reserve;
import jakarta.enterprise.inject.Stereotype;
import org.eclipse.odi.cdi.processor.CdiUtil;

import java.util.Set;

/**
 * Processes CDI 5 bean annotations that need Micronaut metadata.
 */
public class Cdi5AnnotationVisitor implements TypeElementVisitor<Object, Object> {
    private static final AnnotationClassValue<Object> UNSELECTED_RESERVE_CONDITION =
            new AnnotationClassValue<>("org.eclipse.odi.cdi.condition.UnselectedReserveCondition");

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return CollectionUtils.setOf(
                Eager.class.getName(),
                Reserve.class.getName(),
                AutoClose.class.getName()
        );
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        processEager(element, context);
        processReserve(element, context);
        if (hasAutoClose(element)) {
            element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().named(method -> method.equals("close")))
                    .stream()
                    .filter(method -> method.getParameters().length == 0)
                    .findFirst()
                    .ifPresent(method -> method.annotate(Around.class, builder -> builder
                            .member("proxyTarget", false)));
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        processEager(element, context);
        processReserve(element, context);
        processAutoCloseProducer(element);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        processEager(element, context);
        processReserve(element, context);
        processAutoCloseProducer(element);
    }

    private static void processEager(Element element, VisitorContext context) {
        if (!hasEager(element)) {
            return;
        }
        if (hasInvalidEagerStereotype(element, context)) {
            context.fail("@Eager stereotypes must be @ApplicationScoped", element);
            return;
        }
        if (!isApplicationScoped(element)) {
            context.fail("@Eager beans must be @ApplicationScoped", element);
            return;
        }
        if (element instanceof AnnotationElement) {
            return;
        }
        if (element instanceof MemberElement && !element.hasAnnotation(Produces.class)) {
            return;
        }
        element.annotate(Context.class);
    }

    private static void processReserve(Element element, VisitorContext context) {
        if (isAlternativeProducerOfReserveBean(element)) {
            context.fail("@Reserve beans cannot declare @Alternative producers", element);
            return;
        }
        if (!hasReserve(element)) {
            return;
        }
        if (element.hasAnnotation(Alternative.class) || element.hasStereotype(Alternative.class)) {
            context.fail("@Reserve beans cannot also be @Alternative", element);
            return;
        }
        if (hasSelectingPriority(element)) {
            element.annotate(Secondary.class);
        } else {
            element.annotate(Requires.class, builder -> builder.member("condition", UNSELECTED_RESERVE_CONDITION));
        }
    }

    private static boolean hasSelectingPriority(Element element) {
        if (element instanceof MemberElement) {
            return element.hasDeclaredAnnotation(Priority.class)
                    || element.hasDeclaredStereotype(Priority.class);
        }
        return CdiUtil.hasPriority(element);
    }

    private static void processAutoCloseProducer(MemberElement element) {
        if (hasAutoClose(element) && producedType(element).isAssignable(AutoCloseable.class)) {
            element.annotate(Bean.class, builder -> builder.member("preDestroy", "close"));
        }
    }

    private static boolean hasEager(Element element) {
        return element.hasAnnotation(Eager.class) || element.hasStereotype(Eager.class);
    }

    private static boolean hasInvalidEagerStereotype(Element element, VisitorContext context) {
        for (String stereotype : element.getAnnotationNamesByStereotype(Stereotype.class)) {
            if (stereotype.equals(Stereotype.class.getName())) {
                continue;
            }
            if (context.getClassElement(stereotype)
                    .filter(stereotypeElement -> stereotypeElement.hasAnnotation(Eager.class)
                            && !stereotypeElement.hasAnnotation(ApplicationScoped.class))
                    .isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasReserve(Element element) {
        if (element instanceof MemberElement) {
            return element.hasDeclaredAnnotation(Reserve.class)
                    || element.hasDeclaredStereotype(Reserve.class);
        }
        return element.hasAnnotation(Reserve.class) || element.hasStereotype(Reserve.class);
    }

    private static boolean isAlternativeProducerOfReserveBean(Element element) {
        if (!(element instanceof MemberElement memberElement) || !element.hasAnnotation(Produces.class)) {
            return false;
        }
        ClassElement declaringType = memberElement.getDeclaringType();
        return (element.hasAnnotation(Alternative.class) || element.hasStereotype(Alternative.class))
                && (declaringType.hasAnnotation(Reserve.class) || declaringType.hasStereotype(Reserve.class));
    }

    private static boolean hasAutoClose(Element element) {
        return element.hasAnnotation(AutoClose.class) || element.hasStereotype(AutoClose.class);
    }

    private static boolean isApplicationScoped(Element element) {
        return element.hasAnnotation(ApplicationScoped.class) || element.hasStereotype(ApplicationScoped.class);
    }

    private static ClassElement producedType(MemberElement element) {
        if (element instanceof MethodElement) {
            return ((MethodElement) element).getGenericReturnType();
        }
        if (element instanceof FieldElement) {
            return ((FieldElement) element).getGenericField();
        }
        throw new IllegalArgumentException("Unsupported producer element: " + element);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}

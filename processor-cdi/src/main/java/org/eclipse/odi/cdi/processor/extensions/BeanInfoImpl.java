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

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Order;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.beans.BeanElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.DisposerInfo;
import jakarta.enterprise.inject.build.compatible.spi.InjectionPointInfo;
import jakarta.enterprise.inject.build.compatible.spi.ObserverInfo;
import jakarta.enterprise.inject.build.compatible.spi.ScopeInfo;
import jakarta.enterprise.inject.build.compatible.spi.StereotypeInfo;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.interceptor.Interceptor;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class BeanInfoImpl implements BeanInfo {
    protected final BeanElement beanElement;
    private final VisitorContext visitorContext;
    private final Types types;
    private final ClassInfoImpl classInfo;

    BeanInfoImpl(BeanElement beanElement, VisitorContext visitorContext) {
        this.beanElement = beanElement;
        this.visitorContext = visitorContext;
        this.types = new TypesImpl(visitorContext);
        this.classInfo = new ClassInfoImpl(beanElement.getDeclaringClass(), new TypesImpl(visitorContext), visitorContext);
    }

    ClassInfoImpl getClassInfo() {
        return classInfo;
    }

    @Override
    public ScopeInfo scope() {
        final String scope = beanElement.getScope().orElse(null);
        if (scope != null) {
            final ClassElement scopeType = visitorContext.getClassElement(scope).orElse(null);
            if (scopeType != null) {

                return new ScopeInfo() {
                    @Override
                    public String name() {
                        return scope;
                    }

                    @Override
                    public ClassInfo annotation() {
                        return new ClassInfoImpl(scopeType, new TypesImpl(visitorContext), visitorContext);
                    }

                    @Override
                    public boolean isNormal() {
                        return scopeType.hasStereotype(NormalScope.class);
                    }
                };
            }
        }
        return null;
    }

    @Override
    public Collection<Type> types() {
        return beanElement.getBeanTypes()
                .stream().map(ce -> TypeFactory.createType(
                        ce,
                        new TypesImpl(visitorContext),
                        visitorContext
                )).collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Collection<AnnotationInfo> qualifiers() {
        final Collection<String> qualifiers = beanElement.getQualifiers();
        return annotationNamesToInfo(beanElement, qualifiers);
    }

    private List<AnnotationInfo> annotationNamesToInfo(
            Element element, Collection<String> qualifiers) {
        if (!qualifiers.isEmpty()) {
            return qualifiers.stream()
                    .flatMap(n -> visitorContext.getClassElement(n).stream())
                    .map(classElement -> {
                final AnnotationValue<Annotation> annotation = element.getAnnotation(classElement.getName());
                return new AnnotationInfoImpl(types, visitorContext, annotation);
            }).collect(Collectors.toUnmodifiableList());
        }
        return Collections.emptyList();
    }

    @Override
    public ClassInfo declaringClass() {
        return classInfo;
    }

    @Override
    public boolean isClassBean() {
        final Element producingElement = beanElement.getProducingElement();
        if (beanElement.hasAnnotation(Interceptor.class)) {
            // exclude interceptors
            return false;
        }
        return producingElement instanceof ClassElement
                || (producingElement instanceof BeanElementBuilder
                            && ((BeanElementBuilder) producingElement).getOriginatingElement() instanceof ClassElement);
    }

    @Override
    public boolean isProducerMethod() {
        return beanElement.getProducingElement() instanceof MethodElement;
    }

    @Override
    public boolean isProducerField() {
        return beanElement.getProducingElement() instanceof FieldElement;
    }

    @Override
    public boolean isSynthetic() {
        // ??
        return false;
    }

    @Override
    public MethodInfo producerMethod() {
        if (isProducerMethod()) {
            return new MethodInfoImpl(
                    this.classInfo,
                    (MethodElement) beanElement.getProducingElement(),
                    new TypesImpl(visitorContext),
                    visitorContext
            );
        }
        return null;
    }

    @Override
    public FieldInfo producerField() {
        if (isProducerField()) {
            return new FieldInfoImpl(
                    this.classInfo,
                    (FieldElement) beanElement.getProducingElement(),
                    new TypesImpl(visitorContext),
                    visitorContext
            );
        }
        return null;
    }

    @Override
    public boolean isAlternative() {
        return beanElement.hasDeclaredAnnotation(Alternative.class);
    }

    @Override
    public Integer priority() {
        final OptionalInt i = beanElement.intValue(Order.class);
        if (i.isPresent()) {
            return -i.getAsInt();
        }
        return null;
    }

    @Override
    public String name() {
        return beanElement.stringValue(AnnotationUtil.NAMED).orElse(null);
    }

    @Override
    public DisposerInfo disposer() {
        // TODO
        return null;
    }

    @Override
    public Collection<StereotypeInfo> stereotypes() {
        // TODO
        return Collections.emptyList();
    }

    @Override
    public Collection<InjectionPointInfo> injectionPoints() {
        return beanElement.getInjectionPoints()
                .stream().map((element -> new InjectionPointInfo() {
                    @Override
                    public Type type() {
                        return declaration().asType();
                    }

                    @Override
                    public Collection<AnnotationInfo> qualifiers() {
                        final List<String> qualifiers = element.getAnnotationMetadata()
                                .getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER);
                        return annotationNamesToInfo(element, qualifiers);
                    }

                    @Override
                    public DeclarationInfoImpl declaration() {
                        if (element instanceof FieldElement) {
                            return new FieldInfoImpl(
                                    BeanInfoImpl.this.classInfo,
                                    (FieldElement) element,
                                    new TypesImpl(visitorContext),
                                    visitorContext
                            );
                        } else if (element instanceof MethodElement) {
                            return new MethodInfoImpl(
                                    BeanInfoImpl.this.classInfo,
                                    (MethodElement) element,
                                    new TypesImpl(visitorContext),
                                    visitorContext
                            );
                        }
                        throw new IllegalStateException("Unknown declaration type");
                    }
                })).collect(Collectors.toUnmodifiableList());
    }

    public List<ObserverInfo> observers() {
        Element producingElement = beanElement.getProducingElement();
        if (producingElement instanceof BeanElementBuilder) {
            producingElement = ((BeanElementBuilder) producingElement).getProducingElement();
        }
        if (producingElement instanceof ClassElement) {
            ClassElement ce = (ClassElement) producingElement;
            return ce.getEnclosedElements(
                    ElementQuery.ALL_METHODS.onlyInstance()
                            .filter(m -> m.hasParameters() &&
                                                    Arrays.stream(m.getParameters()).anyMatch(p -> p.hasDeclaredAnnotation(Observes.class) || p.hasDeclaredAnnotation(ObservesAsync.class)))
            ).stream().flatMap(me -> {
                final ParameterElement pe = Arrays.stream(me.getParameters())
                        .filter(p -> p.hasDeclaredAnnotation(Observes.class) || p.hasDeclaredAnnotation(ObservesAsync.class))
                        .findFirst().orElse(null);
                if (pe != null) {

                    return Stream.of(new MethodObserverInfoImpl(
                            this,
                            me,
                            pe, visitorContext
                    ));
                }
                return Stream.empty();
            }).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

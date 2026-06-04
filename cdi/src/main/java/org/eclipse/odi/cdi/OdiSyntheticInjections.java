/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package org.eclipse.odi.cdi;

import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticInjections;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.TypeLiteral;
import org.eclipse.odi.cdi.context.DependentContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

final class OdiSyntheticInjections implements SyntheticInjections {
    private static final ThreadLocal<Deque<OdiSyntheticInjections>> CREATOR_INJECTIONS =
            ThreadLocal.withInitial(ArrayDeque::new);

    private final OdiBeanContainer beanContainer;
    private final List<OdiSyntheticInjectionPoint> injectionPoints;
    private final DependentContext dependentContext;
    private final InjectionPoint consumerInjectionPoint;
    private final OdiBean<?> syntheticBean;
    private final BeanDefinition<?> syntheticBeanDefinition;
    private final boolean creatorInjections;
    private final AtomicBoolean destroyed = new AtomicBoolean();

    OdiSyntheticInjections(OdiBeanContainer beanContainer,
                           List<OdiSyntheticInjectionPoint> injectionPoints,
                           DependentContext dependentContext,
                           InjectionPoint consumerInjectionPoint,
                           BeanDefinition<?> syntheticBeanDefinition,
                           boolean creatorInjections) {
        this.beanContainer = beanContainer;
        this.injectionPoints = injectionPoints;
        this.dependentContext = dependentContext;
        this.consumerInjectionPoint = consumerInjectionPoint;
        this.syntheticBeanDefinition = syntheticBeanDefinition;
        this.syntheticBean = syntheticBeanDefinition == null ? null : beanContainer.getBean(syntheticBeanDefinition);
        this.creatorInjections = creatorInjections;
        if (creatorInjections) {
            CREATOR_INJECTIONS.get().push(this);
        }
    }

    @Override
    public <T> T get(Class<T> type, Annotation... qualifiers) {
        validateRegistered(type, qualifiers);
        if (type == InjectionPoint.class) {
            return type.cast(resolveInjectionPoint());
        }
        return resolve(Argument.of(type), qualifiers);
    }

    @Override
    public <T> T get(TypeLiteral<T> type, Annotation... qualifiers) {
        Class<?> rawType = rawType(type.getType());
        validateRegistered(rawType, qualifiers);
        if (rawType == InjectionPoint.class) {
            return (T) resolveInjectionPoint();
        }
        return resolve((Argument<T>) Argument.of(type.getType()), qualifiers);
    }

    void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            dependentContext.destroy();
        }
    }

    static void releaseCreatorInjections(BeanDefinition<?> beanDefinition, OdiCreationalContext<?> creationalContext) {
        Deque<OdiSyntheticInjections> injections = CREATOR_INJECTIONS.get();
        if (injections.isEmpty()) {
            return;
        }
        List<OdiSyntheticInjections> deferred = new ArrayList<>();
        while (!injections.isEmpty()) {
            OdiSyntheticInjections candidate = injections.pop();
            if (candidate.creatorInjections && candidate.matchesSyntheticBean(beanDefinition)) {
                creationalContext.addDependentContext(candidate.dependentContext);
                break;
            }
            deferred.add(candidate);
        }
        for (int i = deferred.size() - 1; i >= 0; i--) {
            injections.push(deferred.get(i));
        }
        if (injections.isEmpty()) {
            CREATOR_INJECTIONS.remove();
        }
    }

    private <T> T resolve(Argument<T> type, Annotation[] qualifiers) {
        InjectionPoint injectionPoint = new SyntheticInjectionPoint(
                syntheticBean,
                type.asType(),
                normalizeQualifiers(qualifiers)
        );
        return new OdiInstanceImpl<>(
                beanContainer,
                dependentContext,
                Argument.OBJECT_ARGUMENT,
                injectionPoint,
                null
        ).select(type, beanContainer.getOdiAnnotations().resolveQualifier(qualifiers)).get();
    }

    private InjectionPoint resolveInjectionPoint() {
        if (consumerInjectionPoint == null) {
            throw new IllegalStateException("InjectionPoint is not available for this synthetic injection lookup");
        }
        return consumerInjectionPoint;
    }

    private boolean matchesSyntheticBean(BeanDefinition<?> beanDefinition) {
        return syntheticBeanDefinition == null
                || syntheticBeanDefinition == beanDefinition
                || syntheticBeanDefinition.equals(beanDefinition);
    }

    private void validateRegistered(Class<?> type, Annotation[] qualifiers) {
        for (OdiSyntheticInjectionPoint injectionPoint : injectionPoints) {
            if (injectionPoint.matches(type, qualifiers)) {
                return;
            }
        }
        throw new IllegalArgumentException("Synthetic injection point is not registered: " + type.getName());
    }

    private static Class<?> rawType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        throw new IllegalArgumentException("Unsupported synthetic injection type: " + type);
    }

    private static Set<Annotation> normalizeQualifiers(Annotation[] qualifiers) {
        if (qualifiers == null || qualifiers.length == 0) {
            return Set.of(Default.Literal.INSTANCE);
        }
        return Set.of(qualifiers);
    }

    private record SyntheticInjectionPoint(Bean<?> bean,
                                           Type type,
                                           Set<Annotation> qualifiers) implements InjectionPoint {
        private SyntheticInjectionPoint {
            Objects.requireNonNull(type, "Injection point type cannot be null");
            qualifiers = qualifiers == null || qualifiers.isEmpty()
                    ? Set.of(Default.Literal.INSTANCE)
                    : Set.copyOf(qualifiers);
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return qualifiers;
        }

        @Override
        public Bean<?> getBean() {
            return bean;
        }

        @Override
        public Member getMember() {
            return null;
        }

        @Override
        public Annotated getAnnotated() {
            return null;
        }

        @Override
        public boolean isDelegate() {
            return false;
        }

        @Override
        public boolean isTransient() {
            return false;
        }
    }
}

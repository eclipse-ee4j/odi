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
package com.oracle.odi.cdi;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.Prioritized;
import jakarta.inject.Named;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the {@link jakarta.enterprise.inject.spi.Bean} interface for ODI.
 *
 * @param <T> The generic type of the bean
 */
@Internal
public class OdiBeanImpl<T> implements OdiBean<T>, Prioritized {

    private final BeanDefinition<T> definition;
    private final BeanContext beanContext;
    private Class<? extends Annotation> scope;

    /**
     * Default constructor.
     *
     * @param beanContext The bean context
     * @param definition  The definition
     */
    public OdiBeanImpl(BeanContext beanContext, BeanDefinition<T> definition) {
        this.beanContext = beanContext;
        this.definition = Objects.requireNonNull(definition, "Bean definition cannot be null");
    }

    @Override
    public Class<?> getBeanClass() {
        if (definition instanceof AdvisedBeanType) {
            return ((AdvisedBeanType<?>) definition).getInterceptedType();
        }
        return definition.getBeanType();
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        @SuppressWarnings("rawtypes")
        Stream<? extends io.micronaut.inject.InjectionPoint> injectionPoints =
                Stream.concat(definition.getInjectedFields().stream(), definition.getInjectedMethods().stream());
        injectionPoints = Stream.concat(
                injectionPoints,
                Stream.of(definition.getConstructor())
        );
        return injectionPoints.flatMap((ip) -> {
            if (ip instanceof FieldInjectionPoint) {
                return Stream.of(new OdiInjectionPoint(this, ip, ((FieldInjectionPoint<?, ?>) ip).asArgument()));
            } else if (ip instanceof MethodInjectionPoint) {
                MethodInjectionPoint<?, ?> mip = (MethodInjectionPoint) ip;
                return Stream.of(mip.getArguments()).map((arg) -> new OdiInjectionPoint(this, mip, arg));
            } else if (ip instanceof ConstructorInjectionPoint) {
                ConstructorInjectionPoint<?> cip = (ConstructorInjectionPoint) ip;
                return Stream.of(cip.getArguments()).map((arg) -> new OdiInjectionPoint(this, cip, arg));
            }
            return Stream.empty();
        }).collect(Collectors.toSet());
    }

    @Override
    public T create(CreationalContext<T> creationalContext) {
        try {
            BeanRegistration<T> beanRegistration = beanContext.getBeanRegistration(definition);
            if (creationalContext != null) {
                creationalContext.push(beanRegistration.bean());
                if (creationalContext instanceof OdiCreationalContext) {
                    OdiCreationalContext<T> odiCreationalContext = (OdiCreationalContext<T>) creationalContext;
                    odiCreationalContext.setCreatedBean(beanRegistration);
                }
            }
            return beanRegistration.getBean();
        } catch (NonUniqueBeanException e) {
            throw new AmbiguousResolutionException(e.getMessage(), e);
        } catch (NoSuchBeanException e) {
            throw new UnsatisfiedResolutionException(e.getMessage(), e);
        } catch (BeanInstantiationException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else {
                throw new CreationException(e.getMessage(), e);
            }
        } catch (Throwable e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                throw new CreationException(e.getMessage(), e);
            }
        }
    }

    @Override
    public void destroy(T instance, CreationalContext<T> creationalContext) {
        if (!(creationalContext instanceof OdiCreationalContext)) {
            throw new IllegalArgumentException("Not an ODI Creational Context");
        }
        creationalContext.release();
    }

    @Override
    public Set<Type> getTypes() {
        if (definition instanceof AdvisedBeanType) {
            return Set.of(((AdvisedBeanType<?>) definition).getInterceptedType());
        }
        return Set.of(definition.getBeanType());
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return AnnotationUtils.synthesizeQualifierAnnotations(definition.getAnnotationMetadata());
    }

    @Override
    public Class<? extends Annotation> getScope() {
        if (this.scope == null) {
            final AnnotationMetadata annotationMetadata = definition.getAnnotationMetadata();
            this.scope = MetaAnnotationSupport.resolveDeclaredScope(annotationMetadata);
        }
        return this.scope;
    }

    @Override
    public String getName() {
        return definition.getAnnotationMetadata().stringValue(Named.class).orElse(null);
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        final AnnotationMetadata annotationMetadata = definition.getAnnotationMetadata();
        return new HashSet<>(annotationMetadata.getAnnotationTypesByStereotype(Stereotype.class));
    }

    @Override
    public boolean isAlternative() {
        return definition.hasAnnotation(Alternative.class) || definition.hasStereotype(Alternative.class);
    }

    @Override
    public int getPriority() {
        final int i = definition.intValue(Order.class).orElse(0);
        if (i != 0) {
            return -i;
        }
        return 0;
    }

    @Override
    public int hashCode() {
        Argument<?> argument = asArgument();
        final io.micronaut.context.Qualifier<T> qualifier = definition.getDeclaredQualifier();
        return Objects.hash(argument, qualifier);
    }

    private Argument<?> asArgument() {
        Argument<?> argument = definition.asArgument();
        if (definition instanceof AdvisedBeanType) {
            final Class<?> interceptedType = ((AdvisedBeanType<?>) definition).getInterceptedType();
            argument = Argument.of(interceptedType, argument.getTypeParameters());
        }
        return argument;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OdiBeanImpl)) {
            return false;
        }
        OdiBeanImpl<?> odiBean = (OdiBeanImpl<?>) o;
        Argument<?> argument = asArgument();
        return Objects.equals(argument, ((OdiBeanImpl<?>) o).asArgument())
                && Objects.equals(definition.getDeclaredQualifier(), odiBean.definition.getDeclaredQualifier());
    }

}

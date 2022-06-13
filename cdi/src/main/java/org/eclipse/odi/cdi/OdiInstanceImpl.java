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
package org.eclipse.odi.cdi;

import org.eclipse.odi.cdi.context.NoOpDependentContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.TypeLiteral;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class OdiInstanceImpl<T> implements OdiInstance<T> {

    private final OdiBeanContainer beanContainer;
    private final Context context;

    private final Argument<T> beanType;
    private final InjectionPoint injectionPoint;
    @Nullable
    private final Qualifier<T> qualifier;
    @Nullable
    private OdiBean<T> bean;

    private final Map<T, CreationalContext<T>> created = new HashMap<>();

    OdiInstanceImpl(OdiBeanContainer beanContainer,
                    @Nullable
                    Context context,
                    Argument<T> beanType,
                    @Nullable InjectionPoint injectionPoint,
                    @Nullable Qualifier<T> qualifier) {
        this.beanContainer = beanContainer;
        this.context = context == null ? NoOpDependentContext.INSTANCE : context;
        this.beanType = beanType;
        this.qualifier = qualifier;
        this.injectionPoint = injectionPoint;
    }

    OdiInstanceImpl(OdiBeanContainer beanContainer,
                    @Nullable
                    Context context,
                    Argument<T> beanType,
                    Annotation... annotations) {
        this(beanContainer, context, beanType, null, beanContainer.getOdiAnnotations().resolveQualifier(annotations));
    }

    @Override
    @NonNull
    public <U extends T> Instance<U> select(@NonNull Argument<U> argument, @Nullable Qualifier<U> qualifier) {
        if (InjectionPoint.class.equals(argument.getType()) && injectionPoint != null) {
            //noinspection unchecked
            return new ResolvedInstanceImpl<>((U) injectionPoint);
        } else {
            return new OdiInstanceImpl<>(
                    beanContainer,
                    context,
                    argument,
                    null,
                    withQualifier(qualifier)
            );
        }
    }

    @Override
    public Instance<T> select(Annotation... qualifiers) {
        return new OdiInstanceImpl<>(
                beanContainer,
                context,
                beanType,
                null,
                withAnnotations(qualifiers)
        );
    }

    @Override
    public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return select(Argument.of(subtype), withAnnotations(qualifiers));
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return select((Argument<U>) Argument.of(subtype.getType()), withAnnotations(qualifiers));
    }

    @Override
    public boolean isUnsatisfied() {
        try {
            getBean();
            return false;
        } catch (UnsatisfiedResolutionException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isAmbiguous() {
        try {
            getBean();
            return false;
        } catch (AmbiguousResolutionException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void destroy(T instance) {
        CreationalContext<T> creationalContext = created.get(instance);
        if (creationalContext != null) {
            creationalContext.release();
        } else {
            beanContainer.getBeanContext().destroyBean(instance);
        }
    }

    @Override
    public Handle<T> getHandle() {
        return toHandle(getBean());
    }

    private OdiBean<T> getBean() {
        try {
            Qualifier<T> beanQualifier = this.qualifier;
            if (beanQualifier == null) {
                beanQualifier = DefaultQualifier.instance();
            }
            if (bean == null) {
                bean = beanContainer.getBean(beanType, beanQualifier);
            }
            return bean;
        } catch (UnsatisfiedResolutionException | AmbiguousResolutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CreationException(e.getMessage(), e);
        }
    }

    private Handle<T> toHandle(OdiBean<T> odiBean) {
        return new Handle<>() {

            private CreationalContext<T> creationalContext;
            private boolean destroyed;

            @Override
            public T get() {
                if (destroyed) {
                    throw new IllegalStateException("Instance already destroyed!");
                }
                if (creationalContext == null) {
                    creationalContext = beanContainer.createCreationalContext(odiBean);
                }
                return context.get(odiBean, creationalContext);
            }

            @Override
            public Bean<T> getBean() {
                return odiBean;
            }

            @Override
            public void destroy() {
                if (destroyed || creationalContext == null) {
                    return;
                }
                creationalContext.release();
                creationalContext = null;
                destroyed = true;
            }

            @Override
            public void close() {
                destroy();
            }
        };
    }

    @Override
    public List<Handle<T>> handles() {
        return beanContainer.getBeans(beanType, qualifier).stream()
                .map(this::toHandle)
                .collect(Collectors.toList());
    }

    @Override
    public T get() {
        Bean<T> resolvedBean = getBean();
        CreationalContext<T> creationalContext = beanContainer.createCreationalContext(resolvedBean);
        T instance = context.get(resolvedBean, creationalContext);
        created.put(instance, creationalContext);
        return instance;
    }

    @Override
    @NonNull
    public Iterator<T> iterator() {
        return stream().iterator();
    }

    @Override
    public Stream<T> stream() {
        return handles().stream().map(Handle::get);
    }

    private <K> Qualifier<K> withAnnotations(Annotation[] qualifiers) {
        return withQualifier(beanContainer.getOdiAnnotations().resolveQualifier(qualifiers));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <K> Qualifier<K> withQualifier(Qualifier<?> newQualifier) {
        if (qualifier == null) {
            return (Qualifier<K>) newQualifier;
        }
        if (newQualifier != null) {
            return Qualifiers.byQualifiers(qualifier, (Qualifier) newQualifier);
        }
        return (Qualifier<K>) qualifier;
    }

}

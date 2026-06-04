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

import io.micronaut.inject.qualifiers.AnyQualifier;
import org.eclipse.odi.cdi.context.NoOpDependentContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.Prioritized;
import jakarta.enterprise.util.TypeLiteral;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class OdiInstanceImpl<T> implements OdiInstance<T> {

    private final OdiBeanContainer beanContainer;
    private final Context context;

    private final Argument<T> beanType;
    private final InjectionPoint injectionPoint;
    @Nullable
    private final Qualifier<T> qualifier;
    @Nullable
    private OdiBean<T> bean;

    private final Map<Object, CreationalContext<?>> created;

    OdiInstanceImpl(OdiBeanContainer beanContainer,
                    @Nullable
                    Context context,
                    Argument<T> beanType,
                    @Nullable InjectionPoint injectionPoint,
                    @Nullable Qualifier<T> qualifier) {
        this(beanContainer, context, beanType, injectionPoint, qualifier, new HashMap<>());
    }

    private OdiInstanceImpl(OdiBeanContainer beanContainer,
                            @Nullable
                            Context context,
                            Argument<T> beanType,
                            @Nullable InjectionPoint injectionPoint,
                            @Nullable Qualifier<T> qualifier,
                            Map<Object, CreationalContext<?>> created) {
        this.beanContainer = beanContainer;
        this.context = context == null ? NoOpDependentContext.INSTANCE : context;
        this.beanType = beanType;
        this.qualifier = qualifier;
        this.injectionPoint = injectionPoint;
        this.created = created;
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
        return select(argument, qualifier, null);
    }

    @NonNull
    private <U extends T> Instance<U> select(@NonNull Argument<U> argument,
                                             @Nullable Qualifier<U> qualifier,
                                             @Nullable Annotation[] qualifierAnnotations) {
        if (InjectionPoint.class.equals(argument.getType()) && injectionPoint != null) {
            //noinspection unchecked
            return new ResolvedInstanceImpl<>((U) injectionPoint);
        } else {
            return new OdiInstanceImpl<>(
                    beanContainer,
                    context,
                    argument,
                    selectInjectionPoint(argument, qualifierAnnotations),
                    withQualifier(qualifier),
                    created
            );
        }
    }

    @Override
    public Instance<T> select(Annotation... qualifiers) {
        return new OdiInstanceImpl<>(
                beanContainer,
                context,
                beanType,
                selectInjectionPoint(beanType, qualifiers),
                withAnnotations(qualifiers),
                created
        );
    }

    @Override
    public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return select(Argument.of(subtype), withAnnotations(qualifiers), qualifiers);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return select((Argument<U>) Argument.of(subtype.getType()), withAnnotations(qualifiers), qualifiers);
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
        Objects.requireNonNull(instance, "instance");
        CreationalContext<?> creationalContext = created.remove(instance);
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
                return create(odiBean, creationalContext);
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
        return resolveInstanceBeans(beanContainer.getBeans(beanType, qualifier)).stream()
                .map(this::toHandle)
                .collect(Collectors.toList());
    }

    private List<OdiBean<T>> resolveInstanceBeans(Collection<OdiBean<T>> beans) {
        List<OdiBean<T>> prioritizedAlternatives = beans.stream()
                .filter(Bean::isAlternative)
                .filter(bean -> getPriority(bean) > 0)
                .collect(Collectors.toList());
        if (prioritizedAlternatives.isEmpty()) {
            List<OdiBean<T>> nonReserve = beans.stream()
                    .filter(bean -> !bean.isReserve())
                    .collect(Collectors.toList());
            if (!nonReserve.isEmpty() && nonReserve.size() < beans.size()) {
                return nonReserve;
            }
            if (beans.stream().allMatch(Bean::isReserve)) {
                return highestPriorityBeans(beans);
            }
            return beans.stream().collect(Collectors.toList());
        }
        return highestPriorityBeans(prioritizedAlternatives);
    }

    private List<OdiBean<T>> highestPriorityBeans(Collection<OdiBean<T>> beans) {
        int highestPriority = beans.stream()
                .mapToInt(this::getPriority)
                .max()
                .orElse(0);
        if (highestPriority <= 0) {
            return List.of();
        }
        return beans.stream()
                .filter(bean -> getPriority(bean) == highestPriority)
                .sorted(Comparator.comparing(bean -> bean.getBeanClass().getName()))
                .collect(Collectors.toList());
    }

    private int getPriority(Bean<?> bean) {
        if (bean instanceof Prioritized) {
            return ((Prioritized) bean).getPriority();
        }
        return 0;
    }

    @Override
    public T get() {
        OdiBean<T> resolvedBean = getBean();
        CreationalContext<T> creationalContext = beanContainer.createCreationalContext(resolvedBean);
        T instance = create(resolvedBean, creationalContext);
        created.put(instance, creationalContext);
        return instance;
    }

    private T create(OdiBean<T> resolvedBean, CreationalContext<T> creationalContext) {
        if (resolvedBean.getScope() != Dependent.class) {
            @SuppressWarnings("unchecked")
            T reference = (T) beanContainer.getReference(resolvedBean, beanType.getType(), creationalContext);
            return reference;
        }
        if (injectionPoint == null) {
            return context.get(resolvedBean, creationalContext);
        }
        return OdiCurrentInjectionPoint.call(
                resolvedBean,
                injectionPoint,
                () -> context.get(resolvedBean, creationalContext)
        );
    }

    @Nullable
    private InjectionPoint selectInjectionPoint(Argument<?> selectedBeanType, @Nullable Annotation[] qualifierAnnotations) {
        if (!(injectionPoint instanceof OdiInjectionPoint)) {
            return injectionPoint;
        }
        OdiInjectionPoint odiInjectionPoint = (OdiInjectionPoint) injectionPoint;
        Set<Annotation> selectedQualifiers = qualifierAnnotations == null
                ? null
                : mergeQualifiers(injectionPoint.getQualifiers(), qualifierAnnotations);
        return odiInjectionPoint.withArgument(selectedBeanType, selectedQualifiers);
    }

    private static Set<Annotation> mergeQualifiers(Set<Annotation> existingQualifiers, Annotation[] selectedQualifiers) {
        Set<Annotation> qualifiers = new LinkedHashSet<>(existingQualifiers);
        for (Annotation selectedQualifier : selectedQualifiers) {
            qualifiers.add(selectedQualifier);
        }
        return qualifiers;
    }

    @Override
    @NonNull
    public Iterator<T> iterator() {
        return handles().stream().map(Handle::get).iterator();
    }

    @Override
    public Stream<T> stream() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator(), 0), false);
    }

    private <K> Qualifier<K> withAnnotations(Annotation[] qualifiers) {
        return withQualifier(beanContainer.getOdiAnnotations().resolveQualifier(qualifiers));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <K> Qualifier<K> withQualifier(Qualifier<?> newQualifier) {
        if (qualifier == null || qualifier == AnyQualifier.INSTANCE && newQualifier != null) {
            return (Qualifier<K>) newQualifier;
        }
        if (newQualifier != null) {
            return Qualifiers.byQualifiers(qualifier, (Qualifier) newQualifier);
        }
        return (Qualifier<K>) qualifier;
    }

}

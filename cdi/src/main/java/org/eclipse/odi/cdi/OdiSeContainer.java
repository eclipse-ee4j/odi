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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextProvider;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.Eager;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.ResolutionException;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticInjections;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.TypeLiteral;
import org.eclipse.odi.cdi.context.DependentContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
@Factory
final class OdiSeContainer extends CDI<Object>
        implements SeContainer, OdiInstance<Object>, ApplicationContextProvider {
    private static final Map<ApplicationContext, OdiSeContainer> RUNNING_CONTAINERS = new LinkedHashMap<>(5);
    private static final ReentrantReadWriteLock RUNNING_CONTAINERS_LOCK = new ReentrantReadWriteLock();
    private final ApplicationContext applicationContext;
    private final OdiBeanContainerImpl beanContainer;
    private boolean eagerBeansInitialized;

    protected OdiSeContainer(ApplicationContext context) {
        this.applicationContext = context;
        this.beanContainer = new OdiBeanContainerImpl(this, context.getBean(OdiAnnotations.class), context);
        register(context, this);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    synchronized void initializeEagerBeans() {
        if (eagerBeansInitialized) {
            return;
        }
        eagerBeansInitialized = true;
        for (BeanDefinition<?> beanDefinition : applicationContext.getAllBeanDefinitions()) {
            if (isCdiEagerBean(beanDefinition)) {
                OdiBean<?> bean = beanContainer.getBean((BeanDefinition) beanDefinition);
                if (bean.isProxy()) {
                    BeanRegistration<?> beanRegistration = applicationContext.getBeanRegistration((BeanDefinition) beanDefinition);
                    beanRegistration.getBean().toString();
                } else {
                    Context context = beanContainer.getContext(bean.getScope());
                    context.get((OdiBean) bean, beanContainer.createCreationalContext(bean));
                }
            }
        }
    }

    private static boolean isCdiEagerBean(BeanDefinition<?> beanDefinition) {
        return !beanDefinition.getBeanType().getName().startsWith("org.eclipse.odi.cdi.")
                && (beanDefinition.hasAnnotation(Eager.class)
                || beanDefinition.hasStereotype(Eager.class));
    }

    @Override
    public void close() {
        ensureRunning();
        try {
            applicationContext.close();
        } finally {
            unregister(applicationContext);
        }
    }

    static CDI<Object> currentContainer() {
        RUNNING_CONTAINERS_LOCK.writeLock().lock();
        try {
            OdiSeContainer latestRunningContainer = null;
            Iterator<Map.Entry<ApplicationContext, OdiSeContainer>> iterator = RUNNING_CONTAINERS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ApplicationContext, OdiSeContainer> entry = iterator.next();
                OdiSeContainer container = entry.getValue();
                if (entry.getKey().isRunning() && container.isRunning()) {
                    latestRunningContainer = container;
                } else {
                    iterator.remove();
                }
            }
            if (latestRunningContainer != null) {
                return latestRunningContainer;
            }
        } finally {
            RUNNING_CONTAINERS_LOCK.writeLock().unlock();
        }
        throw new IllegalStateException("No running SeContainer present");
    }

    private static void register(ApplicationContext context, OdiSeContainer container) {
        RUNNING_CONTAINERS_LOCK.writeLock().lock();
        try {
            RUNNING_CONTAINERS.put(context, container);
        } finally {
            RUNNING_CONTAINERS_LOCK.writeLock().unlock();
        }
    }

    private static void unregister(ApplicationContext context) {
        RUNNING_CONTAINERS_LOCK.writeLock().lock();
        try {
            RUNNING_CONTAINERS.remove(context);
        } finally {
            RUNNING_CONTAINERS_LOCK.writeLock().unlock();
        }
    }

    static OdiSeContainer findRegistered(ApplicationContext context) {
        RUNNING_CONTAINERS_LOCK.readLock().lock();
        try {
            return RUNNING_CONTAINERS.get(context);
        } finally {
            RUNNING_CONTAINERS_LOCK.readLock().unlock();
        }
    }

    @Override
    public boolean isRunning() {
        return applicationContext.isRunning();
    }

    @Override
    public BeanManager getBeanManager() {
        ensureRunning();
        return applicationContext.findBean(BeanManager.class)
                .orElseThrow(() -> new UnsupportedOperationException("Use CDI.current().getBeanContainer() instead"));
    }

    @Override
    public BeanContainer getBeanContainer() {
        ensureRunning();
        return beanContainer;
    }

    OdiInstance<Object> select(Context context) {
        return new OdiInstanceImpl<>(
                beanContainer,
                context,
                Argument.OBJECT_ARGUMENT,
                null,
                (Qualifier<Object>) null
        );
    }

    @Override
    public <U> OdiInstance<U> select(Argument<U> argument, Qualifier<U> qualifier) {
        ensureRunning();
        return new OdiInstanceImpl<>(
                beanContainer,
                null,
                argument,
                null,
                qualifier
        );
    }

    @Override
    public OdiInstance<Object> select(Annotation... qualifiers) {
        ensureRunning();
        return new OdiInstanceImpl<>(beanContainer, null, Argument.OBJECT_ARGUMENT, qualifiers);
    }

    @Override
    public <U> OdiInstance<U> select(Class<U> subtype, Annotation... qualifiers) {
        ensureRunning();
        return new OdiInstanceImpl<>(beanContainer, null, Argument.of(subtype), qualifiers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <U> OdiInstance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        ensureRunning();
        return new OdiInstanceImpl(beanContainer, null, Argument.of(subtype.getType()), qualifiers);
    }

    @Override
    public boolean isUnsatisfied() {
        return false;
    }

    @Override
    public boolean isAmbiguous() {
        return false;
    }

    @Override
    public void destroy(Object instance) {
        applicationContext.destroyBean(instance);
    }

    @Override
    public Handle<Object> getHandle() {
        return new Handle<>() {
            @Override
            public Object get() {
                return OdiSeContainer.this;
            }

            @Override
            public jakarta.enterprise.inject.spi.Bean<Object> getBean() {
                return new OdiBeanImpl(OdiSeContainer.this.applicationContext, new BeanDefinition() {

                    @Override
                    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
                        return true;
                    }

                    @Override
                    public Class getBeanType() {
                        return SeContainer.class;
                    }
                });
            }

            @Override
            public void destroy() {
                close();
            }

            @Override
            public void close() {
                if (OdiSeContainer.this.applicationContext.isRunning()) {
                    OdiSeContainer.this.close();
                }
            }
        };
    }

    @Override
    public Iterable<Handle<Object>> handles() {
        return Collections.singletonList(getHandle());
    }

    @Override
    public Object get() {
        return this;
    }

    @Override
    public Iterator<Object> iterator() {
        return Collections.singletonList((Object) this).iterator();
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Bean
    @Default
    OdiBeanContainer beanContainer() {
        return beanContainer;
    }

    /**
     * Creates the parameters object for synthetic beans.
     *
     * @param injectionPoint The injection point
     * @return The parameters
     */
    @Bean
    Parameters parameterCreator(ArgumentInjectionPoint<?, ?> injectionPoint) {
        final BeanDefinition<?> declaringBean = injectionPoint.getDeclaringBean();
        return OdiUtils.createParameters(declaringBean);
    }

    @Bean
    SyntheticInjections syntheticInjections(ArgumentInjectionPoint<?, ?> injectionPoint,
                                            BeanResolutionContext resolutionContext,
                                            OdiBeanContainer beanContainer) {
        final BeanDefinition<?> declaringBean = injectionPoint.getDeclaringBean();
        Object value = OdiUtils.getSyntheticParameters(declaringBean)
                .get(OdiSyntheticParameters.INJECTION_POINTS);
        BeanDefinition<?> syntheticBeanDefinition = syntheticBeanDefinition(resolutionContext, declaringBean);
        if (value == null && syntheticBeanDefinition != null) {
            value = OdiUtils.getSyntheticParameters(syntheticBeanDefinition)
                    .get(OdiSyntheticParameters.INJECTION_POINTS);
        }
        List<OdiSyntheticInjectionPoint> injectionPoints = value instanceof List<?> list
                ? (List<OdiSyntheticInjectionPoint>) list
                : List.of();
        return new OdiSyntheticInjections(
                beanContainer,
                injectionPoints,
                new DependentContext(null),
                consumerInjectionPoint(resolutionContext, beanContainer),
                syntheticBeanDefinition,
                true
        );
    }

    private static BeanDefinition<?> syntheticBeanDefinition(BeanResolutionContext resolutionContext,
                                                            BeanDefinition<?> fallback) {
        Map<String, Object> fallbackParameters = OdiUtils.getSyntheticParameters(fallback);
        Object beanType = fallbackParameters.get(OdiSyntheticParameters.BEAN_TYPE);
        if (beanType instanceof String beanTypeName) {
            BeanDefinition<?> beanDefinition = findSyntheticBeanDefinition(resolutionContext, beanTypeName);
            if (beanDefinition != null) {
                return beanDefinition;
            }
        }
        for (BeanResolutionContext.Segment<?, ?> segment : resolutionContext.getPath()) {
            BeanDefinition<?> declaringType = segment.getDeclaringType();
            Class<?> beanClass = declaringType.getDeclaringType().orElse(declaringType.getBeanType());
            if (!SyntheticBeanCreator.class.isAssignableFrom(beanClass) && hasSyntheticInjectionPoints(declaringType)) {
                return declaringType;
            }
        }
        return hasSyntheticInjectionPoints(fallback) ? fallback : null;
    }

    private static BeanDefinition<?> findSyntheticBeanDefinition(BeanResolutionContext resolutionContext,
                                                                String beanTypeName) {
        return resolutionContext.getContext()
                .getAllBeanDefinitions()
                .stream()
                .filter(beanDefinition -> beanDefinition.getBeanType().getName().equals(beanTypeName))
                .filter(OdiSeContainer::hasSyntheticInjectionPoints)
                .findFirst()
                .orElse(null);
    }

    private static boolean hasSyntheticInjectionPoints(BeanDefinition<?> beanDefinition) {
        return OdiUtils.getSyntheticParameters(beanDefinition).containsKey(OdiSyntheticParameters.INJECTION_POINTS);
    }

    private static jakarta.enterprise.inject.spi.InjectionPoint consumerInjectionPoint(BeanResolutionContext resolutionContext,
                                                                                      OdiBeanContainer beanContainer) {
        for (BeanResolutionContext.Segment<?, ?> segment : resolutionContext.getPath()) {
            InjectionPoint<?> injectionPoint = segment.getInjectionPoint();
            if (injectionPoint == null) {
                continue;
            }
            BeanDefinition<?> declaringBean = injectionPoint.getDeclaringBean();
            Class<?> declaringType = declaringBean.getDeclaringType().orElse(declaringBean.getBeanType());
            if (!SyntheticBeanCreator.class.isAssignableFrom(declaringType)) {
                Argument<?> argument = injectionPoint instanceof ArgumentCoercible<?> argumentCoercible
                        ? argumentCoercible.asArgument()
                        : segment.getArgument();
                return new OdiInjectionPoint(
                        resolutionContext.getContext().getClassLoader(),
                        new OdiBeanImpl<>(beanContainer.getBeanContext(), declaringBean),
                        injectionPoint,
                        argument
                );
            }
        }
        return null;
    }

    @Bean
    @Default
    SeContainer seContainer() {
        initializeEagerBeans();
        return this;
    }

    @Bean
    @Any
    jakarta.enterprise.inject.spi.Bean<?> getBean(InjectionPoint<?> injectionPoint) {
        if (injectionPoint instanceof ArgumentCoercible) {
            final Argument<?> argument = ((ArgumentCoercible<?>) injectionPoint).asArgument();
            try {
                return beanContainer.getBean(
                        argument.getFirstTypeVariable()
                                .orElseThrow(() -> new UnsatisfiedResolutionException("Cannot resolve bean for injection point:"
                                        + " " + injectionPoint)),
                        Qualifiers.forArgument(argument)
                );
            } catch (NonUniqueBeanException e) {
                throw new AmbiguousResolutionException(e.getMessage(), e);
            } catch (NoSuchBeanException e) {
                throw new UnsatisfiedResolutionException(e.getMessage(), e);
            } catch (Throwable t) {
                throw new ResolutionException(t.getMessage(), t);
            }
        }

        throw new UnsatisfiedResolutionException("Cannot resolve bean for injection point: " + injectionPoint);
    }

    private void ensureRunning() {
        if (!isRunning()) {
            throw new IllegalStateException("SeContainer already shutdown");
        }
    }

}

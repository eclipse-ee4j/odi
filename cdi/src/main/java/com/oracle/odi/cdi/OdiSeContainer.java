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

import com.oracle.odi.cdi.annotation.DisposerMethod;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextProvider;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.ResolutionException;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
@Factory
final class OdiSeContainer extends CDI<Object>
        implements SeContainer, OdiInstance<Object>,  ApplicationContextProvider, ExecutableMethodProcessor<DisposerMethod> {
    static final Map<ApplicationContext, OdiSeContainer> RUNNING_CONTAINERS = Collections.synchronizedMap(new LinkedHashMap<>(5));
    private static final Logger LOG = LoggerFactory.getLogger(OdiSeContainer.class);
    private final ApplicationContext applicationContext;
    private final OdiBeanContainer beanContainer;
    private final Map<DisposerKey, DisposerDef> disposerMethods = new HashMap<>(20);

    private final MicronautContext ctx = new MicronautContext();

    protected OdiSeContainer(ApplicationContext context) {
        this.applicationContext = context;
        this.beanContainer = new OdiBeanContainerImpl(this, context);
        RUNNING_CONTAINERS.put(context, this);
    }

    @Override
    public void close() {
        try {
            applicationContext.close();
        } finally {
            RUNNING_CONTAINERS.remove(applicationContext);
        }
    }

    @Override
    public boolean isRunning() {
        return applicationContext.isRunning();
    }

    @Override
    public BeanManager getBeanManager() {
        throw new UnsupportedOperationException("Use CDI.current().getBeanContainer() instead");
    }

    @Override
    @Singleton
    public BeanContainer getBeanContainer() {
        return beanContainer;
    }

    OdiInstance<Object> select(Context context) {
        return new OdiInstanceImpl<>(applicationContext, beanContainer, context, Argument.OBJECT_ARGUMENT, (Qualifier<Object>) null);
    }

    @Override
    public <U> OdiInstance<U> select(Argument<U> argument, Qualifier<U> qualifier) {
        return new OdiInstanceImpl<>(applicationContext, beanContainer, ctx, argument, qualifier);
    }

    @Override
    public OdiInstance<Object> select(Annotation... qualifiers) {
        if (!isRunning()) {
            throw new IllegalStateException("SeContainer already shutdown");
        }
        return new OdiInstanceImpl<>(applicationContext, beanContainer, ctx, Argument.OBJECT_ARGUMENT, qualifiers);
    }

    @Override
    public <U> OdiInstance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return new OdiInstanceImpl<>(applicationContext, beanContainer, ctx,  Argument.of(subtype), qualifiers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <U> OdiInstance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return new OdiInstanceImpl(applicationContext, beanContainer, ctx, Argument.of(subtype.getType()), qualifiers);
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
        throw new UnsupportedOperationException();
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
    OdiBeanContainer beanContainer() {
        return beanContainer;
    }

    @Bean
    @Any
    jakarta.enterprise.inject.spi.Bean<?> getBean(InjectionPoint<?> injectionPoint) {
        if (injectionPoint instanceof ArgumentCoercible) {
            final Argument<?> argument = ((ArgumentCoercible<?>) injectionPoint).asArgument();
            try {
                final BeanDefinition<?> beanDefinition = this.applicationContext.getBeanDefinition(
                        argument.getFirstTypeVariable()
                                .orElseThrow(() -> new UnsatisfiedResolutionException("Cannot resolve bean for injection point:"
                                                                                              + " " + injectionPoint)),
                        Qualifiers.forArgument(argument)
                );
                return beanContainer.getBean(beanDefinition);
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

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        final Argument<?>[] arguments = method.getArguments();
        for (Argument<?> argument : arguments) {
            if (argument.getAnnotationMetadata().isAnnotationPresent(Disposes.class)) {
                //noinspection unchecked
                this.disposerMethods.put(
                        new DisposerKey(argument),
                        new DisposerDef(beanDefinition, (ExecutableMethod<Object, ?>) method)
                );
                break;
            }
        }
    }

    /**
     * Handler for running disposer methods.
     * @return The BeanPreDestroyEventListener that runs disposer methods
     */
    @Singleton
    @Any
    protected BeanPreDestroyEventListener<Object> onDestroy() {
        return event -> {
            final Object bean = event.getBean();
            final BeanDefinition<?> beanDefinition = event.getBeanDefinition();
            try {
                final DisposerDef disposerDef = disposerMethods.get(new DisposerKey(beanDefinition.asArgument()));
                if (disposerDef != null) {
                    final ExecutableMethod<Object, ?> method = disposerDef.executableMethod;
                    final BeanDefinition<?> targetBean = disposerDef.definition;
                    final Object disposerBean = applicationContext.getBean(targetBean);
                    final Argument<?>[] parameters = method.getArguments();
                    Object[] arguments = new Object[parameters.length];
                    for (int i = 0; i < parameters.length; i++) {
                        Argument<?> parameter = parameters[i];
                        if (parameter.getAnnotationMetadata().hasAnnotation(Disposes.class)) {
                            arguments[i] = bean;
                        } else {
                            arguments[i] = applicationContext.getBean(parameter, Qualifiers.forArgument(parameter));
                        }
                    }
                    method.invoke(disposerBean, arguments);
                }
            } catch (Throwable e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error invoking Disposer method for bean [" + beanDefinition.getBeanType() + "]: " + e.getMessage(),
                              e);
                }
            }
            return bean;
        };
    }

    static final class DisposerKey {
        private final Argument<?> argument;
        private final int typeHashCode;

        DisposerKey(Argument<?> argument) {
            this.argument = argument;
            this.typeHashCode = argument.typeHashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DisposerKey that = (DisposerKey) o;
            return argument.equalsType(that.argument);
        }

        @Override
        public int hashCode() {
            return typeHashCode;
        }
    }

    static final class DisposerDef {
        private final BeanDefinition<?> definition;
        private final ExecutableMethod<Object, ?> executableMethod;

        DisposerDef(BeanDefinition<?> definition, ExecutableMethod<Object, ?> executableMethod) {
            this.definition = definition;
            this.executableMethod = executableMethod;
        }
    }
}

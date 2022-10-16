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
import org.eclipse.odi.cdi.annotation.DisposerMethod;
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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.ResolutionException;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
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
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
@Factory
final class OdiSeContainer extends CDI<Object>
        implements SeContainer, OdiInstance<Object>, ApplicationContextProvider, ExecutableMethodProcessor<DisposerMethod> {
    static final Map<ApplicationContext, OdiSeContainer> RUNNING_CONTAINERS = Collections.synchronizedMap(new LinkedHashMap<>(5));
    private static final Logger LOG = LoggerFactory.getLogger(OdiSeContainer.class);
    private final ApplicationContext applicationContext;
    private final OdiBeanContainerImpl beanContainer;
    private final Map<DisposerKey, DisposerDef> disposerMethods = new HashMap<>(20);
    private final Map<DisposerKey, DisposerDef> anyDisposerMethods = new HashMap<>(20);

    protected OdiSeContainer(ApplicationContext context) {
        this.applicationContext = context;
        this.beanContainer = new OdiBeanContainerImpl(this, context.getBean(OdiAnnotations.class), context);
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
    public BeanContainer getBeanContainer() {
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
        if (!isRunning()) {
            throw new IllegalStateException("SeContainer already shutdown");
        }
        return new OdiInstanceImpl<>(beanContainer, null, Argument.OBJECT_ARGUMENT, qualifiers);
    }

    @Override
    public <U> OdiInstance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return new OdiInstanceImpl<>(beanContainer, null, Argument.of(subtype), qualifiers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <U> OdiInstance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
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
    @Default
    SeContainer seContainer() {
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

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        final Argument<?>[] arguments = method.getArguments();
        for (Argument<?> argument : arguments) {
            if (argument.getAnnotationMetadata().isAnnotationPresent(Disposes.class)) {
                //noinspection unchecked
                Qualifier<Object> qualifier = Qualifiers.forArgument(argument);
                if (qualifier == null) {
                    qualifier = Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, Default.class);
                }
                if (qualifier.contains(AnyQualifier.INSTANCE)) {
                    anyDisposerMethods.put(new DisposerKey(argument, null), new DisposerDef(beanDefinition, method));
                } else {
                    disposerMethods.put(new DisposerKey(argument, qualifier), new DisposerDef(beanDefinition, method));
                }
                break;
            }
        }
    }

    /**
     * Handler for running disposer methods.
     *
     * @return The BeanPreDestroyEventListener that runs disposer methods
     */
    @Singleton
    @Any
    protected BeanPreDestroyEventListener<Object> onDestroy() {
        return event -> {
            final Object bean = event.getBean();
            final BeanDefinition<?> beanDefinition = event.getBeanDefinition();
            try {
                Argument<?> type = beanDefinition.asArgument();
                DisposerDef<Object> disposerDef = disposerMethods.get(new DisposerKey(type, beanDefinition.getDeclaredQualifier()));
                if (disposerDef == null) {
                    disposerDef = anyDisposerMethods.get(new DisposerKey(type, null));
                }
                if (disposerDef != null) {
                    Optional<Class<?>> producedDeclaringType = beanDefinition.getDeclaringType();
                    Optional<Class<?>> disposeDeclaringType = disposerDef.definition.getDeclaringType();
                    if (disposeDeclaringType.isPresent() && producedDeclaringType.isPresent() && !producedDeclaringType.get().equals(disposeDeclaringType.get())) {
                        return bean;
                    }
                    beanContainer.fulfillAndExecuteMethod(disposerDef.definition, disposerDef.executableMethod, argument -> {
                        if (argument.getAnnotationMetadata().hasAnnotation(Disposes.class)) {
                            return bean;
                        }
                        return null;
                    });
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
        private final Qualifier<?> qualifier;
        private final int typeHashCode;

        DisposerKey(Argument<?> argument, @Nullable Qualifier<?> qualifier) {
            this.argument = argument;
            this.qualifier = qualifier;
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
            return argument.equalsType(that.argument) && Objects.equals(qualifier, that.qualifier);
        }

        @Override
        public int hashCode() {
            return typeHashCode;
        }
    }

    static final class DisposerDef<B> {
        private final BeanDefinition<B> definition;
        private final ExecutableMethod<B, Object> executableMethod;

        DisposerDef(BeanDefinition<B> definition, ExecutableMethod<B, Object> executableMethod) {
            this.definition = definition;
            this.executableMethod = executableMethod;
        }
    }
}

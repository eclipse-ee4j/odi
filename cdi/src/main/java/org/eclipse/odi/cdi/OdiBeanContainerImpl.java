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

import io.micronaut.aop.InterceptedProxy;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Reserve;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.UnproxyableResolutionException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.inject.spi.Prioritized;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.odi.cdi.annotation.ObservesMethod;
import org.eclipse.odi.cdi.annotation.reflect.AnnotationReflection;
import org.eclipse.odi.cdi.context.DependentContext;
import org.eclipse.odi.cdi.context.SingletonContext;
import org.eclipse.odi.cdi.events.OdiEventFactory;
import org.eclipse.odi.cdi.events.OdiObserverMethodRegistry;

import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class OdiBeanContainerImpl implements OdiBeanContainer {
    private static final String JAKARTA_INTERCEPTOR_BINDING = "jakarta.interceptor.InterceptorBinding";
    private static final String MICRONAUT_INTERCEPTOR_BINDING = "io.micronaut.aop.InterceptorBinding";

    private final ApplicationContext applicationContext;
    private final OdiSeContainer container;

    private final OdiAnnotations odiAnnotations;
    private OdiObserverMethodRegistry observerMethodRegistry;
    private Event<Object> objectEvent;

    OdiBeanContainerImpl(OdiSeContainer container, OdiAnnotations odiAnnotations, ApplicationContext applicationContext) {
        this.container = container;
        this.odiAnnotations = odiAnnotations;
        this.applicationContext = applicationContext;
    }

    @Override
    public OdiAnnotations getOdiAnnotations() {
        return odiAnnotations;
    }

    @Override
    public <B, R> Object fulfillAndExecuteMethod(BeanDefinition<B> beanDefinition,
                                                 ExecutableMethod<B, R> executableMethod,
                                                 Function<Argument<?>, Object> valueSupplier) {
        return fulfillAndExecuteMethod(beanDefinition, executableMethod, valueSupplier, false);
    }

    @Override
    public <B, R> Object fulfillAndExecuteMethod(BeanDefinition<B> beanDefinition,
                                                 ExecutableMethod<B, R> executableMethod,
                                                 Function<Argument<?>, Object> valueSupplier,
                                                 boolean staticMethod) {
        Argument<?>[] arguments = executableMethod.getArguments();
        Object[] values = new Object[arguments.length];
        try (BeanResolutionContext resolutionContext = new DefaultBeanResolutionContext(getBeanContext(), beanDefinition)) {
            DependentContext dependentContext = new DependentContext(resolutionContext);
            for (int i = 0; i < arguments.length; i++) {
                Argument<?> argument = arguments[i];
                Object value = valueSupplier.apply(argument);
                if (value != null) {
                    values[i] = value;
                } else {
                    try (BeanResolutionContext.Path ignore = resolutionContext.getPath().pushMethodArgumentResolve(
                            beanDefinition,
                            executableMethod.getMethodName(),
                            argument,
                            arguments
                    )) {
                        if (argument.getType() == Instance.class) {
                            Instance<?> instance = createInstance(dependentContext, false).select(argument.getFirstTypeVariable()
                                    .orElseThrow(() -> new IllegalArgumentException("Expected the type of Instance!")));
                            values[i] = instance;
                        } else {
                            Instance<?> instance = createInstance(dependentContext, false).select(argument);
                            values[i] = instance.get();
                        }
                    }
                }
            }
            try {
                if (!staticMethod) {
                    OdiBean<B> bean = getBean(beanDefinition);
                    Optional<MethodInvocation<B, R>> proxyInvocation = findProxyMethodInvocation(bean, executableMethod, dependentContext);
                    if (proxyInvocation.isPresent()) {
                        return proxyInvocation.get().invoke(values);
                    }
                    CreationalContext<B> creationalContext = createCreationalContext(bean);
                    Context beanContext = odiAnnotations.isDependent(bean.getScope()) ? dependentContext : getContext(bean.getScope());
                    B beanInstance = beanContext.get(bean, creationalContext);
                    return executableMethod.invoke(beanInstance, values);
                }
                return executableMethod.invoke(null, values);
            } finally {
                dependentContext.destroy();
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <B, R> Optional<MethodInvocation<B, R>> findProxyMethodInvocation(OdiBean<B> bean,
                                                                              ExecutableMethod<B, R> executableMethod,
                                                                              DependentContext dependentContext) {
        BeanDefinition<B> beanDefinition = bean.getBeanDefinition();
        Optional<BeanDefinition<B>> proxyBeanDefinition = applicationContext.findProxyBeanDefinition(beanDefinition.asArgument(), beanDefinition.getDeclaredQualifier());
        return proxyBeanDefinition
                .map(proxyDefinition -> {
                    OdiBean<B> proxyOdiBean = getBean(proxyDefinition);
                    CreationalContext<B> proxyCreationalContext = createCreationalContext(proxyOdiBean);
                    Context proxyContext = odiAnnotations.isDependent(proxyOdiBean.getScope()) ? dependentContext : getContext(proxyOdiBean.getScope());
                    B proxyBean = proxyContext.get(proxyOdiBean, proxyCreationalContext);
                    Optional<ExecutableMethod<B, R>> proxyMethod = (Optional) proxyDefinition.findMethod(
                            executableMethod.getMethodName(),
                            executableMethod.getArgumentTypes()
                    );
                    if (shouldInvokeObserverOnProxyTarget(beanDefinition, proxyDefinition, executableMethod)
                            && proxyBean instanceof InterceptedProxy<?> interceptedProxy) {
                        return new MethodInvocation<>((B) interceptedProxy.interceptedTarget(), executableMethod);
                    }
                    return new MethodInvocation<>(proxyBean, proxyMethod.orElse(executableMethod));
                });
    }

    private boolean shouldInvokeObserverOnProxyTarget(BeanDefinition<?> beanDefinition,
                                                      BeanDefinition<?> proxyDefinition,
                                                      ExecutableMethod<?, ?> executableMethod) {
        return executableMethod.hasAnnotation(ObservesMethod.class)
                && !hasCdiInterceptorBinding(beanDefinition)
                && !hasCdiInterceptorBinding(proxyDefinition)
                && !hasCdiInterceptorBinding(executableMethod);
    }

    private boolean hasCdiInterceptorBinding(AnnotationMetadataProvider metadataProvider) {
        AnnotationMetadata annotationMetadata = metadataProvider.getAnnotationMetadata();
        return hasCdiInterceptorBinding(annotationMetadata, JAKARTA_INTERCEPTOR_BINDING)
                || hasCdiInterceptorBinding(annotationMetadata, MICRONAUT_INTERCEPTOR_BINDING);
    }

    private boolean hasCdiInterceptorBinding(AnnotationMetadata annotationMetadata, String stereotype) {
        return annotationMetadata
                .getAnnotationNamesByStereotype(stereotype)
                .stream()
                .anyMatch(annotationName -> !JAKARTA_INTERCEPTOR_BINDING.equals(annotationName)
                        && !MICRONAUT_INTERCEPTOR_BINDING.equals(annotationName));
    }

    private record MethodInvocation<B, R>(B bean, ExecutableMethod<B, R> executableMethod) {
        Object invoke(Object[] values) {
            return executableMethod.invoke(bean, values);
        }
    }

    @Override
    public <T> OdiBeanImpl<T> getBean(BeanDefinition<T> beanDefinition) {
        return new OdiBeanImpl<>(applicationContext, beanDefinition);
    }

    @Override
    public <T> OdiBeanImpl<T> getBean(Argument<T> argument, io.micronaut.context.Qualifier<T> qualifier) {
        Collection<BeanDefinition<T>> beanDefinitions = resolveBeanDefinitions(getBeanDefinitions(argument, qualifier));
        if (beanDefinitions.isEmpty()) {
            throw new UnsatisfiedResolutionException("No bean found for argument: " + argument + " and qualifier: " + qualifier);
        }
        if (beanDefinitions.size() > 1) {
            throw new AmbiguousResolutionException("Multiple beans found for argument: " + argument + " and qualifier: " + qualifier);
        }
        return new OdiBeanImpl<>(applicationContext, beanDefinitions.iterator().next());
    }

    @Override
    public <T> Collection<OdiBean<T>> getBeans(Argument<T> argument, io.micronaut.context.Qualifier<T> qualifier) {
        return getBeanDefinitions(argument, qualifier).stream()
                .map(bd -> new OdiBeanImpl<>(applicationContext, bd))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public <T> Collection<BeanDefinition<T>> getBeanDefinitions(Argument<T> argument, io.micronaut.context.Qualifier<T> qualifier) {
        if (qualifier == null) {
            qualifier = DefaultQualifier.instance();
        }
        Collection<BeanDefinition<T>> beanDefinitions = findBeanDefinitions(argument, qualifier);
        Class<?> primitiveType = ReflectionUtils.getPrimitiveType(argument.getType());
        Class<?> wrapperType = argument.getWrapperType();
        if (primitiveType != argument.getType()) {
            return mergeBeanDefinitions(beanDefinitions, findBeanDefinitions((Argument<T>) Argument.of(primitiveType), qualifier));
        }
        if (wrapperType != argument.getType()) {
            return mergeBeanDefinitions(beanDefinitions, findBeanDefinitions((Argument<T>) Argument.of(wrapperType), qualifier));
        }
        return beanDefinitions;
    }

    private <T> Collection<BeanDefinition<T>> findBeanDefinitions(Argument<T> argument, io.micronaut.context.Qualifier<T> qualifier) {
        Collection<BeanDefinition<T>> beanDefinitions = applicationContext.getBeanDefinitions(argument, qualifier);
        if (qualifier instanceof DefaultQualifier) {
            return beanDefinitions.stream()
                    .filter(OdiBeanContainerImpl::isEnabledBeanDefinition)
                    .filter(DefaultQualifier::hasDefaultQualifier)
                    .collect(Collectors.toList());
        }
        return beanDefinitions.stream()
                .filter(OdiBeanContainerImpl::isEnabledBeanDefinition)
                .collect(Collectors.toList());
    }

    private static boolean isEnabledBeanDefinition(BeanDefinition<?> beanDefinition) {
        if (isReserve(beanDefinition) && getPriority(beanDefinition) <= 0) {
            return false;
        }
        return !beanDefinition.hasStereotype(Alternative.class) || getPriority(beanDefinition) > 0;
    }

    private static <T> Collection<BeanDefinition<T>> mergeBeanDefinitions(Collection<BeanDefinition<T>> first,
                                                                          Collection<BeanDefinition<T>> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        Set<BeanDefinition<T>> merged = new LinkedHashSet<>(first);
        merged.addAll(second);
        return merged;
    }

    private <T> Collection<BeanDefinition<T>> resolveBeanDefinitions(Collection<BeanDefinition<T>> beanDefinitions) {
        if (beanDefinitions.isEmpty() || beanDefinitions.size() == 1) {
            return beanDefinitions;
        }
        List<BeanDefinition<T>> alternatives = beanDefinitions
                .stream()
                .filter(bd -> bd.hasStereotype(Alternative.class))
                .filter(bd -> getPriority(bd) > 0)
                .collect(Collectors.toList());
        if (!alternatives.isEmpty()) {
            return alternatives.stream()
                    .sorted(Comparator.<BeanDefinition<T>>comparingInt(OdiBeanContainerImpl::getPriority).reversed())
                    .limit(1)
                    .collect(Collectors.toList());
        }
        List<BeanDefinition<T>> nonReserve = beanDefinitions
                .stream()
                .filter(bd -> !isReserve(bd))
                .collect(Collectors.toList());
        if (!nonReserve.isEmpty() && nonReserve.size() < beanDefinitions.size()) {
            return nonReserve;
        }
        if (beanDefinitions.stream().allMatch(OdiBeanContainerImpl::isReserve)) {
            return highestUniquePriority(beanDefinitions);
        }
        return beanDefinitions;
    }

    private static <T> List<BeanDefinition<T>> highestUniquePriority(Collection<BeanDefinition<T>> beanDefinitions) {
        List<BeanDefinition<T>> sorted = beanDefinitions.stream()
                .filter(beanDefinition -> getPriority(beanDefinition) > 0)
                .sorted(Comparator.<BeanDefinition<T>>comparingInt(OdiBeanContainerImpl::getPriority).reversed())
                .collect(Collectors.toList());
        if (sorted.isEmpty()) {
            return List.of();
        }
        if (sorted.size() == 1 || getPriority(sorted.get(0)) != getPriority(sorted.get(1))) {
            return List.of(sorted.get(0));
        }
        return sorted.stream()
                .filter(beanDefinition -> getPriority(beanDefinition) == getPriority(sorted.get(0)))
                .collect(Collectors.toList());
    }

    private static boolean isReserve(BeanDefinition<?> beanDefinition) {
        return beanDefinition.hasDeclaredAnnotation(Reserve.class) || beanDefinition.hasDeclaredStereotype(Reserve.class);
    }

    private static int getPriority(BeanDefinition<?> beanDefinition) {
        OptionalInt priority = beanDefinition.intValue(Priority.class);
        if (priority.isPresent()) {
            return priority.getAsInt();
        }
        int order = beanDefinition.intValue(Order.class).orElse(0);
        if (order == 0) {
            return 0;
        }
        if (order == Ordered.HIGHEST_PRECEDENCE) {
            return Integer.MAX_VALUE;
        }
        return -order;
    }

    @Override
    public Object getReference(Bean<?> bean, Type beanType, CreationalContext<?> ctx) {
        if (bean instanceof OdiBean) {
            if (beanType instanceof ParameterizedType) {
                beanType = ((ParameterizedType) beanType).getRawType();
            }
            if (!(beanType instanceof Class)) {
                throw new IllegalStateException("Not implemented");
            }
            OdiBean<Object> odiBean = (OdiBean<Object>) bean;
            CreationalContext creationalContext = ctx;
            if (creationalContext == null) {
                creationalContext = createCreationalContext(odiBean);
            }
            if (odiBean.getBeanDefinition().hasAnnotation(org.eclipse.odi.cdi.annotation.OdiUnproxyableBean.class)) {
                throw new UnproxyableResolutionException("Bean type is not proxyable: " + odiBean.getBeanClass().getName());
            }
            Class<? extends Annotation> scope = odiBean.getScope();
            Object instance;
            if (odiBean.isProxy()) {
                BeanRegistration<Object> beanRegistration = getBeanContext().getBeanRegistration(odiBean.getBeanDefinition());
                instance = beanRegistration.getBean();
                if (creationalContext instanceof OdiCreationalContext) {
                    OdiCreationalContext<Object> odiCreationalContext = (OdiCreationalContext<Object>) creationalContext;
                    odiCreationalContext.push(instance);
                    odiCreationalContext.setCreatedBean(beanRegistration);
                }
            } else if (odiAnnotations.isDependent(scope)) {
                instance = odiBean.create(creationalContext);
            } else {
                if (odiAnnotations.isNormalScope(scope)) {
                    Optional<BeanDefinition<Object>> proxyBeanDefinition = findProxyBeanDefinitionForReference(
                            (Class<Object>) beanType,
                            odiBean.getBeanDefinition().getDeclaredQualifier()
                    );
                    if (proxyBeanDefinition.isPresent()) {
                        BeanRegistration<Object> beanRegistration = getBeanContext().getBeanRegistration(proxyBeanDefinition.get());
                        instance = beanRegistration.getBean();
                        if (creationalContext instanceof OdiCreationalContext) {
                            OdiCreationalContext<Object> odiCreationalContext = (OdiCreationalContext<Object>) creationalContext;
                            odiCreationalContext.push(instance);
                            odiCreationalContext.setCreatedBean(beanRegistration);
                        }
                    } else {
                        Context context = getContext(scope);
                        instance = context.get(odiBean, creationalContext);
                    }
                } else {
                    Context context = getContext(scope);
                    instance = context.get(odiBean, creationalContext);
                }
            }
            if (instance == null) {
                return null;
            }
            if (!isReferenceInstance((Class<?>) beanType, instance)) {
                throw new IllegalArgumentException("Invalid instance!");
            }
            return instance;
        } else {
            throw new IllegalArgumentException("Unsupported by bean type: " + bean.getClass());
        }
    }

    private Optional<BeanDefinition<Object>> findProxyBeanDefinitionForReference(Class<Object> beanType,
                                                                                Qualifier<Object> qualifier) {
        Optional<BeanDefinition<Object>> proxyBeanDefinition = applicationContext.findProxyBeanDefinition(
                Argument.of(beanType),
                qualifier
        );
        if (proxyBeanDefinition.isPresent() || qualifier == null) {
            return proxyBeanDefinition;
        }
        return applicationContext.findProxyBeanDefinition(Argument.of(beanType), null);
    }

    @Override
    public <T> CreationalContext<T> createCreationalContext(Contextual<T> contextual) {
        return new OdiCreationalContext<>(getBeanContext(), contextual);
    }

    @Override
    public Set<Bean<?>> getBeans(Type beanType, Annotation... qualifiers) {
        boolean filterBeanTypes = shouldFilterBeanTypes(beanType);
        Collection<OdiBean<?>> candidates = getBeansForRequiredType(beanType, odiAnnotations.resolveQualifier(qualifiers), filterBeanTypes);
        return candidates.stream()
                .filter(bean -> !filterBeanTypes || matchesBeanType(beanType, bean.getTypes()))
                .collect(Collectors.toUnmodifiableSet());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Collection<OdiBean<?>> getBeansForRequiredType(Type beanType,
                                                           io.micronaut.context.Qualifier<?> qualifier,
                                                           boolean filterBeanTypes) {
        if (!filterBeanTypes) {
            return (Collection) getBeans((Argument) Argument.of(beanType), (io.micronaut.context.Qualifier) qualifier);
        }
        io.micronaut.context.Qualifier<Object> objectQualifier = (io.micronaut.context.Qualifier<Object>) qualifier;
        if (objectQualifier == null) {
            objectQualifier = (io.micronaut.context.Qualifier<Object>) DefaultQualifier.instance();
        }
        return objectQualifier.filterQualified(Object.class, applicationContext.getAllBeanDefinitions()).stream()
                .map(bd -> new OdiBeanImpl<>(applicationContext, bd))
                .collect(Collectors.toList());
    }

    @Override
    public Set<Bean<?>> getBeans(String name) {
        String beanName = Objects.requireNonNull(name, "Name cannot be null");
        return getBeans(Argument.OBJECT_ARGUMENT, Qualifiers.byName(beanName)).stream()
                .filter(bean -> beanName.equals(bean.getName()))
                .map(bean -> (Bean<?>) bean)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public <X> Bean<? extends X> resolve(Set<Bean<? extends X>> beans) {
        if (beans == null || beans.isEmpty()) {
            return null;
        }
        if (beans.size() == 1) {
            return beans.iterator().next();
        }
        List<Bean<? extends X>> alternatives = beans.stream()
                .filter(Bean::isAlternative)
                .filter(bean -> getPriority(bean) > 0)
                .collect(Collectors.toList());
        if (!alternatives.isEmpty()) {
            return alternatives.stream()
                    .sorted(Comparator.<Bean<? extends X>>comparingInt(OdiBeanContainerImpl::getPriority).reversed())
                    .findFirst()
                    .orElse(null);
        }
        List<Bean<? extends X>> nonReserve = beans.stream()
                .filter(bean -> !isReserve(bean))
                .collect(Collectors.toList());
        if (!nonReserve.isEmpty() && nonReserve.size() < beans.size()) {
            if (nonReserve.size() == 1) {
                return nonReserve.iterator().next();
            }
            throw new AmbiguousResolutionException("Multiple beans are eligible for injection: " + nonReserve);
        }
        List<Bean<? extends X>> reserves = beans.stream()
                .filter(OdiBeanContainerImpl::isReserve)
                .collect(Collectors.toList());
        if (reserves.size() == beans.size()) {
            List<Bean<? extends X>> highestPriorityReserves = highestPriorityBeans(reserves);
            if (highestPriorityReserves.size() == 1) {
                return highestPriorityReserves.iterator().next();
            }
            if (!highestPriorityReserves.isEmpty()) {
                throw new AmbiguousResolutionException("Multiple beans are eligible for injection: " + highestPriorityReserves);
            }
        }
        throw new AmbiguousResolutionException("Multiple beans are eligible for injection: " + beans);
    }

    private static <X> List<Bean<? extends X>> highestPriorityBeans(Collection<Bean<? extends X>> beans) {
        int highestPriority = beans.stream()
                .mapToInt(OdiBeanContainerImpl::getPriority)
                .max()
                .orElse(0);
        if (highestPriority <= 0) {
            return List.of();
        }
        return beans.stream()
                .filter(bean -> getPriority(bean) == highestPriority)
                .collect(Collectors.toList());
    }

    private static boolean isReserve(Bean<?> bean) {
        return bean instanceof OdiBean<?> odiBean && isReserve(odiBean.getBeanDefinition());
    }

    private static int getPriority(Bean<?> bean) {
        if (bean instanceof Prioritized) {
            return ((Prioritized) bean).getPriority();
        }
        return 0;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public <T> Set<ObserverMethod<? super T>> resolveObserverMethods(T event, Annotation... qualifiers) {
        if (observerMethodRegistry == null) {
            observerMethodRegistry = applicationContext.getBean(OdiObserverMethodRegistry.class);
        }
        if (event.getClass().getTypeParameters().length > 0) {
            throw new IllegalArgumentException("Type variable in event type");
        }
        Argument<?> argument = Argument.of(event.getClass());
        final io.micronaut.context.Qualifier qualifierInstances =
                odiAnnotations.resolveQualifier(qualifiers);
        return observerMethodRegistry
                .findSetOfObserverMethods(argument, qualifierInstances);
    }

    @Override
    public List resolveInterceptors(InterceptionType type, Annotation... interceptorBindings) {
        validateInterceptorBindings(interceptorBindings);
        return applicationContext.streamOfType(Interceptor.class)
                .filter(interceptor -> getPriority(interceptor) > 0)
                .filter(interceptor -> interceptor.intercepts(type))
                .filter(interceptor -> interceptorBindingsMatch(interceptor, interceptorBindings))
                .sorted((left, right) -> {
                    int result = Integer.compare(getPriority(left), getPriority(right));
                    if (result != 0) {
                        return result;
                    }
                    return left.getBeanClass().getName().compareTo(right.getBeanClass().getName());
                })
                .collect(Collectors.toList());
    }

    private void validateInterceptorBindings(Annotation... interceptorBindings) {
        if (interceptorBindings == null || interceptorBindings.length == 0) {
            throw new IllegalArgumentException("At least one interceptor binding is required");
        }
        Set<Class<? extends Annotation>> bindingTypes = new LinkedHashSet<>(interceptorBindings.length);
        for (Annotation interceptorBinding : interceptorBindings) {
            if (interceptorBinding == null) {
                throw new IllegalArgumentException("Interceptor binding cannot be null");
            }
            Class<? extends Annotation> bindingType = AnnotationUtils.findAnnotationClass(interceptorBinding);
            if (!bindingTypes.add(bindingType)) {
                throw new IllegalArgumentException("Interceptor binding cannot be duplicated for type: " + bindingType.getName());
            }
            if (!odiAnnotations.isInterceptorBinding(bindingType)) {
                throw new IllegalArgumentException("Not a valid interceptor binding annotation type: " + bindingType.getName());
            }
        }
    }

    private static boolean interceptorBindingsMatch(Interceptor<?> interceptor, Annotation... requiredBindings) {
        Set<Annotation> interceptorBindings = interceptor.getInterceptorBindings();
        if (interceptorBindings.isEmpty()) {
            return false;
        }
        for (Annotation interceptorBinding : interceptorBindings) {
            if (!containsInterceptorBinding(requiredBindings, interceptorBinding)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsInterceptorBinding(Annotation[] requiredBindings, Annotation interceptorBinding) {
        Class<? extends Annotation> interceptorBindingType = AnnotationUtils.findAnnotationClass(interceptorBinding);
        for (Annotation requiredBinding : requiredBindings) {
            if (AnnotationUtils.findAnnotationClass(requiredBinding).equals(interceptorBindingType)
                    && interceptorBindingValuesMatch(requiredBinding, interceptorBinding)) {
                return true;
            }
        }
        return false;
    }

    private static boolean interceptorBindingValuesMatch(Annotation requiredBinding, Annotation interceptorBinding) {
        if (requiredBinding.equals(interceptorBinding) || interceptorBinding.equals(requiredBinding)) {
            return true;
        }
        AnnotationValue<?> requiredBindingValues = bindingValues(requiredBinding);
        AnnotationValue<?> interceptorBindingValues = bindingValues(interceptorBinding);
        return requiredBindingValues.equals(interceptorBindingValues);
    }

    private static AnnotationValue<?> bindingValues(Annotation annotation) {
        AnnotationValue<?> annotationValue = AnnotationReflection.toAnnotationValue(annotation);
        String[] nonBindingMembers = annotationValue.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE);
        Map<CharSequence, Object> values = new LinkedHashMap<>();
        Map<CharSequence, Object> defaultValues = annotationValue.getDefaultValues();
        if (defaultValues != null) {
            values.putAll(defaultValues);
        }
        values.putAll(annotationValue.getValues());
        values.remove(AnnotationUtil.NON_BINDING_ATTRIBUTE);
        for (String nonBindingMember : nonBindingMembers) {
            values.remove(nonBindingMember);
        }
        return AnnotationValue.builder(annotationValue.getAnnotationName())
                .members(values)
                .build();
    }

    @Override
    public boolean isScope(Class<? extends Annotation> annotationType) {
        return odiAnnotations.isScope(annotationType);
    }

    @Override
    public boolean isNormalScope(Class<? extends Annotation> annotationType) {
        return odiAnnotations.isNormalScope(annotationType);
    }

    @Override
    public boolean isQualifier(Class<? extends Annotation> annotationType) {
        return odiAnnotations.isQualifier(annotationType);
    }

    @Override
    public boolean isStereotype(Class<? extends Annotation> annotationType) {
        return odiAnnotations.isStereotype(annotationType);
    }

    @Override
    public boolean isInterceptorBinding(Class<? extends Annotation> annotationType) {
        return odiAnnotations.isInterceptorBinding(annotationType);
    }

    @Override
    public Context getContext(Class<? extends Annotation> scopeType) {
        Collection<Context> contexts = getContexts(scopeType);
        List<Context> activeContexts = contexts.stream()
                .filter(Context::isActive)
                .collect(Collectors.toList());
        if (activeContexts.isEmpty()) {
            throw new ContextNotActiveException("No context active for scope: " + scopeType.getSimpleName());
        } else if (activeContexts.size() > 1) {
            throw new IllegalArgumentException("More than one active context for scope: " + scopeType.getSimpleName());
        } else {
            return activeContexts.iterator().next();
        }
    }

    @Override
    public Collection<Context> getContexts(Class<? extends Annotation> scopeType) {
        if (scopeType == Dependent.class || scopeType == null) {
            return Collections.singletonList(new DependentContext(null));
        }
        if (scopeType == Singleton.class) {
            return Collections.singletonList(SingletonContext.INSTANCE);
        }
        final List<Context> contexts = applicationContext.streamOfType(Context.class)
                .filter(c -> c.getScope() == scopeType)
                .collect(Collectors.toList());
        return Collections.unmodifiableList(contexts);
    }

    @Override
    public Event<Object> getEvent() {
        if (objectEvent == null) {
            objectEvent = applicationContext.getBean(Event.class);
        }
        return objectEvent;
    }

    @Override
    public <T> Event<T> getEvent(Argument<T> eventType,
                                 AnnotationMetadata annotationMetadata,
                                 Qualifier<T> qualifier) {
        return applicationContext.getBean(OdiEventFactory.class)
                .buildEvent(annotationMetadata, eventType, qualifier, null);
    }

    @Override
    public OdiInstance<Object> createInstance() {
        return new OdiInstanceImpl<>(this, null, Argument.OBJECT_ARGUMENT);
    }

    @Override
    public OdiInstance<Object> createInstance(Context context) {
        return createInstance(context, true);
    }

    private OdiInstance<Object> createInstance(Context context, boolean allowDynamicInjectionPoint) {
        return new OdiInstanceImpl<>(
                this,
                context,
                Argument.OBJECT_ARGUMENT,
                null,
                (Qualifier<Object>) null,
                allowDynamicInjectionPoint
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrapClientProxy(T instance) {
        if (!(instance instanceof InterceptedProxy<?> interceptedProxy)) {
            return instance;
        }
        Optional<BeanDefinition<T>> proxyBeanDefinition = applicationContext.findBeanRegistration(instance)
                .map(BeanRegistration::getBeanDefinition)
                .filter(BeanDefinition::isProxy);
        if (proxyBeanDefinition.isEmpty()) {
            proxyBeanDefinition = applicationContext.findBeanDefinition((Class<T>) instance.getClass(), null)
                    .filter(BeanDefinition::isProxy);
        }
        if (proxyBeanDefinition.isEmpty()) {
            return (T) interceptedProxy.interceptedTarget();
        }
        BeanDefinition<T> proxyDefinition = proxyBeanDefinition.get();
        OdiBean<T> proxyBean = getBean(proxyDefinition);
        OdiBean<T> targetBean = proxyBean.getProxyTargetBean();
        Context context = getSingleActiveContextForUnwrap(targetBean.getScope());
        T target = context.get(targetBean);
        if (target == null) {
            target = context.get(targetBean, createCreationalContext(targetBean));
        }
        return target;
    }

    private Context getSingleActiveContextForUnwrap(Class<? extends Annotation> scopeType) {
        List<Context> activeContexts = getContexts(scopeType).stream()
                .filter(Context::isActive)
                .collect(Collectors.toList());
        if (activeContexts.isEmpty()) {
            throw new ContextNotActiveException("No context active for scope: " + scopeType.getSimpleName());
        }
        if (activeContexts.size() > 1) {
            throw new IllegalStateException("More than one active context for scope: " + scopeType.getSimpleName());
        }
        return activeContexts.get(0);
    }

    @Override
    public BeanContext getBeanContext() {
        return applicationContext;
    }

    @Override
    public boolean isMatchingBean(Set<Type> beanTypes,
                                  Set<Annotation> beanQualifiers,
                                  Type requiredType,
                                  Set<Annotation> requiredQualifiers) {
        requireNonNull(beanTypes, "Null bean type");
        requireNonNull(beanQualifiers, "Null bean qualifiers");
        requireNonNull(requiredType, "Null required type");
        requireNonNull(requiredQualifiers, "Null required qualifiers");
        validateQualifiers("beanQualifiers annotation not a qualifier", beanQualifiers);
        validateQualifiers("requiredQualifiers annotation not a qualifier", requiredQualifiers);
        return matchesBeanType(requiredType, beanTypes) && matchesBeanQualifiers(beanQualifiers, requiredQualifiers);
    }

    @Override
    public boolean isMatchingEvent(Type eventType,
                                   Set<Annotation> eventQualifiers,
                                   Type observedEventType,
                                   Set<Annotation> observedEventQualifiers) {
        requireNonNull(eventType, "Null event type");
        requireNonNull(eventQualifiers, "Null event qualifiers");
        requireNonNull(observedEventType, "Null required type");
        requireNonNull(observedEventQualifiers, "Null required qualifiers");
        if (containsTypeVariable(eventType)) {
            throw new IllegalArgumentException("Type variable in event type");
        }
        validateQualifiers("A specifiedQualifiers annotation not a qualifier", eventQualifiers);
        validateQualifiers("An observedEventQualfiers annotation not a qualifier", observedEventQualifiers);
        return isEventAssignable(observedEventType, eventType) && matchesEventQualifiers(eventQualifiers, observedEventQualifiers);
    }

    private static boolean matchesBeanType(Type requiredType, Set<Type> beanTypes) {
        if (requiredType == Object.class) {
            return true;
        }
        for (Type beanType : beanTypes) {
            if (isLegalBeanType(beanType) && isBeanTypeAssignable(requiredType, beanType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldFilterBeanTypes(Type requiredType) {
        Class<?> requiredRawType = rawType(requiredType);
        if (requiredRawType == Event.class || requiredRawType == Instance.class) {
            return false;
        }
        if (requiredType instanceof Class<?>) {
            return ((Class<?>) requiredType).getTypeParameters().length > 0
                    || isPrimitiveOrWrapper((Class<?>) requiredType);
        }
        return requiredType instanceof ParameterizedType;
    }

    private static boolean isBeanTypeAssignable(Type requiredType, Type beanType) {
        if (isSameType(requiredType, beanType)) {
            return true;
        }
        if (requiredType instanceof Class<?>) {
            Class<?> requiredClass = (Class<?>) requiredType;
            if (beanType instanceof Class<?>) {
                return requiredClass.equals(beanType);
            }
            if (beanType instanceof ParameterizedType) {
                ParameterizedType beanParameterized = (ParameterizedType) beanType;
                if (!requiredClass.equals(rawType(beanParameterized))) {
                    return false;
                }
                for (Type beanArgument : beanParameterized.getActualTypeArguments()) {
                    if (!isParameterizedBeanTypeAssignableToRaw(beanArgument)) {
                        return false;
                    }
                }
                return true;
            }
        }
        if (requiredType instanceof ParameterizedType) {
            ParameterizedType requiredParameterized = (ParameterizedType) requiredType;
            Class<?> requiredRawType = rawType(requiredParameterized);
            if (requiredRawType == null) {
                return false;
            }
            if (beanType instanceof Class<?>) {
                if (!requiredRawType.equals(beanType)) {
                    return false;
                }
                for (Type requiredArgument : requiredParameterized.getActualTypeArguments()) {
                    if (!isRawBeanTypeAssignableTo(requiredArgument)) {
                        return false;
                    }
                }
                return true;
            }
            if (beanType instanceof ParameterizedType) {
                ParameterizedType beanParameterized = (ParameterizedType) beanType;
                if (!requiredRawType.equals(rawType(beanParameterized))) {
                    return false;
                }
                Type[] requiredArguments = requiredParameterized.getActualTypeArguments();
                Type[] beanArguments = beanParameterized.getActualTypeArguments();
                if (requiredArguments.length != beanArguments.length) {
                    return false;
                }
                for (int i = 0; i < requiredArguments.length; i++) {
                    if (!isBeanTypeArgumentAssignable(requiredArguments[i], beanArguments[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static boolean isParameterizedBeanTypeAssignableToRaw(Type beanArgument) {
        if (beanArgument == Object.class) {
            return true;
        }
        return isTypeVariable(beanArgument) && hasOnlyObjectUpperBound(beanArgument);
    }

    private static boolean isRawBeanTypeAssignableTo(Type requiredArgument) {
        if (requiredArgument == Object.class) {
            return true;
        }
        if (isTypeVariable(requiredArgument)) {
            return hasOnlyObjectUpperBound(requiredArgument);
        }
        if (requiredArgument instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) requiredArgument;
            return wildcard.getLowerBounds().length == 0 && hasOnlyObjectUpperBounds(wildcard.getUpperBounds());
        }
        return false;
    }

    private static boolean isBeanTypeArgumentAssignable(Type requiredArgument, Type beanArgument) {
        if (isSameType(requiredArgument, beanArgument)) {
            return true;
        }
        if (requiredArgument instanceof WildcardType) {
            return isBeanArgumentAssignableToWildcard((WildcardType) requiredArgument, beanArgument);
        }
        if (isTypeVariable(beanArgument)) {
            return satisfiesBounds(requiredArgument, typeVariableBounds(beanArgument));
        }
        if (isTypeVariable(requiredArgument)) {
            return satisfiesBounds(beanArgument, typeVariableBounds(requiredArgument));
        }
        if (requiredArgument instanceof ParameterizedType && beanArgument instanceof ParameterizedType) {
            return isBeanTypeAssignable(requiredArgument, beanArgument);
        }
        return false;
    }

    private static boolean isBeanArgumentAssignableToWildcard(WildcardType wildcard, Type beanArgument) {
        for (Type upperBound : wildcard.getUpperBounds()) {
            if (upperBound == Object.class) {
                continue;
            }
            if (isTypeVariable(beanArgument)) {
                if (!typeVariableBoundsOverlap(upperBound, beanArgument)) {
                    return false;
                }
            } else if (!isTypeAssignable(beanArgument, upperBound)) {
                return false;
            }
        }
        for (Type lowerBound : wildcard.getLowerBounds()) {
            if (isTypeVariable(beanArgument)) {
                if (!satisfiesBounds(lowerBound, typeVariableBounds(beanArgument))) {
                    return false;
                }
            } else if (!isTypeAssignable(lowerBound, beanArgument)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTypeAssignable(Type candidate, Type requiredBound) {
        if (isSameType(candidate, requiredBound) || requiredBound == Object.class) {
            return true;
        }
        if (isTypeVariable(candidate)) {
            for (Type candidateBound : typeVariableBounds(candidate)) {
                if (isTypeAssignable(candidateBound, requiredBound)) {
                    return true;
                }
            }
            return false;
        }
        if (isTypeVariable(requiredBound)) {
            return satisfiesBounds(candidate, typeVariableBounds(requiredBound));
        }
        if (candidate instanceof ParameterizedType && requiredBound instanceof ParameterizedType) {
            return isBeanTypeAssignable(requiredBound, candidate);
        }
        Class<?> candidateClass = rawType(candidate);
        Class<?> requiredClass = rawType(requiredBound);
        return candidateClass != null && requiredClass != null && requiredClass.isAssignableFrom(candidateClass);
    }

    private static boolean satisfiesBounds(Type type, Type[] bounds) {
        for (Type bound : bounds) {
            if (!isTypeAssignable(type, bound)) {
                return false;
            }
        }
        return true;
    }

    private static boolean typeVariableBoundsOverlap(Type requiredBound, Type variable) {
        Type[] variableBounds = typeVariableBounds(variable);
        for (Type variableBound : variableBounds) {
            if (isTypeAssignable(variableBound, requiredBound)) {
                return true;
            }
        }
        return satisfiesBounds(requiredBound, variableBounds);
    }

    private static boolean hasOnlyObjectUpperBound(Type variable) {
        return hasOnlyObjectUpperBounds(typeVariableBounds(variable));
    }

    private static boolean hasOnlyObjectUpperBounds(Type[] bounds) {
        return bounds.length == 0 || (bounds.length == 1 && bounds[0] == Object.class);
    }

    private static boolean isTypeVariable(Type type) {
        return type instanceof TypeVariable<?>
                || (type instanceof Argument<?> && ((Argument<?>) type).isTypeVariable());
    }

    private static Type[] typeVariableBounds(Type type) {
        if (type instanceof TypeVariable<?>) {
            return ((TypeVariable<?>) type).getBounds();
        }
        if (type instanceof Argument<?> && ((Argument<?>) type).isTypeVariable()) {
            return new Type[]{((Argument<?>) type).getType()};
        }
        return new Type[0];
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean isEventAssignable(Type observedType, Type eventType) {
        if (isSameType(observedType, eventType)) {
            return true;
        }
        if (observedType instanceof Class<?>) {
            Class<?> eventClass = rawType(eventType);
            return eventClass != null && ((Class<?>) observedType).isAssignableFrom(eventClass);
        }
        if (observedType instanceof ParameterizedType && eventType instanceof ParameterizedType) {
            ParameterizedType observedParameterized = (ParameterizedType) observedType;
            ParameterizedType eventParameterized = (ParameterizedType) eventType;
            Class<?> observedRaw = rawType(observedParameterized);
            Class<?> eventRaw = rawType(eventParameterized);
            if (observedRaw == null || eventRaw == null || !observedRaw.isAssignableFrom(eventRaw)) {
                return false;
            }
            Type[] observedArguments = observedParameterized.getActualTypeArguments();
            Type[] eventArguments = eventParameterized.getActualTypeArguments();
            if (observedArguments.length != eventArguments.length) {
                return false;
            }
            for (int i = 0; i < observedArguments.length; i++) {
                if (!matchesTypeArgument(observedArguments[i], eventArguments[i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean matchesTypeArgument(Type observedArgument, Type eventArgument) {
        if (isSameType(observedArgument, eventArgument)) {
            return true;
        }
        if (observedArgument instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) observedArgument;
            for (Type upperBound : wildcard.getUpperBounds()) {
                if (!isBoundAssignable(upperBound, eventArgument)) {
                    return false;
                }
            }
            for (Type lowerBound : wildcard.getLowerBounds()) {
                if (!isBoundAssignable(eventArgument, lowerBound)) {
                    return false;
                }
            }
            return true;
        }
        if (observedArgument instanceof ParameterizedType && eventArgument instanceof ParameterizedType) {
            return isEventAssignable(observedArgument, eventArgument);
        }
        return false;
    }

    private static boolean isBoundAssignable(Type requiredBound, Type candidate) {
        Class<?> requiredClass = rawType(requiredBound);
        Class<?> candidateClass = rawType(candidate);
        return requiredClass != null && candidateClass != null && requiredClass.isAssignableFrom(candidateClass);
    }

    private static boolean isSameType(Type left, Type right) {
        if (Objects.equals(left, right)) {
            return true;
        }
        if (left instanceof Class<?> && right instanceof Class<?>) {
            return arePrimitiveWrapperEquivalent((Class<?>) left, (Class<?>) right);
        }
        if (left instanceof ParameterizedType && right instanceof ParameterizedType) {
            ParameterizedType leftParameterized = (ParameterizedType) left;
            ParameterizedType rightParameterized = (ParameterizedType) right;
            if (!Objects.equals(leftParameterized.getRawType(), rightParameterized.getRawType())) {
                return false;
            }
            Type[] leftArguments = leftParameterized.getActualTypeArguments();
            Type[] rightArguments = rightParameterized.getActualTypeArguments();
            if (leftArguments.length != rightArguments.length) {
                return false;
            }
            for (int i = 0; i < leftArguments.length; i++) {
                if (!isSameType(leftArguments[i], rightArguments[i])) {
                    return false;
                }
            }
            return true;
        }
        if (left instanceof GenericArrayType && right instanceof GenericArrayType) {
            return isSameType(
                    ((GenericArrayType) left).getGenericComponentType(),
                    ((GenericArrayType) right).getGenericComponentType()
            );
        }
        return false;
    }

    private static boolean isLegalBeanType(Type type) {
        return type != null && !containsWildcard(type);
    }

    private static boolean containsWildcard(Type type) {
        if (type instanceof WildcardType) {
            return true;
        }
        if (type instanceof ParameterizedType) {
            for (Type argument : ((ParameterizedType) type).getActualTypeArguments()) {
                if (containsWildcard(argument)) {
                    return true;
                }
            }
        }
        if (type instanceof GenericArrayType) {
            return containsWildcard(((GenericArrayType) type).getGenericComponentType());
        }
        return false;
    }

    private static boolean containsTypeVariable(Type type) {
        if (isTypeVariable(type)) {
            return true;
        }
        if (type instanceof ParameterizedType) {
            for (Type argument : ((ParameterizedType) type).getActualTypeArguments()) {
                if (containsTypeVariable(argument)) {
                    return true;
                }
            }
        }
        if (type instanceof GenericArrayType) {
            return containsTypeVariable(((GenericArrayType) type).getGenericComponentType());
        }
        if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            for (Type bound : wildcard.getUpperBounds()) {
                if (containsTypeVariable(bound)) {
                    return true;
                }
            }
            for (Type bound : wildcard.getLowerBounds()) {
                if (containsTypeVariable(bound)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Class<?> rawType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class<?>) {
                return (Class<?>) rawType;
            }
        }
        if (type instanceof Argument<?>) {
            return ((Argument<?>) type).getType();
        }
        return null;
    }

    private static boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() || ReflectionUtils.getPrimitiveType(type) != type;
    }

    private static boolean arePrimitiveWrapperEquivalent(Class<?> left, Class<?> right) {
        if (!isPrimitiveOrWrapper(left) && !isPrimitiveOrWrapper(right)) {
            return false;
        }
        return ReflectionUtils.getPrimitiveType(left).equals(ReflectionUtils.getPrimitiveType(right));
    }

    private static boolean isReferenceInstance(Class<?> beanType, Object instance) {
        if (beanType.isPrimitive()) {
            return ReflectionUtils.getWrapperType(beanType).isInstance(instance);
        }
        return beanType.isInstance(instance);
    }

    private static void validateQualifiers(String message, Set<Annotation> qualifiers) {
        for (Annotation qualifier : qualifiers) {
            if (qualifier == null || !qualifier.annotationType().isAnnotationPresent(jakarta.inject.Qualifier.class)) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    private static boolean matchesBeanQualifiers(Set<Annotation> beanQualifiers, Set<Annotation> requiredQualifiers) {
        if (requiredQualifiers.isEmpty()) {
            return matchesBeanWithNoRequiredQualifiers(beanQualifiers);
        }
        Set<Annotation> candidates = normalizeCandidateQualifiers(beanQualifiers);
        return candidates.containsAll(requiredQualifiers);
    }

    private static boolean matchesEventQualifiers(Set<Annotation> eventQualifiers, Set<Annotation> observedEventQualifiers) {
        Set<Annotation> candidates = normalizeCandidateQualifiers(eventQualifiers);
        return candidates.containsAll(observedEventQualifiers);
    }

    private static boolean matchesBeanWithNoRequiredQualifiers(Set<Annotation> beanQualifiers) {
        if (beanQualifiers.isEmpty()) {
            return true;
        }
        return beanQualifiers.stream().anyMatch(annotation ->
                annotation.annotationType() == Default.class ||
                        annotation.annotationType() == Any.class ||
                        annotation.annotationType() == Named.class
        );
    }

    private static Set<Annotation> normalizeCandidateQualifiers(Set<Annotation> qualifiers) {
        Set<Annotation> normalized = new LinkedHashSet<>(qualifiers);
        normalized.add(Any.Literal.INSTANCE);
        if (qualifiers.isEmpty() || qualifiers.stream().anyMatch(OdiBeanContainerImpl::isDefaultedQualifier)) {
            normalized.add(Default.Literal.INSTANCE);
        }
        return normalized;
    }

    private static boolean isDefaultedQualifier(Annotation annotation) {
        return annotation.annotationType() == Default.class || annotation.annotationType() == Named.class;
    }
}

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
package org.eclipse.odi.cdi.intercept;

import io.micronaut.aop.InterceptorKind;
import io.micronaut.context.BeanContext;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import jakarta.inject.Singleton;
import org.eclipse.odi.cdi.AnnotationUtils;
import org.eclipse.odi.cdi.annotation.OdiInterceptorAdapter;
import org.eclipse.odi.cdi.annotation.reflect.AnnotationReflection;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates CDI interceptor instances when the intercepted target instance is created.
 */
@Singleton
final class InterceptorInstanceAssociation implements BeanCreatedEventListener<Object>, BeanDestroyedEventListener<Object> {

    @Override
    public Object onCreated(BeanCreatedEvent<Object> event) {
        Object bean = event.getBean();
        BeanDefinition<?> beanDefinition = event.getBeanDefinition();
        if (isInterceptorSupportBean(bean, beanDefinition)) {
            return bean;
        }
        if (!shouldSkipSelfAssociation(event, beanDefinition)) {
            associateBean(event.getSource(), bean, beanDefinition);
        }
        if (!(beanDefinition instanceof ProxyBeanDefinition<?>)) {
            for (var dependentBean : event.getDependentBeans()) {
                if (!isInterceptorSupportBean(dependentBean.getBean(), dependentBean.getBeanDefinition())) {
                    associateBean(event.getSource(), dependentBean.getBean(), dependentBean.getBeanDefinition());
                }
            }
        }
        return bean;
    }

    @Override
    public void onDestroyed(BeanDestroyedEvent<Object> event) {
        Object bean = event.getBean();
        if (isInterceptorSupportBean(bean, event.getBeanDefinition())) {
            return;
        }
        JakartaInterceptorAdapter.destroyInterceptorBeansForTarget(event.getSource(), bean);
    }

    private boolean isInterceptorSupportBean(Object bean, BeanDefinition<?> beanDefinition) {
        return bean instanceof JakartaInterceptorAdapter<?>
                || beanDefinition.hasAnnotation(jakarta.interceptor.Interceptor.class)
                || beanDefinition.hasStereotype(jakarta.interceptor.Interceptor.class);
    }

    private void associateBean(BeanContext beanContext, Object bean, BeanDefinition<?> beanDefinition) {
        AnnotationMetadata typeMetadata = beanDefinition.getAnnotationMetadata();
        associateInterceptors(beanContext, bean, InterceptorKind.POST_CONSTRUCT, typeMetadata);
        associateInterceptors(beanContext, bean, InterceptorKind.PRE_DESTROY, typeMetadata);
        associateInterceptors(
                beanContext,
                bean,
                InterceptorKind.AROUND_CONSTRUCT,
                new AnnotationMetadataHierarchy(typeMetadata, beanDefinition.getConstructor().getAnnotationMetadata())
        );
        for (ExecutableMethod<?, ?> method : beanDefinition.getExecutableMethods()) {
            associateInterceptors(
                    beanContext,
                    bean,
                    InterceptorKind.AROUND,
                    new AnnotationMetadataHierarchy(typeMetadata, method.getAnnotationMetadata())
            );
        }
    }

    private boolean shouldSkipSelfAssociation(BeanCreatedEvent<Object> event, BeanDefinition<?> beanDefinition) {
        if (beanDefinition instanceof ProxyBeanDefinition<?>) {
            return true;
        }
        BeanDefinition<?> rootBeanDefinition = event.getRootBeanDefinition();
        return rootBeanDefinition != null && !isRootTarget(rootBeanDefinition, beanDefinition);
    }

    private boolean isRootTarget(BeanDefinition<?> rootBeanDefinition, BeanDefinition<?> beanDefinition) {
        if (rootBeanDefinition.equals(beanDefinition)) {
            return true;
        }
        if (rootBeanDefinition instanceof ProxyBeanDefinition<?> proxyBeanDefinition) {
            return proxyBeanDefinition.getTargetDefinitionType().equals(beanDefinition.getClass());
        }
        return false;
    }

    private void associateInterceptors(BeanContext beanContext,
                                       Object target,
                                       InterceptorKind kind,
                                       AnnotationMetadata annotationMetadata) {
        Set<Annotation> requiredBindings = AnnotationUtils.synthesizeInterceptorBindingAnnotations(annotationMetadata);
        if (requiredBindings.isEmpty()) {
            return;
        }
        for (BeanDefinition<jakarta.enterprise.inject.spi.Interceptor> interceptorDefinition : interceptorDefinitions(beanContext)) {
            if (isSelfInterceptorDefinition(interceptorDefinition)) {
                continue;
            }
            JakartaInterceptorAdapter<?> adapter = resolveInterceptorAdapter(beanContext, interceptorDefinition);
            if (adapter.intercepts(kind) && interceptorBindingsMatch(adapter, requiredBindings)) {
                adapter.ensureInterceptorBean(target);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<BeanDefinition<jakarta.enterprise.inject.spi.Interceptor>> interceptorDefinitions(BeanContext beanContext) {
        return (List) beanContext.getBeanDefinitions(jakarta.enterprise.inject.spi.Interceptor.class)
                .stream()
                .filter(definition -> JakartaInterceptorAdapter.class.isAssignableFrom(definition.getBeanType()))
                .toList();
    }

    private boolean isSelfInterceptorDefinition(BeanDefinition<?> interceptorDefinition) {
        return interceptorDefinition.booleanValue(OdiInterceptorAdapter.class, "self").orElse(true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private JakartaInterceptorAdapter<?> resolveInterceptorAdapter(BeanContext beanContext,
                                                                  BeanDefinition<jakarta.enterprise.inject.spi.Interceptor> interceptorDefinition) {
        return (JakartaInterceptorAdapter<?>) beanContext.getBeanRegistration((BeanDefinition) interceptorDefinition).getBean();
    }

    private static boolean interceptorBindingsMatch(jakarta.enterprise.inject.spi.Interceptor<?> interceptor,
                                                    Set<Annotation> requiredBindings) {
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

    private static boolean containsInterceptorBinding(Set<Annotation> requiredBindings, Annotation interceptorBinding) {
        Class<? extends Annotation> interceptorBindingType = interceptorBinding.annotationType();
        for (Annotation requiredBinding : requiredBindings) {
            if (requiredBinding.annotationType().equals(interceptorBindingType)
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
        return bindingValues(requiredBinding).equals(bindingValues(interceptorBinding));
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
}

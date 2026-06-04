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

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.BeanResolutionCustomizer;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.QualifiedBeanType;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Reserve;
import jakarta.enterprise.inject.TransientReference;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ODI specific {@link ApplicationContextConfigurer}.
 */
@ContextConfigurer
public final class OdiApplicationContextConfigurer implements ApplicationContextConfigurer {
    @Override
    public void configure(ApplicationContextBuilder builder) {
        builder.allowEmptyProviders(true)
                .customScopeRegistry(OdiCustomScopeRegistry::new)
                .beanResolutionCustomizer(new BeanResolutionCustomizer() {
                    @Override
                    public boolean shouldResolveArrayAsBean(Argument<?> injectionPoint) {
                        return true;
                    }

                    @Override
                    public Argument<?> resolveBeanLookupArgument(Argument<?> beanType) {
                        Class<?> type = beanType.getType();
                        if (type.isPrimitive()) {
                            Class<?> wrapperType = ReflectionUtils.getWrapperType(type);
                            return Argument.of(wrapperType, beanType.getName(), beanType.getAnnotationMetadata(), beanType.getTypeParameters());
                        }
                        return beanType;
                    }

                    @Override
                    public Optional<?> resolveNullBean(Argument<?> requestedBeanType, Argument<?> resolvedBeanType, BeanDefinition<?> beanDefinition) {
                        Class<?> requestedType = requestedBeanType.getType();
                        if (requestedType.isPrimitive() && resolvedBeanType.getType() == ReflectionUtils.getWrapperType(requestedType)) {
                            return Optional.of(primitiveDefaultValue(requestedType));
                        }
                        return Optional.empty();
                    }

                    @Override
                    public boolean shouldDestroyDependentBeanAfterResolution(BeanResolutionContext resolutionContext, BeanRegistration<?> beanRegistration) {
                        return resolutionContext.getPath().currentSegment()
                                .map(segment -> segment.getArgument().getAnnotationMetadata().hasAnnotation(TransientReference.class))
                                .orElse(false);
                    }

                    @Override
                    public boolean shouldInitializeBean(BeanResolutionContext resolutionContext, BeanDefinition<?> beanDefinition, Object bean) {
                        return !(beanDefinition.isProxy() && beanDefinition.getAnnotationMetadata().hasStereotype(NormalScope.class));
                    }

                    @Override
                    public boolean shouldPreserveLazyProxyTargetResolutionPath(BeanResolutionContext resolutionContext, BeanDefinition<?> proxyBeanDefinition) {
                        return !proxyBeanDefinition.getAnnotationMetadata().hasStereotype(NormalScope.class);
                    }

                    @Override
                    public boolean isCandidateBean(Argument<?> beanType, QualifiedBeanType<?> candidate) {
                        Type requiredType = OdiTypeUtils.getRequiredType(beanType);
                        if (requiredType != null) {
                            Set<Type> beanTypes = OdiTypeUtils.getBeanTypes(candidate.getAnnotationMetadata(), candidate.getBeanType());
                            if (!beanTypes.isEmpty()) {
                                return OdiTypeUtils.matchesBeanType(requiredType, beanTypes);
                            }
                        }
                        return candidate.isCandidateBean(beanType);
                    }

                    @Override
                    public <T> Optional<BeanDefinition<T>> resolveNonUniqueBean(Argument<T> beanType,
                                                                                Qualifier<T> qualifier,
                                                                                Collection<BeanDefinition<T>> candidates) {
                        return resolveCdiBean(qualifier, candidates);
                    }
                });
    }

    private static <T> Optional<BeanDefinition<T>> resolveCdiBean(Qualifier<T> qualifier,
                                                                  Collection<BeanDefinition<T>> beanDefinitions) {
        if (beanDefinitions.isEmpty() || beanDefinitions.size() == 1) {
            return Optional.empty();
        }
        if (isDefaultQualifier(qualifier)) {
            List<BeanDefinition<T>> defaultBeans = beanDefinitions
                    .stream()
                    .filter(DefaultQualifier::hasDefaultQualifier)
                    .collect(Collectors.toList());
            if (!defaultBeans.isEmpty() && defaultBeans.size() < beanDefinitions.size()) {
                if (defaultBeans.size() == 1) {
                    return Optional.of(defaultBeans.iterator().next());
                }
                beanDefinitions = defaultBeans;
            }
        }
        List<BeanDefinition<T>> alternatives = beanDefinitions
                .stream()
                .filter(bd -> bd.hasStereotype(Alternative.class))
                .filter(bd -> getPriority(bd) > 0)
                .collect(Collectors.toList());
        if (!alternatives.isEmpty()) {
            return highestUniquePriority(alternatives);
        }
        List<BeanDefinition<T>> nonReserve = beanDefinitions
                .stream()
                .filter(bd -> !isReserve(bd))
                .collect(Collectors.toList());
        if (!nonReserve.isEmpty() && nonReserve.size() < beanDefinitions.size()) {
            if (nonReserve.size() == 1) {
                return Optional.of(nonReserve.iterator().next());
            }
            return Optional.empty();
        }
        if (beanDefinitions.stream().allMatch(OdiApplicationContextConfigurer::isReserve)) {
            return highestUniquePriority(beanDefinitions);
        }
        return Optional.empty();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> boolean isDefaultQualifier(Qualifier<T> qualifier) {
        return qualifier == null || DefaultQualifier.instance().contains((Qualifier) qualifier);
    }

    private static <T> Optional<BeanDefinition<T>> highestUniquePriority(Collection<BeanDefinition<T>> beanDefinitions) {
        List<BeanDefinition<T>> sorted = beanDefinitions.stream()
                .filter(beanDefinition -> getPriority(beanDefinition) > 0)
                .sorted(Comparator.<BeanDefinition<T>>comparingInt(OdiApplicationContextConfigurer::getPriority).reversed())
                .collect(Collectors.toList());
        if (sorted.isEmpty()) {
            return Optional.empty();
        }
        if (sorted.size() == 1 || getPriority(sorted.get(0)) != getPriority(sorted.get(1))) {
            return Optional.of(sorted.get(0));
        }
        return Optional.empty();
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

    private static Object primitiveDefaultValue(Class<?> type) {
        return switch (type.getName()) {
            case "boolean" -> false;
            case "byte" -> (byte) 0;
            case "short" -> (short) 0;
            case "int" -> 0;
            case "long" -> 0L;
            case "float" -> 0.0f;
            case "double" -> 0.0d;
            case "char" -> '\0';
            default -> throw new IllegalArgumentException("Not a primitive type: " + type.getName());
        };
    }
}

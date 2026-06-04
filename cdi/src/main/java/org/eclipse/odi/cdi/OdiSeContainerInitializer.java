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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourcesLocator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.QualifiedBeanType;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.spi.Extension;
import org.eclipse.odi.cdi.annotation.OdiBeanDefinition;

/**
 * An implementation of {@link SeContainerInitializer} for ODI.
 */
public class OdiSeContainerInitializer extends SeContainerInitializer implements ApplicationContextBuilder {
    private static final String ADDED_BEAN_CLASSES_PROPERTY = "org.eclipse.odi.cdi.se.added-bean-classes";

    private final ApplicationContextBuilder contextBuilder = new OdiApplicationContextBuilder();
    private final LinkedHashSet<String> addedBeanClassNames = new LinkedHashSet<>();
    private final List<PackageSelection> addedPackages = new ArrayList<>();
    private Predicate<QualifiedBeanType<?>> beansPredicate = beanType -> true;
    private ClassLoader classLoader;
    private boolean discoveryDisabled;

    public OdiSeContainerInitializer() {
        classLoader = Thread.currentThread().getContextClassLoader();
        contextBuilder.classLoader(classLoader);
    }

    @Override
    public SeContainerInitializer addBeanClasses(Class<?>... classes) {
        if (ArrayUtils.isNotEmpty(classes)) {
            ClassLoader classLoader = null;
            for (Class<?> aClass : classes) {
                addedBeanClassNames.add(aClass.getName());
                contextBuilder.packages(aClass.getPackageName());
                if (classLoader == null) {
                    classLoader = aClass.getClassLoader();
                }
            }
            if (classLoader != null) {
                this.classLoader = classLoader;
                contextBuilder.classLoader(classLoader);
            }
            contextBuilder.properties(Collections.singletonMap(ADDED_BEAN_CLASSES_PROPERTY, String.join(",", addedBeanClassNames)));
        }
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(Class<?>... classes) {
        if (ArrayUtils.isNotEmpty(classes)) {
            for (Class<?> aClass : classes) {
                addPackage(aClass.getPackageName(), false);
            }
        }
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(boolean scanRecursively, Class<?>... classes) {
        if (ArrayUtils.isNotEmpty(classes)) {
            for (Class<?> aClass : classes) {
                addPackage(aClass.getPackageName(), scanRecursively);
            }
        }
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(Package... packages) {
        if (ArrayUtils.isNotEmpty(packages)) {
            for (Package aPackage : packages) {
                addPackage(aPackage.getName(), false);
            }
        }
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(boolean scanRecursively, Package... packages) {
        if (ArrayUtils.isNotEmpty(packages)) {
            for (Package aPackage : packages) {
                addPackage(aPackage.getName(), scanRecursively);
            }
        }
        return this;
    }

    @Override
    public SeContainerInitializer addExtensions(Extension... extensions) {
        throw new UnsupportedOperationException("addExtensions is not yet supported");
    }

    @SafeVarargs
    @Override
    public final SeContainerInitializer addExtensions(Class<? extends Extension>... classes) {
        throw new UnsupportedOperationException("addExtensions is not yet supported");
    }

    @SafeVarargs
    @Override
    public final SeContainerInitializer addBuildCompatibleExtensions(Class<? extends BuildCompatibleExtension>... classes) {
        throw new UnsupportedOperationException(
                "Programmatic build-compatible extension registration is not supported by ODI SE bootstrap. "
                        + "ODI runs build-compatible extensions during annotation processing; add extension classes "
                        + "through META-INF/services/jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension "
                        + "on the annotation processor path instead."
        );
    }

    @Override
    public SeContainerInitializer enableInterceptors(Class<?>... classes) {
        throw new UnsupportedOperationException("enableInterceptors is not yet supported");
    }

    @Override
    public SeContainerInitializer enableDecorators(Class<?>... classes) {
        throw new UnsupportedOperationException("enableDecorators is not yet supported");
    }

    @Override
    public SeContainerInitializer selectAlternatives(Class<?>... classes) {
        throw new UnsupportedOperationException("selectAlternatives is not yet supported");
    }

    @SafeVarargs
    @Override
    public final SeContainerInitializer selectAlternativeStereotypes(Class<? extends Annotation>... classes) {
        throw new UnsupportedOperationException("selectAlternativeStereotypes is not yet supported");
    }

    @Override
    public SeContainerInitializer addProperty(String s, Object o) {
        contextBuilder.properties(Collections.singletonMap(s, o));
        return this;
    }

    @Override
    public SeContainerInitializer setProperties(Map<String, Object> map) {
        contextBuilder.properties(map);
        return this;
    }

    @Override
    public SeContainerInitializer disableDiscovery() {
        discoveryDisabled = true;
        return this;
    }

    @Override
    public SeContainerInitializer setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        contextBuilder.classLoader(classLoader);
        return this;
    }

    @Override
    public SeContainer initialize() {
        if (discoveryDisabled) {
            contextBuilder.beansPredicate(beanType -> beansPredicate.test(beanType) && isSelectedBean(beanType));
        }
        final ApplicationContext context = contextBuilder.build();
        context.start();
        OdiSeContainer container = new OdiSeContainer(context);
        container.initializeEagerBeans();
        return container;
    }

    @SafeVarargs
    @Override
    @NonNull
    public final ApplicationContextBuilder eagerInitAnnotated(Class<? extends Annotation>... annotations) {
        contextBuilder.eagerInitAnnotated(annotations);
        return this;
    }

    @Override
    @NonNull
    public ApplicationContextBuilder overrideConfigLocations(String... configLocations) {
        contextBuilder.overrideConfigLocations(configLocations);
        return this;
    }

    @Override
    @NonNull
    public ApplicationContextBuilder singletons(Object... beans) {
        contextBuilder.singletons(beans);
        return this;
    }

    @Override
    @NonNull
    public ApplicationContextBuilder deduceEnvironment(Boolean deduceEnvironment) {
        contextBuilder.deduceEnvironment(deduceEnvironment);
        return this;
    }

    @Override
    public ApplicationContextBuilder deducePackage(boolean deducePackage) {
        return contextBuilder.deducePackage(deducePackage);
    }

    @Override
    public ApplicationContextBuilder deduceCloudEnvironment(boolean deduceEnvironment) {
        return contextBuilder.deduceCloudEnvironment(deduceEnvironment);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder environments(String... environments) {
        contextBuilder.environments(environments);
        return this;
    }

    @Override
    @NonNull
    public ApplicationContextBuilder defaultEnvironments(String... environments) {
        contextBuilder.defaultEnvironments(environments);
        return this;
    }

    @Override
    @NonNull
    public ApplicationContextBuilder packages(String... packages) {
        return contextBuilder.packages(packages);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder properties(Map<String, Object> properties) {
        return contextBuilder.properties(properties);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder propertySources(PropertySource... propertySources) {
        return contextBuilder.propertySources(propertySources);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder environmentPropertySource(boolean environmentPropertySource) {
        return contextBuilder.environmentPropertySource(environmentPropertySource);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder environmentVariableIncludes(String... environmentVariables) {
        return contextBuilder.environmentVariableIncludes(environmentVariables);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder environmentVariableExcludes(String... environmentVariables) {
        return contextBuilder.environmentVariableExcludes(environmentVariables);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder mainClass(Class mainClass) {
        return contextBuilder.mainClass(mainClass);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder classLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        return contextBuilder.classLoader(classLoader);
    }

    @Override
    @NonNull
    public ApplicationContext build() {
        return contextBuilder.build();
    }

    @Override
    @NonNull
    public ApplicationContextBuilder include(String... configurations) {
        return contextBuilder.include(configurations);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder exclude(String... configurations) {
        return contextBuilder.exclude(configurations);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder banner(boolean isEnabled) {
        return contextBuilder.banner(isEnabled);
    }

    @Override
    public ApplicationContextBuilder allowEmptyProviders(boolean shouldAllow) {
        return contextBuilder.allowEmptyProviders(shouldAllow);
    }

    @Override
    public ApplicationContextBuilder configImport(boolean enabled) {
        return contextBuilder.configImport(enabled);
    }

    @Override
    public ApplicationContextBuilder eagerBeansEnabled(boolean enabled) {
        return contextBuilder.eagerBeansEnabled(enabled);
    }

    @Override
    public ApplicationContextBuilder eventsEnabled(boolean enabled) {
        return contextBuilder.eventsEnabled(enabled);
    }

    @Override
    public ApplicationContextBuilder beansPredicate(Predicate<QualifiedBeanType<?>> predicate) {
        this.beansPredicate = predicate;
        return contextBuilder.beansPredicate(predicate);
    }

    @Override
    public ApplicationContextBuilder beanConfigurationsPredicate(Predicate<BeanConfiguration> predicate) {
        return contextBuilder.beanConfigurationsPredicate(predicate);
    }

    @Override
    public ApplicationContextBuilder resourceResolver(ClassPathResourceLoader resourceResolver) {
        return contextBuilder.resourceResolver(resourceResolver);
    }

    @Override
    public ApplicationContextBuilder propertySourcesLocator(PropertySourcesLocator propertySourcesLocator) {
        return contextBuilder.propertySourcesLocator(propertySourcesLocator);
    }

    private void addPackage(String packageName, boolean recursive) {
        contextBuilder.packages(packageName);
        addedPackages.add(new PackageSelection(packageName, recursive));
    }

    private boolean isSelectedBean(QualifiedBeanType<?> beanType) {
        if (isOdiInfrastructureBean(beanType.getBeanType()) || isExternalInfrastructureBean(beanType.getBeanType())) {
            return true;
        }
        if (!beanType.getAnnotationMetadata().hasAnnotation(OdiBeanDefinition.class)) {
            return true;
        }
        if (matchesSelectedClass(beanType.getBeanType())) {
            return true;
        }
        Optional<Class<?>> declaringType = beanType instanceof BeanDefinition<?> beanDefinition
                ? beanDefinition.getDeclaringType()
                : Optional.empty();
        if (declaringType.map(type -> isOdiInfrastructureBean(type) || isExternalInfrastructureBean(type)).orElse(false)) {
            return true;
        }
        return declaringType.map(this::matchesSelectedClass).orElse(false)
                || matchesSelectedPackage(beanType.getBeanType())
                || declaringType.map(this::matchesSelectedPackage).orElse(false);
    }

    private static boolean isOdiInfrastructureBean(Class<?> beanType) {
        return beanType.getName().startsWith("org.eclipse.odi.cdi.");
    }

    private boolean isExternalInfrastructureBean(Class<?> beanType) {
        return classLoader != null && beanType.getClassLoader() != null && beanType.getClassLoader() != classLoader;
    }

    private boolean matchesSelectedClass(Class<?> beanType) {
        return addedBeanClassNames.contains(beanType.getName());
    }

    private boolean matchesSelectedPackage(Class<?> beanType) {
        String packageName = beanType.getPackageName();
        for (PackageSelection addedPackage : addedPackages) {
            if (addedPackage.matches(packageName)) {
                return true;
            }
        }
        return false;
    }

    private static final class PackageSelection {
        private final String packageName;
        private final boolean recursive;

        private PackageSelection(String packageName, boolean recursive) {
            this.packageName = packageName;
            this.recursive = recursive;
        }

        private boolean matches(String candidatePackage) {
            return packageName.equals(candidatePackage)
                    || (recursive && candidatePackage.startsWith(packageName + "."));
        }
    }

}

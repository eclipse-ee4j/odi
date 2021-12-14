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
package com.oracle.odi.cdi.processor.extensions;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.micronaut.context.LifeCycle;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.beans.BeanElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.build.compatible.spi.Validation;

final class BuildTimeExtensionRegistry implements LifeCycle<BuildTimeExtensionRegistry> {
    public static final BuildTimeExtensionRegistry INSTANCE = new BuildTimeExtensionRegistry();
    private List<BuildCompatibleExtensionEntry> buildTimeExtensions;
    private final List<String> loadErrors = new ArrayList<>();

    private BuildTimeExtensionRegistry() {
        buildTimeExtensions = loadExtensions();
    }

    private List<BuildCompatibleExtensionEntry> loadExtensions() {
        final List<BuildCompatibleExtensionEntry> buildTimeExtensions;
        buildTimeExtensions = new ArrayList<>(20);
        try {
            final SoftServiceLoader<BuildCompatibleExtension> loader = SoftServiceLoader.load(BuildCompatibleExtension.class);
            for (ServiceDefinition<BuildCompatibleExtension> compatibleExtension : loader) {
                if (compatibleExtension.isPresent()) {
                    final BuildCompatibleExtension buildCompatibleExtension = compatibleExtension.load();
                    buildTimeExtensions.add(new BuildCompatibleExtensionEntry(buildCompatibleExtension));
                }
            }
        } catch (Exception e) {
            loadErrors.add("Error loading CDI build time extensions: " + e.getMessage());
        }

        OrderUtil.sort(buildTimeExtensions);
        return buildTimeExtensions;
    }

    /**
     * Runs the discovery phase.
     *
     * @param visitorContext The visitor context
     * @return The discovery implementation
     */
    public DiscoveryImpl runDiscovery(VisitorContext visitorContext) {
        final DiscoveryImpl discovery = new DiscoveryImpl(visitorContext);
        if (!loadErrors.isEmpty()) {
            for (String loadError : loadErrors) {
                discovery.getMessages().error(loadError);
            }
        }
        if (!discovery.hasErrors()) {
            for (BuildCompatibleExtensionEntry buildTimeExtension : buildTimeExtensions) {
                final List<Method> discoveryMethods = buildTimeExtension.discoveryMethods;
                final BuildCompatibleExtension extension = buildTimeExtension.extension;

                for (Method discoveryMethod : discoveryMethods) {
                    final Class<?>[] parameterTypes = discoveryMethod.getParameterTypes();
                    Object[] parameters = new Object[parameterTypes.length];
                    if (parameterTypes.length == 0) {
                        ReflectionUtils.invokeMethod(
                                extension,
                                discoveryMethod
                        );
                    } else {
                        for (int i = 0; i < parameterTypes.length; i++) {
                            Class<?> parameterType = parameterTypes[i];
                            if (parameterType == Messages.class) {
                                parameters[i] = discovery.getMessages();
                            } else if (parameterType == MetaAnnotations.class) {

                                parameters[i] = discovery.getMetaAnnotations();
                            } else if (parameterType == ScannedClasses.class) {
                                parameters[i] = discovery.getScannedClasses();
                            } else {
                                discovery.getMessages().error(
                                        "Unsupported parameter of type '"
                                                + parameterType.getName()
                                                + "' defined in method '"
                                                + discoveryMethod.getName()
                                                + "' of extension: " + buildTimeExtension.getClass()
                                                .getName()
                                );
                                break;
                            }
                        }
                        discoveryMethod.setAccessible(true);
                        ReflectionUtils.invokeMethod(
                                extension,
                                discoveryMethod,
                                parameters
                        );
                    }
                }
            }
        }
        return discovery;
    }

    /**
     * Runs all the {@link jakarta.enterprise.inject.build.compatible.spi.Enhancement} instances for the given type.
     *
     * @param originatingElement The originating element
     * @param typeToEnhance      The type to enhance
     * @param visitorContext     The visitor context
     */
    public void runEnhancement(ClassElement originatingElement, ClassElement typeToEnhance, VisitorContext visitorContext) {
        for (BuildCompatibleExtensionEntry buildTimeExtension : buildTimeExtensions) {
            final List<Method> enhanceMethods = buildTimeExtension.enhanceMethods;
            final BuildCompatibleExtension extension = buildTimeExtension.extension;
            outer:
            for (Method enhanceMethod : enhanceMethods) {
                final Enhancement enhancement = enhanceMethod.getAnnotation(Enhancement.class);
                final boolean includeSubtypes = enhancement.withSubtypes();
                final Class<? extends Annotation>[] aw = enhancement.withAnnotations();
                final Class<?>[] types = enhancement.types();
                for (Class<?> type : types) {
                    if (type.getName().equals(typeToEnhance.getName()) || (includeSubtypes && typeToEnhance.isAssignable(type))) {
                        if (ArrayUtils.isEmpty(aw) || (aw.length == 1 && aw[0] == Enhancement.BeanDefiningAnnotations.class) || Arrays.stream(aw)
                                .anyMatch(typeToEnhance::hasAnnotation)) {
                            runEnhancement(originatingElement, typeToEnhance, visitorContext, extension, enhanceMethod);
                            continue outer;
                        }
                    }
                }

            }
        }
    }

    /**
     * Runs the {@link jakarta.enterprise.inject.build.compatible.spi.Registration} phase.
     *
     * @param beanElement    The bean element
     * @param visitorContext The visitor context
     */
    public void runRegistration(BeanElement beanElement, VisitorContext visitorContext) {
        final Set<ClassElement> beanTypes = beanElement.getBeanTypes();
        for (BuildCompatibleExtensionEntry entry : buildTimeExtensions) {
            final BuildCompatibleExtension extension = entry.extension;
            final List<Method> processingMethods = entry.registrationMethods;
            methods:
            for (Method processingMethod : processingMethods) {
                final Class<?>[] types = processingMethod.getAnnotation(Registration.class).types();
                for (Class<?> et : types) {
                    if (et != null && beanTypes.stream().anyMatch(ce -> ce.isAssignable(et))) {
                        runRegistration(extension, processingMethod, beanElement, visitorContext);
                        continue methods;
                    }
                }
            }
        }
    }

    /**
     * Runs the {@link jakarta.enterprise.inject.build.compatible.spi.Synthesis} phase.
     *
     * @param originatingBean The originating bean
     * @param visitorContext  The visitor context
     * @return synthesis component
     */
    public @Nullable
    SyntheticComponentsImpl runSynthesis(BeanElement originatingBean, VisitorContext visitorContext) {
        final SyntheticComponentsImpl syntheticComponents = new SyntheticComponentsImpl(visitorContext);
        for (BuildCompatibleExtensionEntry buildTimeExtension : buildTimeExtensions) {
            final List<Method> synthesisMethods = buildTimeExtension.synthesisMethods;
            final BuildCompatibleExtension extension = buildTimeExtension.extension;
            for (Method synthesisMethod : synthesisMethods) {
                runSynthesis(
                        extension,
                        synthesisMethod,
                        originatingBean,
                        visitorContext,
                        syntheticComponents
                );
            }
        }
        return syntheticComponents;
    }

    private void runSynthesis(BuildCompatibleExtension extension,
                              Method synthesisMethod,
                              BeanElement originatingBean,
                              VisitorContext visitorContext,
                              SyntheticComponentsImpl syntheticComponents) {

        final Class<?>[] parameterTypes = synthesisMethod.getParameterTypes();
        Object[] parameters = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (SyntheticComponents.class == parameterType) {
                parameters[i] = syntheticComponents;
            } else {
                unsupportedParameter(
                        originatingBean.getProducingElement(),
                        visitorContext,
                        extension,
                        synthesisMethod,
                        parameterType
                );
                return;
            }
        }

        try {
            synthesisMethod.setAccessible(true);
            ReflectionUtils.invokeMethod(
                    extension,
                    synthesisMethod,
                    parameters
            );
        } catch (Exception e) {
            visitorContext.fail("Error running build time synthesis in method '" + synthesisMethod.getName() + "' of extension "
                                        + "[" + extension.getClass()
                    .getName() + "]: " + e.getMessage(), originatingBean.getProducingElement());

        }
    }

    private void runRegistration(BuildCompatibleExtension extension,
                               Method processingMethod,
                               BeanElement beanElement,
                               VisitorContext visitorContext) {
        final Class<?>[] parameterTypes = processingMethod.getParameterTypes();
        Object[] parameters = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (BeanInfo.class == parameterType) {
                parameters[i] = new BeanInfoImpl(
                        beanElement,
                        visitorContext
                );
            } else {
                unsupportedParameter(
                        beanElement.getProducingElement(),
                        visitorContext,
                        extension,
                        processingMethod,
                        parameterType
                );
                return;
            }
        }

        try {
            processingMethod.setAccessible(true);
            ReflectionUtils.invokeMethod(
                    extension,
                    processingMethod,
                    parameters
            );
        } catch (Exception e) {
            visitorContext.fail("Error running build time processing in method '" + processingMethod.getName() + "' of "
                                        + "extension [" + extension.getClass()
                    .getName() + "]: " + e.getMessage(), beanElement.getProducingElement());

        }
    }

    private void runEnhancement(ClassElement originatingElement,
                                ClassElement typeToEnhance,
                                VisitorContext visitorContext,
                                BuildCompatibleExtension extension,
                                Method enhanceMethod) {
        final Class<?>[] parameterTypes = enhanceMethod.getParameterTypes();
        Object[] parameters = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType == ClassConfig.class) {
                parameters[i] = new ClassConfigImpl(
                        typeToEnhance,
                        new TypesImpl(visitorContext),
                        visitorContext
                );
            } else if (parameterType == Messages.class) {
                parameters[i] = new MessagesImpl(visitorContext);
            } else {
                unsupportedParameter(originatingElement, visitorContext, extension, enhanceMethod, parameterType);
                return;
            }
        }

        try {
            enhanceMethod.setAccessible(true);
            ReflectionUtils.invokeMethod(
                    extension,
                    enhanceMethod,
                    parameters
            );
        } catch (Exception e) {
            visitorContext.fail("Error running build time enhancement in method '" + enhanceMethod.getName() + "' of extension "
                                        + "[" + extension.getClass()
                    .getName() + "]: " + e.getMessage(), originatingElement);

        }
    }

    private void unsupportedParameter(
            @Nullable
                    Element originatingElement,
            VisitorContext visitorContext,
            BuildCompatibleExtension buildTimeExtension,
            Method enhanceMethod,
            Class<?> parameterType) {
        visitorContext.fail("Unsupported parameter of type '" + parameterType.getName() + "' defined in method '"
                                    + enhanceMethod.getName()
                                    + "' of extension: " + buildTimeExtension.getClass()
                .getName(), originatingElement);
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public BuildTimeExtensionRegistry start() {
        this.buildTimeExtensions = loadExtensions();
        return this;
    }

    @Override
    public BuildTimeExtensionRegistry stop() {
        this.buildTimeExtensions.clear();
        return this;
    }

    static class BuildCompatibleExtensionEntry implements Ordered {
        private final BuildCompatibleExtension extension;
        // phase 1
        private final List<Method> discoveryMethods;
        // phase 2
        private final List<Method> enhanceMethods;
        // phase 3
        private final List<Method> registrationMethods;
        // phase 4
        private final List<Method> synthesisMethods;
        // phase 5
        private final List<Method> validationMethods;

        BuildCompatibleExtensionEntry(BuildCompatibleExtension extension) {
            this.extension = extension;
            final Class<? extends BuildCompatibleExtension> type = extension.getClass();
            final Method[] methods = type.getDeclaredMethods();
            List<Method> discoveryMethods = new ArrayList<>(3);
            List<Method> enhanceMethods = new ArrayList<>(3);
            List<Method> registrationMethods = new ArrayList<>(3);
            List<Method> synthesisMethods = new ArrayList<>(3);
            List<Method> validationMethods = new ArrayList<>(3);
            for (Method method : methods) {
                final int modifiers = method.getModifiers();
                if (!method.getReturnType().equals(void.class)) {
                    continue;
                }

                if (!Modifier.isStatic(modifiers) && !Modifier.isAbstract(modifiers)) {
                    if (method.isAnnotationPresent(Discovery.class)) {
                        discoveryMethods.add(method);
                    } else if (method.isAnnotationPresent(Enhancement.class)) {
                        enhanceMethods.add(method);
                    } else if (method.isAnnotationPresent(Registration.class)) {
                        registrationMethods.add(method);
                    } else if (method.isAnnotationPresent(Synthesis.class)) {
                        synthesisMethods.add(method);
                    } else if (method.isAnnotationPresent(Validation.class)) {
                        validationMethods.add(method);
                    }
                }
            }
            this.discoveryMethods = immutableListOf(discoveryMethods);
            this.enhanceMethods = immutableListOf(enhanceMethods);
            this.registrationMethods = immutableListOf(registrationMethods);
            this.synthesisMethods = immutableListOf(synthesisMethods);
            this.validationMethods = immutableListOf(validationMethods);
        }

        private List<Method> immutableListOf(List<Method> discoveryMethods) {
            return discoveryMethods.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(discoveryMethods);
        }

        @Override
        public int getOrder() {
            final Priority priority = extension.getClass().getAnnotation(Priority.class);
            if (priority != null) {
                return -priority.value();
            }
            return 0;
        }
    }
}

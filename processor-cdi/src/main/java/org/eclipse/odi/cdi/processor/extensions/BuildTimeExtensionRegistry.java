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
package org.eclipse.odi.cdi.processor.extensions;

import io.micronaut.context.LifeCycle;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.reflect.exception.InvocationException;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.beans.BeanElement;
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.DeclarationConfig;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.FieldConfig;
import jakarta.enterprise.inject.build.compatible.spi.InterceptorInfo;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.InvokerValidation;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.inject.build.compatible.spi.ObserverInfo;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserverBuilder;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.inject.build.compatible.spi.Validation;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.enterprise.invoke.AsyncHandler;
import jakarta.interceptor.Interceptor;
import org.eclipse.odi.cdi.OdiExecutableInvokerInfo;
import org.eclipse.odi.cdi.processor.transformers.InterceptorBindingTransformer;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Internal implementation for build time extensions.
 */
@Internal
public class BuildTimeExtensionRegistry implements LifeCycle<BuildTimeExtensionRegistry> {
    private static final Set<Class<?>> SUPPORTED_ENHANCEMENT_PARAMETERS = Set.of(ClassInfo.class,
                                                                                 ClassConfig.class,
                                                                                 FieldInfo.class,
                                                                                 FieldConfig.class,
                                                                                 MethodInfo.class,
                                                                                 MethodConfig.class);
    private static final Set<Class<?>> SUPPORTED_REGISTRATION_PARAMETERS = Set.of(BeanInfo.class,
                                                                                 InterceptorInfo.class,
                                                                                 ObserverInfo.class);
    private static BuildTimeExtensionRegistry instance = new BuildTimeExtensionRegistry();
    private List<BuildCompatibleExtensionEntry> buildTimeExtensions;
    private final List<String> loadErrors = new ArrayList<>();
    private final List<BeanElement> beanElements = new ArrayList<>();
    private final List<InvokerRegistration> invokers = new ArrayList<>();
    private List<AsyncHandlerMetadata> returnAsyncHandlers;
    private List<AsyncHandlerMetadata> parameterAsyncHandlers;
    private boolean asyncHandlersValidated;
    private DiscoveryImpl discovery;
    private static final String DEPLOYMENT_EXCEPTION_MARKER = "[ODI_DEPLOYMENT_EXCEPTION] ";
    private static final String DEFAULT_QUALIFIER = "jakarta.enterprise.inject.Default";
    private static final String ANY_QUALIFIER = "jakarta.enterprise.inject.Any";

    /**
     * Loads the default extensions.
     */
    protected BuildTimeExtensionRegistry() {
        buildTimeExtensions = loadExtensions();
    }

    /**
     * @return Gets the build time extensions instance.
     */
    public static @NonNull BuildTimeExtensionRegistry getInstance() {
        if (instance == null) {
            instance = new BuildTimeExtensionRegistry();
        }
        return instance;
    }

    /**
     * Sets the build time extension instance.
     * @param instance The instance
     */
    public static void setInstance(@Nullable BuildTimeExtensionRegistry instance) {
        BuildTimeExtensionRegistry.instance = Objects.requireNonNullElseGet(
                instance,
                BuildTimeExtensionRegistry::new
        );
    }

    private List<BuildCompatibleExtensionEntry> loadExtensions() {
        final List<BuildCompatibleExtensionEntry> buildTimeExtensions = new ArrayList<>(20);
        try {
            final SoftServiceLoader<BuildCompatibleExtension> loader = findExtensions();
            for (ServiceDefinition<BuildCompatibleExtension> compatibleExtension : loader) {
                if (compatibleExtension.isPresent()) {
                    final BuildCompatibleExtension buildCompatibleExtension = compatibleExtension.load();
                    buildTimeExtensions.add(new BuildCompatibleExtensionEntry(buildCompatibleExtension, loadErrors));
                }
            }
        } catch (Exception e) {
            loadErrors.add("Error loading CDI build time extensions: " + e.getMessage());
        }

        OrderUtil.sort(buildTimeExtensions);
        return buildTimeExtensions;
    }

    /**
     * @return Finds the extensions
     */
    protected @NonNull SoftServiceLoader<BuildCompatibleExtension> findExtensions() {
        return SoftServiceLoader.load(BuildCompatibleExtension.class);
    }

    /**
     * Runs the discovery phase.
     *
     * @param visitorContext The visitor context
     * @return The discovery implementation
     */
    public DiscoveryImpl runDiscovery(VisitorContext visitorContext) {
        if (discovery == null) {


            this.discovery = new DiscoveryImpl(visitorContext);
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
                        try {
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
                                        throw new BuildTimeExtensionException("Unsupported parameter of type '" + parameterType.getName() + "'");
                                    }
                                }
                                invokeExtensionMethod(extension, discoveryMethod, parameters);
                            }
                        } catch (Exception e) {
                            handleExtensionException(
                                    extension,
                                    discoveryMethod,
                                    null,
                                    visitorContext,
                                    e,
                                    "Error running build time discovery in method '"
                            );
                        }
                    }
                }
            }
        }
        return discovery;
    }

    /**
     * @return Obtain the resolved discovery.
     */
    public @Nullable DiscoveryImpl getDiscovery() {
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
        runDiscoveryEnhancements(typeToEnhance);
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
                        if (ArrayUtils.isEmpty(aw) || matchesWithAnnotations(typeToEnhance, aw)) {
                            runEnhancement(originatingElement, typeToEnhance, visitorContext, extension, enhanceMethod);
                            continue outer;
                        }
                    }
                }

            }
        }
    }

    private boolean matchesWithAnnotations(ClassElement typeToEnhance, Class<? extends Annotation>[] annotationTypes) {
        for (Class<? extends Annotation> annotationType : annotationTypes) {
            if (hasAnnotationOnClassOrMember(typeToEnhance, annotationType.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnnotationOnClassOrMember(ClassElement classElement, String annotationName) {
        if (classElement.hasAnnotation(annotationName)) {
            return true;
        }
        if (!classElement.getEnclosedElements(ElementQuery.ALL_FIELDS
                .annotated(annotationMetadata -> annotationMetadata.hasAnnotation(annotationName))).isEmpty()) {
            return true;
        }
        if (!classElement.getEnclosedElements(ElementQuery.CONSTRUCTORS
                .annotated(annotationMetadata -> annotationMetadata.hasAnnotation(annotationName))).isEmpty()) {
            return true;
        }
        for (MethodElement method : classElement.getEnclosedElements(ElementQuery.ALL_METHODS)) {
            if (method.hasAnnotation(annotationName)) {
                return true;
            }
            for (ParameterElement parameter : method.getParameters()) {
                if (parameter.hasAnnotation(annotationName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Runs any discovery enhancements like adding/removing qualifiers etc.
     * @param typeToEnhance The type to enhance
     */
    public void runDiscoveryEnhancements(Element typeToEnhance) {
        final MetaAnnotationsImpl metaAnnotations = discovery.getMetaAnnotations();
        final Set<MetaAnnotationImpl> qualifiers = metaAnnotations.getQualifiers();
        final Set<MetaAnnotationImpl> interceptorBindings = metaAnnotations.getInterceptorBindings();

        for (MetaAnnotationImpl annotation : qualifiers) {
            final String annotationName = annotation.getName();
            final String[] nonBindingMembers = annotation.getNonBindingMembers();
            if (typeToEnhance.hasDeclaredAnnotation(annotationName)) {
                final AnnotationValue<Annotation> annotationValue = typeToEnhance.getAnnotation(annotationName);
                if (annotationValue != null) {
                    final AnnotationValueBuilder<Annotation> qualifierBuilder =
                            AnnotationValue.builder(AnnotationUtil.QUALIFIER);
                    if (ArrayUtils.isNotEmpty(nonBindingMembers)) {
                        qualifierBuilder.member(AnnotationUtil.NON_BINDING_ATTRIBUTE, nonBindingMembers);
                    }

                    final List<AnnotationValue<?>> stereotypes = new ArrayList<>();
                    if (annotationValue.getStereotypes() != null) {
                        for (AnnotationValue<?> stereotype : annotationValue.getStereotypes()) {
                            if (!AnnotationUtil.QUALIFIER.equals(stereotype.getAnnotationName())) {
                                stereotypes.add(stereotype);
                            }
                        }
                    }
                    stereotypes.add(qualifierBuilder.build());

                    typeToEnhance.annotate(
                            AnnotationValue.builder(annotationValue, RetentionPolicy.RUNTIME)
                                    .replaceStereotypes(stereotypes)
                                    .build()
                    );
                }
            }
        }
        for (MetaAnnotationImpl annotation : interceptorBindings) {
            final String annotationName = annotation.getName();
            if (typeToEnhance.hasDeclaredAnnotation(annotationName)) {
                final AnnotationValue<Annotation> annotationValue = typeToEnhance.getAnnotation(annotationName);
                if (annotationValue != null) {
                    final AnnotationValueBuilder<Annotation> annotationBuilder =
                            AnnotationValue.builder(annotationValue, RetentionPolicy.RUNTIME);
                    final String[] nonBindingMembers = annotation.getNonBindingMembers();
                    if (ArrayUtils.isNotEmpty(nonBindingMembers)) {
                        annotationBuilder.member(AnnotationUtil.NON_BINDING_ATTRIBUTE, nonBindingMembers);
                    }
                    AnnotationValue<Annotation> rewrittenAnnotationValue = annotationBuilder.build();
                    AnnotationValue<Annotation> bindingValues = bindingValues(rewrittenAnnotationValue);

                    final List<AnnotationValue<?>> stereotypes = new ArrayList<>();
                    if (annotationValue.getStereotypes() != null) {
                        for (AnnotationValue<?> stereotype : annotationValue.getStereotypes()) {
                            if (!AnnotationUtil.ANN_INTERCEPTOR_BINDING.equals(stereotype.getAnnotationName())) {
                                stereotypes.add(stereotype);
                            }
                        }
                    }
                    for (AnnotationValue<?> interceptorBinding : InterceptorBindingTransformer.INTERCEPTOR_BINDING_VALUES) {
                        stereotypes.add(AnnotationValue.builder(interceptorBinding, RetentionPolicy.RUNTIME)
                                .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(annotationName))
                                .member(InterceptorBindingQualifier.META_BINDING_VALUES, bindingValues)
                                .build());
                    }
                    typeToEnhance.annotate(AnnotationValue.builder(rewrittenAnnotationValue, RetentionPolicy.RUNTIME)
                            .replaceStereotypes(stereotypes)
                            .build());
                }
            }
        }
    }

    private AnnotationValue<Annotation> bindingValues(AnnotationValue<?> annotationValue) {
        Map<CharSequence, Object> values = new LinkedHashMap<>(annotationValue.getValues());
        values.remove(AnnotationUtil.NON_BINDING_ATTRIBUTE);
        for (String nonBinding : annotationValue.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE)) {
            values.remove(nonBinding);
        }
        return AnnotationValue.<Annotation>builder(annotationValue.getAnnotationName())
                .members(values)
                .build();
    }

    /**
     * Runs the {@link jakarta.enterprise.inject.build.compatible.spi.Registration} phase.
     *
     * @param beanElement    The bean element
     * @param visitorContext The visitor context
     */
    public void runRegistration(BeanElement beanElement, VisitorContext visitorContext) {
        beanElements.add(beanElement);
        final Set<ClassElement> beanTypes = beanElement.getBeanTypes();
        for (BuildCompatibleExtensionEntry entry : buildTimeExtensions) {
            final BuildCompatibleExtension extension = entry.extension;
            final List<Method> processingMethods = entry.registrationMethods;
            for (Method processingMethod : processingMethods) {
                if (processingMethod.getParameterTypes().length == 0) {
                    visitorContext.fail("Registration method '"
                                                + processingMethod.getName()
                                                + "' of extension: " + extension.getClass().getName() + " specifies no parameters", beanElement);
                    continue;
                }
                runRegistration(extension, processingMethod, beanElement, visitorContext);
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
    public @NonNull
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

    /**
     * Runs the validation phase.
     *
     * @param visitorContext The visitor context
     */
    @SuppressWarnings("java:S1119")
    public void runValidation(VisitorContext visitorContext) {
        for (BuildCompatibleExtensionEntry entry : buildTimeExtensions) {
            final BuildCompatibleExtension extension = entry.extension;
            methods:
            for (Method validationMethod : entry.validationMethods) {
                try {
                    final Class<?>[] parameterTypes = validationMethod.getParameterTypes();
                    Object[] parameters = new Object[parameterTypes.length];
                    for (int i = 0; i < parameterTypes.length; i++) {
                        Class<?> parameterType = parameterTypes[i];
                        if (Messages.class == parameterType) {
                            parameters[i] = new MessagesImpl(visitorContext);
                        } else if (Types.class == parameterType) {
                            parameters[i] = new TypesImpl(visitorContext);
                        } else if (InvokerValidation.class == parameterType) {
                            parameters[i] = new InvokerValidationImpl(visitorContext, this);
                        } else {
                            unsupportedParameter(
                                null,
                                visitorContext,
                                extension,
                                validationMethod,
                                parameterType
                            );
                            continue methods;
                        }
                    }
                    invokeExtensionMethod(extension, validationMethod, parameters);
                } catch (Exception e) {
                    handleExtensionException(
                            extension,
                            validationMethod,
                            null,
                            visitorContext,
                            e,
                            "Validation error in method '"
                    );

                }
            }
        }
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
                parameters[i] = new SyntheticComponents() {
                    @Override
                    public <T> SyntheticBeanBuilder<T> addBean(Class<T> beanClass) {
                        return syntheticComponents.addBean(beanClass);
                    }

                    @Override
                    public <T> SyntheticObserverBuilder<T> addObserver(Class<T> eventType) {
                        SyntheticObserverBuilder<T> observerBuilder = syntheticComponents.addObserver(eventType);
                        observerBuilder.declaringClass(extension.getClass());
                        return observerBuilder;
                    }

                    @Override
                    public <T> SyntheticObserverBuilder<T> addObserver(Type eventType) {
                        SyntheticObserverBuilder<T> observerBuilder = syntheticComponents.addObserver(eventType);
                        observerBuilder.declaringClass(extension.getClass());
                        return observerBuilder;
                    }
                };
            } else if (Types.class == parameterType) {
                parameters[i] = new TypesImpl(visitorContext);
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
            invokeExtensionMethod(extension, synthesisMethod, parameters);
        } catch (Exception e) {
            visitorContext.fail("Error running build time synthesis in method '" + synthesisMethod.getName() + "' of extension "
                    + "[" + extension.getClass()
                    .getName() + "]: " + e.getMessage(), originatingBean.getProducingElement());

        }
    }

    @SuppressWarnings("java:S1181")
    private void runRegistration(BuildCompatibleExtension extension,
                                 Method registrationMethod,
                                 BeanElement beanElement,
                                 VisitorContext visitorContext) {
        try {
            final Class<?>[] parameterTypes = registrationMethod.getParameterTypes();
            Object[] parameters = new Object[parameterTypes.length];
            ExtensionParameter extensionParameter = null;
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                if (BeanInfo.class == parameterType || InterceptorInfo.class == parameterType || ObserverInfo.class  == parameterType) {
                    extensionParameter = newExtensionParameter(parameterType, i, extensionParameter, SUPPORTED_REGISTRATION_PARAMETERS);
                } else if (Types.class == parameterType) {
                    parameters[i] = new TypesImpl(visitorContext);
                } else if (Messages.class == parameterType) {
                    parameters[i] = new MessagesImpl(visitorContext);
                } else if (InvokerFactory.class == parameterType) {
                    parameters[i] = new InvokerFactoryImpl(this);
                } else {
                    unsupportedParameter(
                            beanElement.getProducingElement(),
                            visitorContext,
                            extension,
                            registrationMethod,
                            parameterType
                    );
                    return;
                }
            }

            if (extensionParameter == null) {
                throw new BuildTimeExtensionException("At least 1 parameter of type BeanInfo, ObserverInfo or InterceptorInfo is required");
            } else {
                final Class<?> type = extensionParameter.type;
                List<RegistrationType> registrationTypes = registrationTypes(registrationMethod, visitorContext);
                if (registrationTypes == null) {
                    return;
                }
                final BeanInfoImpl beanInfo = new BeanInfoImpl(
                        beanElement,
                        visitorContext
                );
                if (BeanInfo.class == type && matchesBeanRegistration(beanElement, registrationTypes)) {
                    parameters[extensionParameter.index] = beanInfo;
                    invokeExtensionMethod(extension, registrationMethod, parameters);
                } else if (InterceptorInfo.class == type
                        && beanElement.hasDeclaredAnnotation(Interceptor.class)
                        && matchesBeanRegistration(beanElement, registrationTypes)) {
                    parameters[extensionParameter.index] = new InterceptorInfoImpl(
                            beanElement,
                            visitorContext
                    );
                    invokeExtensionMethod(extension, registrationMethod, parameters);
                } else if (ObserverInfo.class == type) {
                    List<ObserverInfo> observerInfos = beanInfo.observers();
                    for (ObserverInfo observerInfo : observerInfos) {
                        if (!matchesObserverRegistration(observerInfo, registrationTypes)) {
                            continue;
                        }
                        parameters[extensionParameter.index] = observerInfo;
                        invokeExtensionMethod(extension, registrationMethod, parameters);
                    }
                }

            }

        } catch (Throwable e) {
            handleExtensionException(
                    extension,
                    registrationMethod,
                    beanElement,
                    visitorContext,
                    e,
                    "Error running build time registration in method '"
            );

        }
    }

    @Nullable
    private List<RegistrationType> registrationTypes(Method registrationMethod, VisitorContext visitorContext) {
        List<RegistrationType> registrationTypes = new ArrayList<>();
        for (Class<?> type : registrationMethod.getAnnotation(Registration.class).types()) {
            if (type == null) {
                continue;
            }
            ClassElement typeElement = visitorContext.getClassElement(type).orElse(ClassElement.of(type));
            if (typeElement.isAssignable(TypeLiteral.class)) {
                Map<String, ClassElement> typeArguments = typeElement.getTypeArguments(TypeLiteral.class);
                if (typeArguments.size() != 1 || typeElement.isRawType()) {
                    visitorContext.fail("Registration type literal must declare exactly one type argument: " + type.getName(), null);
                    return null;
                }
                ClassElement literalType = typeArguments.values().iterator().next();
                if (containsInvalidRegistrationTypeArgument(literalType)) {
                    visitorContext.fail("Registration type literal must not contain type variables or wildcards: " + type.getName(), null);
                    return null;
                }
                registrationTypes.add(new RegistrationType(literalType));
            } else {
                registrationTypes.add(new RegistrationType(typeElement));
            }
        }
        return registrationTypes;
    }

    private boolean containsInvalidRegistrationTypeArgument(ClassElement type) {
        if (type.isTypeVariable()
                || type.isGenericPlaceholder()
                || type.isWildcard()
                || type.hasUnresolvedTypes()) {
            return true;
        }
        for (ClassElement typeArgument : type.getBoundGenericTypes()) {
            if (containsInvalidRegistrationTypeArgument(typeArgument)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesBeanRegistration(BeanElement beanElement, List<RegistrationType> registrationTypes) {
        Set<ClassElement> beanTypes = beanElement.getBeanTypes();
        for (RegistrationType registrationType : registrationTypes) {
            for (ClassElement beanType : beanTypes) {
                if (matchesRegistrationType(beanType, registrationType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesObserverRegistration(ObserverInfo observerInfo, List<RegistrationType> registrationTypes) {
        ClassElement eventType = classElement(observerInfo.eventType());
        if (eventType == null) {
            return false;
        }
        for (RegistrationType registrationType : registrationTypes) {
            if (matchesRegistrationType(eventType, registrationType)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private ClassElement classElement(Type type) {
        if (type instanceof ClassTypeImpl classType) {
            return classType.getClassElement();
        }
        if (type instanceof ParameterizedTypeImpl parameterizedType) {
            return parameterizedType.getClassElement();
        }
        return null;
    }

    private boolean matchesRegistrationType(ClassElement candidateType, RegistrationType registrationType) {
        if (!candidateType.isAssignable(registrationType.rawTypeName())) {
            return false;
        }
        List<? extends ClassElement> requiredTypeArguments = registrationType.typeArguments();
        if (requiredTypeArguments.isEmpty()) {
            return true;
        }
        List<? extends ClassElement> candidateTypeArguments = candidateType.getTypeArguments(registrationType.rawTypeName())
                .values()
                .stream()
                .toList();
        if (candidateTypeArguments.isEmpty()) {
            candidateTypeArguments = candidateType.getBoundGenericTypes();
        }
        return typeArgumentsEqual(candidateTypeArguments, requiredTypeArguments);
    }

    private boolean typeArgumentsEqual(List<? extends ClassElement> candidateTypeArguments,
                                       List<? extends ClassElement> requiredTypeArguments) {
        if (candidateTypeArguments.size() != requiredTypeArguments.size()) {
            return false;
        }
        for (int i = 0; i < requiredTypeArguments.size(); i++) {
            if (!typeArgumentEqual(candidateTypeArguments.get(i), requiredTypeArguments.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean typeArgumentEqual(ClassElement candidateTypeArgument, ClassElement requiredTypeArgument) {
        if (!candidateTypeArgument.getName().equals(requiredTypeArgument.getName())) {
            return false;
        }
        return typeArgumentsEqual(candidateTypeArgument.getBoundGenericTypes(), requiredTypeArgument.getBoundGenericTypes());
    }

    private record RegistrationType(ClassElement type) {
        String rawTypeName() {
            return type.getName();
        }

        List<? extends ClassElement> typeArguments() {
            return type.getBoundGenericTypes();
        }
    }

    private void handleExtensionException(BuildCompatibleExtension extension,
                           Method method,
                           @Nullable Element beanElement,
                           VisitorContext visitorContext,
                           Throwable e,
                           String rootMessage) {
        Throwable exception = unwrapExtensionException(e);
        String message = rootMessage + method.getName() + "' of extension "
                                    + "[" + extension.getClass()
                .getName() + "]: " + exception.getMessage();
        if (exception instanceof DeploymentException) {
            message = DEPLOYMENT_EXCEPTION_MARKER + message;
        }
        visitorContext.fail(message, beanElement);
    }

    private Throwable unwrapExtensionException(Throwable e) {
        Throwable exception = e;
        while ((exception instanceof InvocationException || exception instanceof InvocationTargetException)
                && exception.getCause() != null) {
            exception = exception.getCause();
        }
        return exception;
    }

    void registerInvoker(OdiExecutableInvokerInfo invokerInfo, MethodElement methodElement) {
        invokers.add(new InvokerRegistration(invokerInfo, methodElement));
    }

    void validateInvokers(VisitorContext visitorContext) {
        validateAsyncHandlers(visitorContext);
        List<AsyncHandlerMetadata> returnHandlers = returnAsyncHandlers(visitorContext);
        List<AsyncHandlerMetadata> parameterHandlers = parameterAsyncHandlers(visitorContext);
        for (InvokerRegistration invoker : invokers) {
            configureAsyncHandlers(invoker.invokerInfo, invoker.methodElement, returnHandlers, parameterHandlers);
            ParameterElement[] parameters = invoker.methodElement.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                if (invoker.invokerInfo.isArgumentLookup(i)) {
                    validateArgumentLookup(visitorContext, invoker.methodElement, parameters[i]);
                }
            }
        }
    }

    void validateAsyncHandlers(VisitorContext visitorContext) {
        if (asyncHandlersValidated) {
            return;
        }
        asyncHandlersValidated = true;
        validateDuplicateAsyncHandlers(visitorContext, returnAsyncHandlers(visitorContext), AsyncHandler.ReturnType.class);
        validateDuplicateAsyncHandlers(visitorContext, parameterAsyncHandlers(visitorContext), AsyncHandler.ParameterType.class);
    }

    void validateSyntheticInjectionPoints(VisitorContext visitorContext,
                                          SyntheticBeanBuilderImpl<?> syntheticBeanBuilder) {
        for (SyntheticBeanBuilderImpl.SyntheticInjectionPoint injectionPoint : syntheticBeanBuilder.getInjectionPoints()) {
            ClassElement requiredType = injectionPoint.type();
            String requiredTypeName = requiredType.getName();
            if (isBuiltinSyntheticInjectionPointType(requiredTypeName)) {
                continue;
            }
            List<String> requiredQualifiers = syntheticInjectionPointQualifiers(injectionPoint);
            int candidates = 0;
            for (BeanElement beanElement : beanElements) {
                if (isCandidateBean(beanElement)
                        && matchesRequiredType(beanElement, requiredType)
                        && matchesRequiredQualifiers(beanElement, requiredQualifiers)) {
                    candidates++;
                }
            }
            if (candidates == 0) {
                visitorContext.fail(DEPLOYMENT_EXCEPTION_MARKER
                        + "Unsatisfied synthetic injection point for " + requiredTypeName, syntheticBeanBuilder.getBeanType());
            } else if (candidates > 1) {
                visitorContext.fail(DEPLOYMENT_EXCEPTION_MARKER
                        + "Ambiguous synthetic injection point for " + requiredTypeName,
                        syntheticBeanBuilder.getBeanType());
            }
        }
    }

    private List<AsyncHandlerMetadata> returnAsyncHandlers(VisitorContext visitorContext) {
        if (returnAsyncHandlers == null) {
            returnAsyncHandlers = asyncHandlers(visitorContext, AsyncHandler.ReturnType.class);
        }
        return returnAsyncHandlers;
    }

    private List<AsyncHandlerMetadata> parameterAsyncHandlers(VisitorContext visitorContext) {
        if (parameterAsyncHandlers == null) {
            parameterAsyncHandlers = asyncHandlers(visitorContext, AsyncHandler.ParameterType.class);
        }
        return parameterAsyncHandlers;
    }

    protected SoftServiceLoader<?> findAsyncHandlers(Class<?> handlerType) {
        return SoftServiceLoader.load(handlerType);
    }

    private List<AsyncHandlerMetadata> asyncHandlers(VisitorContext visitorContext, Class<?> handlerInterface) {
        List<AsyncHandlerMetadata> result = new ArrayList<>();
        for (ServiceDefinition<?> definition : findAsyncHandlers(handlerInterface)) {
            java.util.Optional<ClassElement> handlerElement = visitorContext.getClassElement(definition.getName());
            if (handlerElement.isEmpty()) {
                visitorContext.fail("Async handler provider [" + definition.getName() + "] is not available", null);
                continue;
            }
            asyncHandlerMetadata(visitorContext, handlerElement.get(), handlerInterface.getName())
                    .ifPresent(result::add);
        }
        return result;
    }

    boolean hasAsyncHandler(VisitorContext visitorContext, String asyncTypeName) {
        return returnAsyncHandlers(visitorContext)
                .stream()
                .anyMatch(handler -> handler.asyncTypeName.equals(asyncTypeName))
                || parameterAsyncHandlers(visitorContext)
                .stream()
                .anyMatch(handler -> handler.asyncTypeName.equals(asyncTypeName));
    }

    private java.util.Optional<AsyncHandlerMetadata> asyncHandlerMetadata(VisitorContext visitorContext,
                                                                          ClassElement handlerElement,
                                                                          String handlerInterfaceName) {
        if (!directlyImplements(handlerElement, handlerInterfaceName)) {
            visitorContext.fail("Async handler [" + handlerElement.getName() + "] must directly implement ["
                    + handlerInterfaceName + "]", handlerElement);
            return java.util.Optional.empty();
        }
        if (directlyImplements(handlerElement, AsyncHandler.ReturnType.class.getName())
                && directlyImplements(handlerElement, AsyncHandler.ParameterType.class.getName())) {
            visitorContext.fail("Async handler [" + handlerElement.getName()
                    + "] must not implement both AsyncHandler.ReturnType and AsyncHandler.ParameterType", handlerElement);
            return java.util.Optional.empty();
        }
        Map<String, ClassElement> typeArguments = handlerElement.getTypeArguments(handlerInterfaceName);
        if (typeArguments.size() != 1 || handlerElement.isRawType()) {
            visitorContext.fail("Async handler [" + handlerElement.getName()
                    + "] must declare exactly one async type argument for [" + handlerInterfaceName + "]", handlerElement);
            return java.util.Optional.empty();
        }
        ClassElement asyncType = typeArguments.values().iterator().next();
        if (asyncType.isArray()
                || asyncType.isTypeVariable()
                || asyncType.isGenericPlaceholder()
                || asyncType.isWildcard()
                || asyncType.hasUnresolvedTypes()) {
            visitorContext.fail("Async handler [" + handlerElement.getName()
                    + "] declares an invalid async type [" + asyncType.getName() + "]", handlerElement);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new AsyncHandlerMetadata(handlerElement.getName(), asyncType.getName()));
    }

    private boolean directlyImplements(ClassElement handlerElement, String handlerInterfaceName) {
        return handlerElement.getInterfaces()
                .stream()
                .anyMatch(interfaceElement -> typeNameEquals(interfaceElement.getName(), handlerInterfaceName));
    }

    private boolean typeNameEquals(String left, String right) {
        return left.equals(right)
                || left.replace('$', '.').equals(right.replace('$', '.'));
    }

    private void validateDuplicateAsyncHandlers(VisitorContext visitorContext,
                                                List<AsyncHandlerMetadata> handlers,
                                                Class<?> handlerInterface) {
        for (int i = 0; i < handlers.size(); i++) {
            AsyncHandlerMetadata left = handlers.get(i);
            for (int j = i + 1; j < handlers.size(); j++) {
                AsyncHandlerMetadata right = handlers.get(j);
                if (left.asyncTypeName.equals(right.asyncTypeName)
                        && !left.handlerClassName.equals(right.handlerClassName)) {
                    visitorContext.fail(DEPLOYMENT_EXCEPTION_MARKER
                            + "Multiple async handlers of type [" + handlerInterface.getName()
                            + "] declare async type [" + left.asyncTypeName + "]", null);
                    return;
                }
            }
        }
    }

    private void configureAsyncHandlers(OdiExecutableInvokerInfo invokerInfo,
                                        MethodElement methodElement,
                                        List<AsyncHandlerMetadata> returnHandlers,
                                        List<AsyncHandlerMetadata> parameterHandlers) {
        String returnTypeName = methodElement.getReturnType().getType().getName();
        for (AsyncHandlerMetadata handler : returnHandlers) {
            if (handler.asyncTypeName.equals(returnTypeName)) {
                invokerInfo.asyncReturnHandler(handler.handlerClassName);
                return;
            }
        }
        for (AsyncHandlerMetadata handler : parameterHandlers) {
            int parameterIndex = uniqueParameterIndex(methodElement, handler.asyncTypeName);
            if (parameterIndex >= 0) {
                invokerInfo.asyncParameterHandler(handler.handlerClassName, parameterIndex);
                return;
            }
        }
    }

    private int uniqueParameterIndex(MethodElement methodElement, String typeName) {
        int index = -1;
        ParameterElement[] parameters = methodElement.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getType().getName().equals(typeName)) {
                if (index >= 0) {
                    return -1;
                }
                index = i;
            }
        }
        return index;
    }

    private void validateArgumentLookup(VisitorContext visitorContext,
                                        MethodElement methodElement,
                                        ParameterElement parameterElement) {
        ClassElement requiredType = parameterElement.getGenericType();
        String requiredTypeName = requiredType.getName();
        if (isBuiltinLookupType(requiredTypeName)) {
            return;
        }
        int candidates = 0;
        for (BeanElement beanElement : beanElements) {
            if (isCandidateBean(beanElement)
                    && matchesRequiredType(beanElement, requiredType)
                    && matchesRequiredQualifiers(beanElement, parameterElement)) {
                candidates++;
            }
        }
        if (candidates == 0) {
            visitorContext.fail(DEPLOYMENT_EXCEPTION_MARKER
                    + "Unsatisfied invoker argument lookup for " + requiredTypeName, methodElement);
        } else if (candidates > 1) {
            visitorContext.fail(DEPLOYMENT_EXCEPTION_MARKER
                    + "Ambiguous invoker argument lookup for " + requiredTypeName, methodElement);
        }
    }

    private boolean isBuiltinLookupType(String typeName) {
        return typeName.equals("jakarta.enterprise.inject.spi.BeanContainer")
                || typeName.equals("jakarta.enterprise.inject.spi.BeanManager")
                || typeName.equals("jakarta.enterprise.event.Event")
                || typeName.equals("jakarta.enterprise.inject.Instance");
    }

    private boolean isBuiltinSyntheticInjectionPointType(String typeName) {
        return typeName.equals("jakarta.enterprise.inject.spi.InjectionPoint")
                || isBuiltinLookupType(typeName);
    }

    private boolean isCandidateBean(BeanElement beanElement) {
        return !beanElement.getProducingElement().getName().contains("$Definition$Intercepted");
    }

    private boolean matchesRequiredType(BeanElement beanElement, ClassElement requiredType) {
        String requiredTypeName = requiredType.getName();
        return beanElement.getBeanTypes()
                .stream()
                .anyMatch(beanType -> beanType.getName().equals(requiredTypeName));
    }

    private List<String> syntheticInjectionPointQualifiers(SyntheticBeanBuilderImpl.SyntheticInjectionPoint injectionPoint) {
        List<String> requiredQualifiers = new ArrayList<>();
        for (Annotation qualifier : injectionPoint.qualifiers()) {
            requiredQualifiers.add(qualifier.annotationType().getName());
        }
        injectionPoint.qualifierInfos()
                .stream()
                .map(jakarta.enterprise.lang.model.AnnotationInfo::name)
                .forEach(requiredQualifiers::add);
        if (requiredQualifiers.isEmpty()) {
            requiredQualifiers = Collections.singletonList(DEFAULT_QUALIFIER);
        }
        return requiredQualifiers;
    }

    private boolean matchesRequiredQualifiers(BeanElement beanElement, ParameterElement parameterElement) {
        List<String> requiredQualifiers = parameterElement.getAnnotationMetadata()
                .getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER);
        if (requiredQualifiers.isEmpty()) {
            requiredQualifiers = Collections.singletonList(DEFAULT_QUALIFIER);
        }
        return matchesRequiredQualifiers(beanElement, requiredQualifiers);
    }

    private boolean matchesRequiredQualifiers(BeanElement beanElement, List<String> requiredQualifiers) {
        Collection<String> beanQualifiers = beanElement.getQualifiers();
        if (requiredQualifiers.size() == 1 && requiredQualifiers.contains(ANY_QUALIFIER)) {
            return true;
        }
        if (beanQualifiers.isEmpty()) {
            return requiredQualifiers.size() == 1 && requiredQualifiers.contains(DEFAULT_QUALIFIER);
        }
        return beanQualifiers.containsAll(requiredQualifiers);
    }

    @SuppressWarnings("java:S1181")
    private void runEnhancement(ClassElement originatingElement,
                                ClassElement typeToEnhance,
                                VisitorContext visitorContext,
                                BuildCompatibleExtension extension,
                                Method enhanceMethod) {

        try {
            final Class<?>[] parameterTypes = enhanceMethod.getParameterTypes();
            if (parameterTypes.length == 0) {
                visitorContext.fail("Enhancement method '"
                                            + enhanceMethod.getName()
                                            + "' of extension: " + extension.getClass().getName() + " specifies no parameters", originatingElement);
                return;
            }
            Object[] parameters = new Object[parameterTypes.length];
            final TypesImpl types = new TypesImpl(visitorContext);
            ExtensionParameter extensionParameter = null;
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                if (DeclarationInfo.class.isAssignableFrom(parameterType)) {
                    extensionParameter = newExtensionParameter(parameterType, i, extensionParameter, SUPPORTED_ENHANCEMENT_PARAMETERS);
                } else if (DeclarationConfig.class.isAssignableFrom(parameterType)) {
                    extensionParameter = newExtensionParameter(parameterType, i, extensionParameter, SUPPORTED_ENHANCEMENT_PARAMETERS);
                } else if (parameterType == Messages.class) {
                    parameters[i] = new MessagesImpl(visitorContext);
                } else if (parameterType == Types.class) {
                    parameters[i] = types;
                } else {
                    unsupportedParameter(originatingElement, visitorContext, extension, enhanceMethod, parameterType);
                    return;
                }
            }

            if (extensionParameter != null) {
                final Class<?> type = extensionParameter.type;
                final ClassConfigImpl classConfig = new ClassConfigImpl(
                        typeToEnhance,
                        types,
                        visitorContext
                );
                if (ClassConfig.class == type || ClassInfo.class == type) {
                    parameters[extensionParameter.index] = ClassInfo.class == type ? classConfig.info() : classConfig;
                    invokeExtensionMethod(extension, enhanceMethod, parameters);
                } else if (FieldConfig.class == type || FieldInfo.class == type) {
                    final Collection<FieldConfig> fields = classConfig.fields();
                    for (FieldConfig field : fields) {
                        parameters[extensionParameter.index] = FieldInfo.class == type ? field.info() : field;
                        invokeExtensionMethod(extension, enhanceMethod, parameters);
                    }
                } else if (MethodConfig.class == type || MethodInfo.class == type) {
                    final Collection<MethodConfig> methods = classConfig.methods();
                    for (MethodConfig method : methods) {
                        parameters[extensionParameter.index] = MethodInfo.class == type ? method.info() : method;
                        invokeExtensionMethod(extension, enhanceMethod, parameters);
                    }
                }
            } else {
                visitorContext.fail("Enhancement method '"
                                            + enhanceMethod.getName()
                                            + "' of extension: " + extension.getClass().getName() + " does not specify any declaration config or info types", originatingElement);

            }

        } catch (Throwable e) {
            handleExtensionException(
                    extension,
                    enhanceMethod,
                    typeToEnhance,
                    visitorContext,
                    e,
                    "Error running build time enhancement in method '"
            );
        }
    }

    @SuppressWarnings("java:S3011")
    private void invokeExtensionMethod(BuildCompatibleExtension extension,
                                       Method enhanceMethod,
                                       Object[] parameters) {
        enhanceMethod.setAccessible(true);
        ReflectionUtils.invokeMethod(
                extension,
                enhanceMethod,
                parameters
        );
    }

    private ExtensionParameter newExtensionParameter(Class<?> parameterType,
                                                     int index,
                                                     ExtensionParameter extensionParameter,
                                                     Set<Class<?>> supportedExtensionParameterTypes) {
        if (extensionParameter != null) {
            throw new BuildTimeExtensionException("Extension parameter of type [" + extensionParameter.getClass() + "] already defined at index " + extensionParameter.index);
        }
        if (supportedExtensionParameterTypes.contains(parameterType)) {
            return new ExtensionParameter(index, parameterType);
        } else {
            throw new BuildTimeExtensionException("Unsupported parameter type: " + parameterType.getName());
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
        if (CollectionUtils.isEmpty(buildTimeExtensions)) {
            this.buildTimeExtensions = loadExtensions();
        }
        return this;
    }

    @Override
    public BuildTimeExtensionRegistry stop() {
        this.buildTimeExtensions.clear();
        this.beanElements.clear();
        this.invokers.clear();
        this.returnAsyncHandlers = null;
        this.parameterAsyncHandlers = null;
        this.asyncHandlersValidated = false;
        return this;
    }

    private static final class InvokerRegistration {
        final OdiExecutableInvokerInfo invokerInfo;
        final MethodElement methodElement;

        private InvokerRegistration(OdiExecutableInvokerInfo invokerInfo, MethodElement methodElement) {
            this.invokerInfo = invokerInfo;
            this.methodElement = methodElement;
        }
    }

    private static final class AsyncHandlerMetadata {
        final String handlerClassName;
        final String asyncTypeName;

        private AsyncHandlerMetadata(String handlerClassName, String asyncTypeName) {
            this.handlerClassName = handlerClassName;
            this.asyncTypeName = asyncTypeName;
        }
    }

    private static final class ExtensionParameter {
        final int index;
        final Class<?> type;

        private ExtensionParameter(int index, Class<?> type) {
            this.index = index;
            this.type = type;
        }
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

        BuildCompatibleExtensionEntry(BuildCompatibleExtension extension, List<String> loadErrors) {
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
                        final Class<?>[] parameterTypes = method.getParameterTypes();
                        if (parameterTypes.length == 1 && Messages.class.isAssignableFrom(parameterTypes[0])) {
                            loadErrors.add("Extension method [" + method + "] of type [" + extension.getClass().getName() + "] cannot define only Messages as a parameter.");
                            continue;
                        }
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
            discoveryMethods.sort((o1, o2) -> {
                final Priority p1 = o1.getAnnotation(Priority.class);
                final Priority p2 = o2.getAnnotation(Priority.class);

                // see javadoc for BuildCompatibleExtension where default priority is defined
                final int n1 = p1 != null ? p1.value() : Interceptor.Priority.APPLICATION + 500;
                final int n2 = p2 != null ? p2.value() : Interceptor.Priority.APPLICATION + 500;
                return Integer.compare(
                        n1, n2
                );
            });
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

    private static final class BuildTimeExtensionException extends RuntimeException {
        public BuildTimeExtensionException(String message) {
            super(message);
        }
    }
}

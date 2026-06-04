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
package org.eclipse.odi.cdi.processor;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.AntPathMatcher;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.util.Nonbinding;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Reserve;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.interceptor.Interceptor;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Internal CDI utils.
 */
@Internal
public final class CdiUtil {
    public static final String SPEC_LOCATION = "https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html";
    public static final String BEAN_CLASSES_OPTION = "micronaut.cdi.bean.classes";
    public static final String BUILD_COMPATIBLE_EXTENSIONS_OPTION = "micronaut.cdi.build.compatible.extensions";
    private static final String SELECTED_ALTERNATIVES_OPTION = "odi.selected-alternatives";
    private static final String DEPLOYMENT_EXCEPTION_MARKER = "[ODI_DEPLOYMENT_EXCEPTION] ";
    private static final String ODI_UNPROXYABLE_BEAN = org.eclipse.odi.cdi.processor.AnnotationUtil.ANN_ODI_UNPROXYABLE_BEAN;

    private CdiUtil() {
    }

    public static boolean validateBeanDefinition(VisitorContext context, Class<? extends Annotation> annotationType, ClassElement classElement) {
        if (!org.eclipse.odi.cdi.processor.AnnotationUtil.hasBeanDefiningAnnotation(classElement)) {
            context.fail(
                    "Class with " + annotationType.getSimpleName() + " must specify a bean definition annotation. See "
                            + CdiUtil.SPEC_LOCATION
                            + "#bean_defining_annotations",
                    classElement
            );
            return true;
        }
        return false;
    }

    public static boolean isBeanClass(ClassElement classElement) {
        if (classElement.getDefaultConstructor().isPresent()) {
            return true;
        }
        return classElement.getAccessibleConstructors().stream()
                .anyMatch(constructor -> constructor.hasDeclaredAnnotation(Inject.class));
    }

    public static boolean validateNoInterceptor(VisitorContext context,
                                                Class<? extends Annotation> annotationType,
                                                MemberElement memberElement) {
        if (memberElement.getOwningType().hasDeclaredAnnotation(Interceptor.class)) {
            String memberKind = memberElement instanceof FieldElement ? "fields" : "methods";
            context.fail("Interceptors cannot have " + memberKind + " annotated with @" + annotationType.getSimpleName(), memberElement);
            return true;
        }
        return false;
    }

    public static boolean validateMethodExtraAnnotations(VisitorContext context,
                                                         Class<? extends Annotation> annotationType,
                                                         MethodElement methodElement) {
        String prefix = methodElement instanceof ConstructorElement ? "Constructors" : "Methods";
        for (Class<? extends Annotation> annotationToCheck : new Class[]{Inject.class, Produces.class}) {
            if (!annotationToCheck.equals(annotationType) && methodElement.hasDeclaredAnnotation(annotationToCheck)) {
                context.fail(prefix + " annotated with @" + annotationType.getSimpleName() + " cannot be annotated with @" + annotationToCheck.getSimpleName(), methodElement);
                return true;
            }
        }
        return false;
    }

    public static boolean validateParameterExtraAnnotations(VisitorContext context,
                                                            Class<? extends Annotation> annotationType,
                                                            MethodElement methodElement,
                                                            ParameterElement parameterElement) {
        String prefix = methodElement instanceof ConstructorElement ? "Constructors" : "Methods";
        for (Class<? extends Annotation> annotationToCheck : new Class[]{Disposes.class, Observes.class, ObservesAsync.class}) {
            if (!annotationToCheck.equals(annotationType) && parameterElement.hasDeclaredAnnotation(annotationToCheck)) {
                context.fail(prefix + " with parameters annotated with @" + annotationType.getSimpleName() + " cannot be annotated with @" + annotationToCheck.getSimpleName(), parameterElement);
                return true;
            }
        }
        return false;
    }

    public static boolean validateMethodNoSpecialParameters(VisitorContext context,
                                                            String annotationSimpleName,
                                                            MethodElement methodElement,
                                                            ParameterElement parameterElement) {
        String prefix = methodElement instanceof ConstructorElement ? "Constructors" : "Methods";
        for (Class<? extends Annotation> annotationToCheck : new Class[]{Disposes.class, Observes.class, ObservesAsync.class}) {
            if (parameterElement.hasDeclaredAnnotation(annotationToCheck)) {
                context.fail(prefix + " annotated with @" + annotationSimpleName + " cannot define parameters annotated with @" + annotationToCheck.getSimpleName(), parameterElement);
                return true;
            }
        }
        return false;
    }

    public static boolean validateField(VisitorContext context,
                                        Class<? extends Annotation> annotationType,
                                        FieldElement fieldElement) {
        if (!io.micronaut.core.annotation.AnnotationUtil.INJECT.equals(annotationType.getName())
                && fieldElement.hasDeclaredAnnotation(io.micronaut.core.annotation.AnnotationUtil.INJECT)) {
            context.fail("Fields annotated with @" + annotationType.getSimpleName() + " cannot be annotated with @Inject", fieldElement);
            return true;
        }
        return false;
    }

    public static String toAnnotationDescription(List<String> annotations) {
        return annotations.stream().map(n -> "@" + NameUtils.getSimpleName(n)).collect(Collectors.joining(" and "));
    }

    public static void visitPriority(VisitorContext context, ClassElement element) {
        if (element.hasDeclaredAnnotation(Order.class)) {
            return;
        }
        OptionalInt priority = resolvePriority(context, element);
        if (priority.isPresent()) {
            element.annotate(Order.class, builder -> builder.value(-priority.getAsInt()));
        }
    }

    public static OptionalInt resolvePriority(VisitorContext context, Element element) {
        OptionalInt directPriority = declaredIntValue(element.getAnnotationMetadata(), Priority.class);
        if (directPriority.isPresent()) {
            return directPriority;
        }
        OptionalInt metadataPriority = resolveStereotypePriority(element.getAnnotationMetadata(), new HashSet<>());
        if (metadataPriority.isPresent()) {
            return metadataPriority;
        }
        return resolveStereotypePriority(
                context,
                element.getAnnotationNames(),
                new HashSet<>()
        );
    }

    private static OptionalInt resolveStereotypePriority(AnnotationMetadata annotationMetadata, Set<String> visited) {
        for (String annotationName : annotationMetadata.getAnnotationNames()) {
            AnnotationValue<Annotation> annotation = annotationMetadata.getAnnotation(annotationName);
            if (annotation == null || annotation.getStereotypes() == null) {
                continue;
            }
            OptionalInt priority = resolveStereotypePriority(annotation.getStereotypes(), visited);
            if (priority.isPresent()) {
                return priority;
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt resolveStereotypePriority(Iterable<AnnotationValue<?>> stereotypes, Set<String> visited) {
        for (AnnotationValue<?> stereotype : stereotypes) {
            String annotationName = stereotype.getAnnotationName();
            if (!visited.add(annotationName)) {
                continue;
            }
            if (annotationName.equals(Priority.class.getName())) {
                OptionalInt priority = stereotype.intValue();
                if (priority.isPresent()) {
                    return priority;
                }
            }
            if (annotationName.equals(Order.class.getName())) {
                int order = stereotype.intValue().orElse(0);
                if (order != 0) {
                    return OptionalInt.of(-order);
                }
            }
            if (stereotype.getStereotypes() != null) {
                OptionalInt priority = resolveStereotypePriority(stereotype.getStereotypes(), visited);
                if (priority.isPresent()) {
                    return priority;
                }
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt resolveStereotypePriority(VisitorContext context, Collection<String> annotationNames, Set<String> visited) {
        for (String annotationName : annotationNames) {
            if (!visited.add(annotationName)) {
                continue;
            }
            OptionalInt priority = context.getClassElement(annotationName)
                    .flatMap(annotation -> {
                        if (!annotation.hasAnnotation(Stereotype.class) && !annotation.hasStereotype(Stereotype.class)) {
                            return java.util.Optional.empty();
                        }
                        OptionalInt directPriority = declaredIntValue(annotation.getAnnotationMetadata(), Priority.class);
                        if (directPriority.isPresent()) {
                            return java.util.Optional.of(directPriority);
                        }
                        int order = declaredIntValue(annotation.getAnnotationMetadata(), Order.class).orElse(0);
                        if (order != 0) {
                            return java.util.Optional.of(OptionalInt.of(-order));
                        }
                        OptionalInt nestedPriority = resolveStereotypePriority(
                                context,
                                annotation.getAnnotationNames(),
                                visited
                        );
                        return nestedPriority.isPresent()
                                ? java.util.Optional.of(nestedPriority)
                                : java.util.Optional.empty();
                    })
                    .orElse(OptionalInt.empty());
            if (priority.isPresent()) {
                return priority;
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt declaredIntValue(AnnotationMetadata annotationMetadata, Class<? extends Annotation> annotationType) {
        AnnotationValue<?> annotation = annotationMetadata.getDeclaredAnnotation(annotationType);
        if (annotation != null) {
            OptionalInt value = annotation.intValue();
            if (value.isPresent()) {
                return value;
            }
        }
        return annotationMetadata.getDeclaredMetadata().intValue(annotationType);
    }

    private static boolean needsDefaultQualifier(AnnotationMetadata annotationMetadata, boolean declaringElementOnly) {
        AnnotationMetadata declaredMetadata;
        if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            declaredMetadata = ((AnnotationMetadataHierarchy) annotationMetadata).getRootMetadata();
        } else {
            declaredMetadata = annotationMetadata.getDeclaredMetadata();
        }
        if (declaringElementOnly) {
            return !declaredMetadata.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)
                    && !declaredMetadata.hasStereotype(AnnotationUtil.QUALIFIER);
        }
        return !declaredMetadata.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)
                && !annotationMetadata.hasStereotype(AnnotationUtil.QUALIFIER);
    }

    private static boolean needsDefaultInjectPointQualifier(AnnotationMetadata annotationMetadata) {
        AnnotationMetadata declaredMetadata = annotationMetadata.getDeclaredMetadata();
        return !declaredMetadata.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)
                && !declaredMetadata.hasDeclaredAnnotation(Any.class)
                && !declaredMetadata.hasDeclaredAnnotation(io.micronaut.context.annotation.Any.class);
    }

    public static void visitBeanDefinition(VisitorContext context, Element beanDefinition) {
        beanDefinition.annotate(org.eclipse.odi.cdi.processor.AnnotationUtil.ANN_ODI_BEAN_DEFINITION);
        boolean declaringElementOnly = beanDefinition instanceof MethodElement || beanDefinition instanceof FieldElement;
        if (needsDefaultQualifier(beanDefinition.getAnnotationMetadata(), declaringElementOnly)) {
            beanDefinition.annotate(Default.class);
        }
        visitQualifierDefaults(context, beanDefinition);
        visitBeanTypes(beanDefinition);
    }

    private static void visitBeanTypes(Element beanDefinition) {
        ClassElement beanType = resolveBeanType(beanDefinition);
        if (beanType == null) {
            return;
        }
        boolean collapseDeclaredTypeVariables = !(beanDefinition instanceof ClassElement);
        Set<ClassElement> beanTypes = new LinkedHashSet<>();
        collectBeanTypes(beanType, beanTypes);
        boolean hasMetadataTypes = beanTypes.stream().anyMatch(type -> hasTypeArguments(type, collapseDeclaredTypeVariables));
        if (!hasMetadataTypes && collapseDeclaredTypeVariables) {
            hasMetadataTypes = beanTypes.stream().anyMatch(CdiUtil::hasRawTypeArguments);
        }
        if (!hasMetadataTypes) {
            return;
        }
        for (ClassElement type : beanTypes) {
            BeanTypeArguments typeArguments = toTypeArguments(type, collapseDeclaredTypeVariables);
            beanDefinition.annotate(AnnotationValue.builder(org.eclipse.odi.cdi.processor.AnnotationUtil.ANN_BEAN_TYPE)
                    .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(type.getName()))
                    .member("arguments", typeArguments.values.toArray(new AnnotationClassValue<?>[0]))
                    .member("argumentCounts", typeArguments.counts.stream().mapToInt(Integer::intValue).toArray())
                    .member("typeVariables", toBooleanArray(typeArguments.typeVariables))
                    .member("wildcards", toBooleanArray(typeArguments.wildcards))
                    .member("lowerBoundCounts", typeArguments.lowerBoundCounts.stream().mapToInt(Integer::intValue).toArray())
                    .member("typeVariableNames", typeArguments.typeVariableNames.toArray(String[]::new))
                    .build());
        }
    }

    private static ClassElement resolveBeanType(Element beanDefinition) {
        if (beanDefinition instanceof MethodElement) {
            return ((MethodElement) beanDefinition).getGenericReturnType();
        }
        if (beanDefinition instanceof FieldElement) {
            return ((FieldElement) beanDefinition).getGenericField();
        }
        if (beanDefinition instanceof ClassElement) {
            return (ClassElement) beanDefinition;
        }
        return null;
    }

    private static void collectBeanTypes(ClassElement beanType, Set<ClassElement> beanTypes) {
        if (beanType == null || beanType.getName().equals(Object.class.getName())) {
            return;
        }
        beanTypes.add(beanType);
        if (beanType.isArray()) {
            return;
        }
        for (ClassElement interfaceType : beanType.getInterfaces()) {
            collectBeanTypes(interfaceType, beanTypes);
        }
        beanType.getSuperType().ifPresent(superType -> collectBeanTypes(superType, beanTypes));
    }

    private static boolean hasTypeArguments(ClassElement beanType, boolean collapseDeclaredTypeVariables) {
        return !resolveTypeArguments(beanType, collapseDeclaredTypeVariables).isEmpty();
    }

    private static boolean hasRawTypeArguments(ClassElement beanType) {
        return beanType.isRawType() && !beanType.getTypeArguments().isEmpty();
    }

    private static BeanTypeArguments toTypeArguments(ClassElement beanType, boolean collapseDeclaredTypeVariables) {
        BeanTypeArguments metadata = new BeanTypeArguments();
        collectTypeArguments(resolveTypeArguments(beanType, collapseDeclaredTypeVariables), metadata, collapseDeclaredTypeVariables);
        return metadata;
    }

    private static void collectTypeArguments(List<ClassElement> typeArguments,
                                             BeanTypeArguments metadata,
                                             boolean collapseDeclaredTypeVariables) {
        for (ClassElement typeArgument : typeArguments) {
            boolean typeVariable = typeArgument instanceof GenericPlaceholderElement;
            boolean wildcard = typeArgument instanceof WildcardElement;
            ClassElement storedTypeArgument = typeArgument;
            List<? extends ClassElement> typeVariableBounds = List.of();
            List<? extends ClassElement> upperBounds = List.of();
            List<? extends ClassElement> lowerBounds = List.of();
            if (wildcard) {
                WildcardElement wildcardElement = (WildcardElement) typeArgument;
                upperBounds = wildcardElement.getUpperBounds();
                lowerBounds = wildcardElement.getLowerBounds();
                if (!upperBounds.isEmpty()) {
                    storedTypeArgument = upperBounds.get(0);
                }
            } else if (typeVariable) {
                GenericPlaceholderElement placeholder = (GenericPlaceholderElement) typeArgument;
                ClassElement resolvedTypeArgument = placeholder.getResolved().orElse(null);
                if (resolvedTypeArgument != null) {
                    storedTypeArgument = resolvedTypeArgument;
                    typeVariable = false;
                } else {
                    typeVariableBounds = placeholder.getBounds();
                    if (!typeVariableBounds.isEmpty()) {
                        storedTypeArgument = typeVariableBounds.get(0);
                    }
                }
            }
            List<ClassElement> nestedTypeArguments;
            int lowerBoundCount = 0;
            if (wildcard) {
                nestedTypeArguments = new ArrayList<>(upperBounds);
                nestedTypeArguments.addAll(lowerBounds);
                lowerBoundCount = lowerBounds.size();
            } else if (typeVariable) {
                nestedTypeArguments = new ArrayList<>(typeVariableBounds);
            } else {
                nestedTypeArguments = resolveTypeArguments(storedTypeArgument, collapseDeclaredTypeVariables);
            }
            metadata.values.add(new AnnotationClassValue<>(storedTypeArgument.getName()));
            metadata.counts.add(nestedTypeArguments.size());
            metadata.typeVariables.add(typeVariable);
            metadata.wildcards.add(wildcard);
            metadata.lowerBoundCounts.add(lowerBoundCount);
            metadata.typeVariableNames.add(typeVariable ? ((GenericPlaceholderElement) typeArgument).getVariableName() : "");
            collectTypeArguments(nestedTypeArguments, metadata, collapseDeclaredTypeVariables);
        }
    }

    private static List<ClassElement> resolveTypeArguments(ClassElement beanType, boolean collapseDeclaredTypeVariables) {
        List<ClassElement> typeArguments = new ArrayList<>(beanType.getBoundGenericTypes());
        if (typeArguments.isEmpty() && !beanType.getTypeArguments().isEmpty()) {
            typeArguments.addAll(beanType.getTypeArguments().values());
        }
        if (collapseDeclaredTypeVariables
                && !typeArguments.isEmpty()
                && typeArguments.stream().allMatch(typeArgument -> isDeclaredTypeVariable(beanType, typeArgument))) {
            return List.of();
        }
        if (!typeArguments.isEmpty() && typeArguments.stream().allMatch(CdiUtil::isRawTypeVariable)) {
            return List.of();
        }
        if (beanType.isRawType()
                && (typeArguments.isEmpty() || typeArguments.stream().allMatch(typeArgument -> isDeclaredTypeVariable(beanType, typeArgument)))) {
            return List.of();
        }
        if (typeArguments.isEmpty() && !beanType.getDeclaredGenericPlaceholders().isEmpty()) {
            for (GenericPlaceholderElement placeholder : beanType.getDeclaredGenericPlaceholders()) {
                typeArguments.add(placeholder.getBounds().get(0));
            }
        }
        if (typeArguments.isEmpty()) {
            return List.of();
        }
        return typeArguments;
    }

    private static boolean isRawTypeVariable(ClassElement typeArgument) {
        return typeArgument instanceof GenericPlaceholderElement && typeArgument.isRawType();
    }

    private static boolean isDeclaredTypeVariable(ClassElement beanType, ClassElement typeArgument) {
        if (!(typeArgument instanceof GenericPlaceholderElement)) {
            return false;
        }
        GenericPlaceholderElement placeholder = (GenericPlaceholderElement) typeArgument;
        Map<String, ClassElement> typeArguments = beanType.getTypeArguments();
        ClassElement namedTypeArgument = typeArguments.get(placeholder.getVariableName());
        if (namedTypeArgument instanceof GenericPlaceholderElement
                && ((GenericPlaceholderElement) namedTypeArgument).getVariableName().equals(placeholder.getVariableName())) {
            return true;
        }
        return beanType.getDeclaredGenericPlaceholders().stream()
                .anyMatch(declaredPlaceholder -> declaredPlaceholder.getVariableName().equals(placeholder.getVariableName()));
    }

    private static boolean[] toBooleanArray(List<Boolean> values) {
        boolean[] array = new boolean[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    private static final class BeanTypeArguments {
        private final List<AnnotationClassValue<?>> values = new ArrayList<>();
        private final List<Integer> counts = new ArrayList<>();
        private final List<Boolean> typeVariables = new ArrayList<>();
        private final List<Boolean> wildcards = new ArrayList<>();
        private final List<Integer> lowerBoundCounts = new ArrayList<>();
        private final List<String> typeVariableNames = new ArrayList<>();
    }

    public static boolean hasDependentScope(Element beanDefinition, VisitorContext context) {
        for (String annotationName : beanDefinition.getDeclaredAnnotationNames()) {
            if (annotationName.equals(jakarta.enterprise.context.Dependent.class.getName())) {
                continue;
            }
            boolean nonDependentScope = context.getClassElement(annotationName)
                    .map(annotation -> annotation.hasAnnotation(jakarta.enterprise.context.NormalScope.class.getName())
                            || annotation.hasAnnotation(jakarta.inject.Scope.class.getName())
                            || annotation.hasStereotype(jakarta.enterprise.context.NormalScope.class.getName())
                            || annotation.hasStereotype(AnnotationUtil.SCOPE)
                            || annotation.hasStereotype(jakarta.inject.Scope.class.getName()))
                    .orElse(false);
            if (nonDependentScope) {
                return false;
            }
        }
        return true;
    }

    public static boolean visitInjectPoint(VisitorContext context, TypedElement injectPoint) {
        boolean declaredAnyWithoutDefault = isDeclaredAnyWithoutDefault(injectPoint);
        if (needsDefaultInjectPointQualifier(injectPoint.getAnnotationMetadata())) {
            injectPoint.annotate(Default.class);
        }
        visitQualifierDefaults(context, injectPoint);
        visitRequiredType(injectPoint);
        ClassElement injectPointType = resolveInjectPointType(injectPoint);
        if (CdiUtil.validateInjectedType(context, injectPointType, injectPoint)) {
            return true;
        }
        if (CdiUtil.validateInjectionPointMetadata(context, injectPointType, injectPoint)) {
            return true;
        }
        return validateResolvableInjectionPoint(context, injectPointType, injectPoint, declaredAnyWithoutDefault);
    }

    public static void visitEventType(TypedElement eventType) {
        ClassElement requiredType = resolveInjectPointType(eventType);
        visitEventType(eventType, requiredType, false);
    }

    public static void visitEventType(TypedElement eventType, ClassElement owningType, MethodElement methodElement) {
        ClassElement requiredType = resolveInjectPointType(eventType);
        if (requiredType == null) {
            return;
        }
        ClassElement resolvedType = resolveTypeVariables(requiredType, resolveTypeArguments(owningType, methodElement));
        if (resolvedType == requiredType
                && (requiredType.isArray()
                || requiredType.isRawType()
                || !containsWildcard(requiredType))) {
            return;
        }
        visitEventType(eventType, resolvedType, resolvedType != requiredType);
    }

    private static Map<String, ClassElement> resolveTypeArguments(ClassElement owningType, MethodElement methodElement) {
        Map<String, ClassElement> typeArguments = new LinkedHashMap<>();
        ClassElement declaringType = methodElement.getDeclaringType();
        mergeTypeArguments(typeArguments, methodElement.getOwningType().getTypeArguments(declaringType.getName()));
        mergeTypeArguments(typeArguments, owningType.getTypeArguments(declaringType.getName()));
        mergeTypeArguments(typeArguments, declaringType.getTypeArguments());
        return typeArguments;
    }

    private static void mergeTypeArguments(Map<String, ClassElement> target, Map<String, ClassElement> source) {
        for (Map.Entry<String, ClassElement> entry : source.entrySet()) {
            ClassElement typeArgument = unwrapResolvedTypeArgument(entry.getValue());
            ClassElement existing = target.get(entry.getKey());
            if (existing == null || (!isConcreteTypeArgument(existing) && isConcreteTypeArgument(typeArgument))) {
                target.put(entry.getKey(), typeArgument);
            }
        }
    }

    private static ClassElement unwrapResolvedTypeArgument(ClassElement typeArgument) {
        if (typeArgument instanceof GenericPlaceholderElement) {
            return ((GenericPlaceholderElement) typeArgument).getResolved().orElse(typeArgument);
        }
        return typeArgument;
    }

    private static boolean isConcreteTypeArgument(ClassElement typeArgument) {
        return !(typeArgument instanceof GenericPlaceholderElement)
                && !Object.class.getName().equals(typeArgument.getName());
    }

    private static void visitEventType(TypedElement eventType, ClassElement requiredType, boolean force) {
        if (requiredType == null
                || requiredType.isArray()
                || requiredType.isRawType()
                || (!force && !containsWildcard(requiredType))) {
            return;
        }
        visitRequiredType(eventType, requiredType);
    }

    private static void visitRequiredType(TypedElement injectPoint) {
        ClassElement requiredType = resolveInjectPointType(injectPoint);
        visitRequiredType(injectPoint, requiredType);
    }

    private static void visitRequiredType(TypedElement injectPoint, ClassElement requiredType) {
        if (requiredType == null || !hasTypeArguments(requiredType, false)) {
            return;
        }
        BeanTypeArguments typeArguments = toTypeArguments(requiredType, false);
        injectPoint.annotate(AnnotationValue.builder(org.eclipse.odi.cdi.processor.AnnotationUtil.ANN_BEAN_TYPE)
                .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(requiredType.getName()))
                .member("arguments", typeArguments.values.toArray(new AnnotationClassValue<?>[0]))
                .member("argumentCounts", typeArguments.counts.stream().mapToInt(Integer::intValue).toArray())
                .member("typeVariables", toBooleanArray(typeArguments.typeVariables))
                .member("wildcards", toBooleanArray(typeArguments.wildcards))
                .member("lowerBoundCounts", typeArguments.lowerBoundCounts.stream().mapToInt(Integer::intValue).toArray())
                .member("typeVariableNames", typeArguments.typeVariableNames.toArray(String[]::new))
                .build());
    }

    private static ClassElement resolveTypeVariables(ClassElement type, Map<String, ClassElement> typeArguments) {
        if (typeArguments.isEmpty()) {
            return type;
        }
        if (type instanceof GenericPlaceholderElement) {
            GenericPlaceholderElement placeholder = (GenericPlaceholderElement) type;
            ClassElement resolved = typeArguments.get(placeholder.getVariableName());
            if (resolved instanceof GenericPlaceholderElement) {
                resolved = ((GenericPlaceholderElement) resolved).getResolved().orElse(resolved);
            }
            if (resolved == null) {
                resolved = placeholder.getResolved().orElse(null);
            }
            return resolved == null ? type : resolved;
        }
        List<? extends ClassElement> boundTypes = type.getBoundGenericTypes();
        if (boundTypes.isEmpty()) {
            return type;
        }
        List<ClassElement> resolvedBoundTypes = new ArrayList<>(boundTypes.size());
        boolean changed = false;
        for (ClassElement boundType : boundTypes) {
            ClassElement resolvedBoundType = resolveTypeVariables(boundType, typeArguments);
            resolvedBoundTypes.add(resolvedBoundType);
            changed |= resolvedBoundType != boundType;
        }
        return changed ? type.withTypeArguments(resolvedBoundTypes) : type;
    }

    private static ClassElement resolveInjectPointType(TypedElement injectPoint) {
        if (injectPoint instanceof FieldElement) {
            return ((FieldElement) injectPoint).getGenericField();
        }
        return injectPoint.getGenericType();
    }

    public static void visitQualifierDefaults(VisitorContext context, Element element) {
        AnnotationMetadata annotationMetadata = element.getAnnotationMetadata();
        Set<String> qualifierNames = new LinkedHashSet<>(annotationMetadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER));
        for (String annotationName : annotationMetadata.getAnnotationNames()) {
            if (isQualifierAnnotation(context, annotationName)) {
                qualifierNames.add(annotationName);
            }
        }
        for (String annotationName : qualifierNames) {
            if (AnnotationUtil.NAMED.equals(annotationName)) {
                continue;
            }
            if (!hasRuntimeRetention(context, annotationName)) {
                element.removeAnnotation(annotationName);
                continue;
            }
            AnnotationValue<Annotation> annotationValue = annotationMetadata.getAnnotation(annotationName);
            if (annotationValue == null) {
                continue;
            }
            Map<CharSequence, Object> defaultValues = context.getAnnotationDefaultValues(annotationName);
            if (defaultValues == null || defaultValues.isEmpty()) {
                continue;
            }
            Set<String> nonBindingMembers = nonBindingMembers(context, annotationMetadata, annotationName);
            Map<CharSequence, Object> mergedValues = new LinkedHashMap<>();
            for (Map.Entry<CharSequence, Object> entry : defaultValues.entrySet()) {
                if (!nonBindingMembers.contains(entry.getKey().toString())) {
                    mergedValues.put(entry.getKey(), entry.getValue());
                }
            }
            mergedValues.putAll(annotationValue.getValues());
            if (mergedValues.equals(annotationValue.getValues())) {
                continue;
            }
            element.annotate(AnnotationValue.builder(annotationValue, annotationValue.getRetentionPolicy())
                    .members(mergedValues)
                    .build());
        }
    }

    private static Set<String> nonBindingMembers(VisitorContext context, AnnotationMetadata annotationMetadata, String annotationName) {
        Set<String> nonBindingMembers = new LinkedHashSet<>(List.of(
                annotationMetadata.stringValues(AnnotationUtil.QUALIFIER, AnnotationUtil.NON_BINDING_ATTRIBUTE)
        ));
        context.getClassElement(annotationName).ifPresent(annotation ->
                annotation.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())
                        .stream()
                        .filter(method -> method.hasAnnotation(Nonbinding.class))
                        .map(MethodElement::getName)
                        .forEach(nonBindingMembers::add)
        );
        return nonBindingMembers;
    }

    private static boolean hasRuntimeRetention(VisitorContext context, String annotationName) {
        return context.getAnnotationRetentionPolicy(annotationName) == RetentionPolicy.RUNTIME;
    }

    private static boolean isQualifierAnnotation(VisitorContext context, String annotationName) {
        return context.getClassElement(annotationName)
                .map(annotation -> annotation.hasAnnotation(AnnotationUtil.QUALIFIER)
                        || annotation.hasStereotype(AnnotationUtil.QUALIFIER))
                .orElse(false);
    }

    public static boolean validateInjectedType(VisitorContext context, ClassElement classElement, Element owningElement) {
        if (isTypeVariable(classElement) && !isDeclaredByVetoedType(owningElement)) {
            context.fail("Injection point type must not be a type variable", owningElement);
            return true;
        }
        if (EventMetadata.class.getName().equals(classElement.getName()) && !isObserverMethodParameter(owningElement)) {
            context.fail("EventMetadata may only be injected into observer method parameters", owningElement);
            return true;
        }
        if (Bean.class.getName().equals(classElement.getName())
                && validateBeanMetadataTypeParameter(context, classElement, owningElement)) {
            return true;
        }
        if (jakarta.enterprise.inject.spi.Interceptor.class.getName().equals(classElement.getName())
                && validateInterceptorMetadataInjectionPoint(context, classElement, owningElement)) {
            return true;
        }
        if (classElement.getName().equals(Instance.class.getName()) && isNoGenericType(classElement)) {
            context.fail("jakarta.enterprise.inject.Instance must have a required type parameter specified", owningElement);
            return true;
        }
        if (classElement.getName().equals(Instance.class.getName()) && hasWildcardInstanceRequiredType(classElement)) {
            context.fail("jakarta.enterprise.inject.Instance required type must not contain wildcard type parameters", owningElement);
            return true;
        }
        if (classElement.getName().equals(Event.class.getName()) && isNoGenericType(classElement)) {
            context.fail("jakarta.enterprise.event.Event must have a required type parameter specified", owningElement);
            return true;
        }
        if (classElement.getName().equals(Event.class.getName()) && hasWildcardRequiredType(classElement)) {
            context.fail("jakarta.enterprise.event.Event required type must not contain wildcard type parameters", owningElement);
            return true;
        }
        return false;
    }

    private static boolean hasWildcardRequiredType(ClassElement classElement) {
        List<ClassElement> typeArguments = resolvedTypeArguments(classElement);
        if (typeArguments.isEmpty()) {
            return false;
        }
        ClassElement requiredType = typeArguments.get(0);
        return requiredType instanceof WildcardElement || requiredType.isWildcard();
    }

    private static boolean hasWildcardInstanceRequiredType(ClassElement classElement) {
        List<ClassElement> typeArguments = resolvedTypeArguments(classElement);
        if (typeArguments.isEmpty()) {
            return false;
        }
        return containsWildcard(typeArguments.get(0));
    }

    private static boolean validateBeanMetadataTypeParameter(VisitorContext context,
                                                             ClassElement beanMetadataType,
                                                             Element owningElement) {
        if (owningElement.hasAnnotation(Intercepted.class)) {
            return validateInterceptedBeanMetadataInjectionPoint(context, beanMetadataType, owningElement);
        }
        List<ClassElement> typeArguments = resolvedTypeArguments(beanMetadataType);
        if (typeArguments.isEmpty()) {
            return false;
        }
        ClassElement metadataBeanType = typeArguments.get(0);
        if (metadataBeanType instanceof WildcardElement || metadataBeanType.isWildcard() || isObjectType(metadataBeanType)) {
            return false;
        }
        ClassElement contextualBeanType = resolveContextualBeanType(owningElement);
        if (contextualBeanType != null && !metadataBeanType.isAssignable(contextualBeanType)) {
            context.fail("Bean metadata type parameter must be assignable from the declaring bean type", owningElement);
            return true;
        }
        return false;
    }

    private static boolean validateInterceptedBeanMetadataInjectionPoint(VisitorContext context,
                                                                        ClassElement beanMetadataType,
                                                                        Element owningElement) {
        ClassElement owningType = resolveOwningType(owningElement);
        if (!isInterceptorBean(owningType)) {
            context.fail("@Intercepted Bean metadata may only be injected into interceptor beans", owningElement);
            return true;
        }
        List<ClassElement> typeArguments = resolvedTypeArguments(beanMetadataType);
        if (!typeArguments.isEmpty() && !isUnboundedWildcard(typeArguments.get(0))) {
            context.fail("@Intercepted Bean metadata must use Bean<?>", owningElement);
            return true;
        }
        return false;
    }

    private static boolean validateInterceptorMetadataInjectionPoint(VisitorContext context,
                                                                    ClassElement interceptorMetadataType,
                                                                    Element owningElement) {
        ClassElement owningType = resolveOwningType(owningElement);
        if (!isInterceptorBean(owningType)) {
            context.fail("Interceptor metadata may only be injected into interceptor beans", owningElement);
            return true;
        }
        List<ClassElement> typeArguments = resolvedTypeArguments(interceptorMetadataType);
        if (typeArguments.isEmpty()) {
            return false;
        }
        ClassElement metadataBeanType = typeArguments.get(0);
        if (metadataBeanType instanceof WildcardElement || metadataBeanType.isWildcard() || isObjectType(metadataBeanType)) {
            return false;
        }
        if (!metadataBeanType.isAssignable(owningType)) {
            context.fail("Interceptor metadata type parameter must be assignable from the interceptor bean type", owningElement);
            return true;
        }
        return false;
    }

    private static boolean isInterceptorBean(ClassElement classElement) {
        return classElement != null
                && (classElement.hasAnnotation(Interceptor.class)
                || classElement.hasStereotype(Interceptor.class));
    }

    private static boolean isUnboundedWildcard(ClassElement typeArgument) {
        if (!(typeArgument instanceof WildcardElement) && !typeArgument.isWildcard()) {
            return false;
        }
        if (typeArgument instanceof WildcardElement) {
            WildcardElement wildcard = (WildcardElement) typeArgument;
            List<? extends ClassElement> upperBounds = wildcard.getUpperBounds();
            return wildcard.getLowerBounds().isEmpty()
                    && (upperBounds.isEmpty() || (upperBounds.size() == 1 && isObjectType(upperBounds.get(0))));
        }
        return isObjectType(typeArgument);
    }

    private static ClassElement resolveContextualBeanType(Element owningElement) {
        if (owningElement instanceof FieldElement) {
            return ((FieldElement) owningElement).getOwningType();
        }
        if (owningElement instanceof ParameterElement) {
            try {
                MethodElement methodElement = ((ParameterElement) owningElement).getMethodElement();
                if (methodElement.hasDeclaredAnnotation(Produces.class)) {
                    return methodElement.getGenericReturnType();
                }
                for (ParameterElement parameter : methodElement.getParameters()) {
                    if (parameter.hasDeclaredAnnotation(Disposes.class)) {
                        return parameter.getGenericType();
                    }
                }
                return methodElement.getOwningType();
            } catch (IllegalStateException e) {
                return null;
            }
        }
        if (owningElement instanceof MemberElement) {
            return ((MemberElement) owningElement).getOwningType();
        }
        return null;
    }

    private static ClassElement resolveOwningType(Element owningElement) {
        if (owningElement instanceof FieldElement) {
            return ((FieldElement) owningElement).getOwningType();
        }
        if (owningElement instanceof ParameterElement) {
            try {
                return ((ParameterElement) owningElement).getMethodElement().getOwningType();
            } catch (IllegalStateException e) {
                return null;
            }
        }
        if (owningElement instanceof MemberElement) {
            return ((MemberElement) owningElement).getOwningType();
        }
        if (owningElement instanceof ClassElement) {
            return (ClassElement) owningElement;
        }
        return null;
    }

    private static boolean isObserverMethodParameter(Element element) {
        if (!(element instanceof ParameterElement)) {
            return false;
        }
        try {
            for (ParameterElement parameter : ((ParameterElement) element).getMethodElement().getParameters()) {
                if (parameter.hasDeclaredAnnotation(Observes.class) || parameter.hasDeclaredAnnotation(ObservesAsync.class)) {
                    return true;
                }
            }
        } catch (IllegalStateException e) {
            return false;
        }
        return false;
    }

    public static boolean validateGenericBeanScope(VisitorContext context, ClassElement classElement) {
        if (!classElement.getDeclaredGenericPlaceholders().isEmpty() && !hasDependentScope(classElement, context)) {
            context.fail("Managed bean classes with type parameters must have @Dependent scope", classElement);
            return true;
        }
        return false;
    }

    public static boolean validateMultipleScopes(VisitorContext context, Element element) {
        List<String> declaredScopes = new ArrayList<>();
        for (String annotationName : element.getDeclaredAnnotationNames()) {
            if (isScopeAnnotation(context, annotationName)) {
                declaredScopes.add(annotationName);
            }
        }
        if (element.hasAnnotation(Factory.class) || element.hasStereotype(Factory.class)) {
            declaredScopes.remove(Singleton.class.getName());
            declaredScopes.remove(jakarta.enterprise.context.Dependent.class.getName());
        }
        if (declaredScopes.size() > 1) {
            context.fail("Bean declares more than one scope: " + toAnnotationDescription(declaredScopes), element);
            return true;
        }
        return false;
    }

    static boolean isScopeAnnotation(VisitorContext context, String annotationName) {
        if (annotationName.equals(jakarta.enterprise.context.Dependent.class.getName())) {
            return true;
        }
        return context.getClassElement(annotationName)
                .map(annotation -> annotation.hasDeclaredAnnotation(jakarta.enterprise.context.NormalScope.class.getName())
                        || annotation.hasDeclaredAnnotation(jakarta.inject.Scope.class.getName()))
                .orElse(false);
    }

    public static boolean validateNormalScopeFinalClass(VisitorContext context, ClassElement classElement) {
        if (classElement.hasStereotype(jakarta.enterprise.context.NormalScope.class.getName()) && classElement.isFinal()) {
            context.fail(
                    DEPLOYMENT_EXCEPTION_MARKER + "Managed bean classes that are final must have @Dependent scope",
                    classElement
            );
            return true;
        }
        return false;
    }

    public static boolean markUnproxyableNormalScopedBean(ClassElement classElement) {
        if (!classElement.hasStereotype(jakarta.enterprise.context.NormalScope.class.getName())) {
            return false;
        }
        if (classElement.isFinal()
                || !hasNonPrivateNoArgsConstructor(classElement)
                || hasNonPrivateFinalMethod(classElement)) {
            classElement.annotate(ODI_UNPROXYABLE_BEAN);
            return true;
        }
        return false;
    }

    public static void removeNormalScopeAnnotations(VisitorContext context, ClassElement classElement) {
        for (String annotationName : List.copyOf(classElement.getDeclaredAnnotationNames())) {
            boolean normalScope = context.getClassElement(annotationName)
                    .map(annotation -> annotation.hasDeclaredAnnotation(jakarta.enterprise.context.NormalScope.class.getName())
                            || annotation.hasStereotype(jakarta.enterprise.context.NormalScope.class.getName()))
                    .orElse(false);
            if (normalScope) {
                classElement.removeAnnotation(annotationName);
            }
        }
    }

    public static boolean validateNormalScopeConstructor(VisitorContext context, ClassElement classElement) {
        if (!classElement.hasStereotype(jakarta.enterprise.context.NormalScope.class.getName())) {
            return false;
        }
        if (!hasNonPrivateNoArgsConstructor(classElement)) {
            context.fail(
                    DEPLOYMENT_EXCEPTION_MARKER
                            + "Managed bean classes with normal scope must declare a non-private no-arguments constructor",
                    classElement
            );
            return true;
        }
        return false;
    }

    public static boolean validateInterceptedBeanConstructor(VisitorContext context, ClassElement classElement) {
        if (!isBeanClass(classElement)) {
            context.fail(
                    DEPLOYMENT_EXCEPTION_MARKER
                            + "Intercepted bean classes must declare a non-private no-arguments constructor or an @Inject constructor",
                    classElement
            );
            return true;
        }
        return false;
    }

    public static boolean validateInterceptorDependentScope(VisitorContext context, ClassElement classElement) {
        if (classElement.hasStereotype(Interceptor.class) && !hasDependentScope(classElement, context)) {
            context.fail("Interceptors must have @Dependent scope", classElement);
            return true;
        }
        return false;
    }

    public static boolean validateInterceptedBeanProxyability(VisitorContext context,
                                                              ClassElement classElement,
                                                              List<MethodElement> interceptedMethods) {
        if (classElement.isFinal()) {
            context.fail(
                    DEPLOYMENT_EXCEPTION_MARKER + "Intercepted bean classes must not be final",
                    classElement
            );
            return true;
        }
        for (MethodElement method : interceptedMethods) {
            if (method.isFinal() && !method.isPrivate() && !method.isStatic() && !method.isSynthetic()) {
                context.fail(
                        DEPLOYMENT_EXCEPTION_MARKER + "Intercepted bean methods must not be non-private final methods",
                        method
                );
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonPrivateNoArgsConstructor(ClassElement classElement) {
        return classElement.getAccessibleConstructors()
                .stream()
                .anyMatch(constructor -> constructor.getParameters().length == 0);
    }

    private static boolean hasNonPrivateFinalMethod(ClassElement classElement) {
        for (MethodElement method : classElement.getEnclosedElements(ElementQuery.ALL_METHODS
                .onlyInstance()
                .includeOverriddenMethods()
                .includeHiddenElements())) {
            if (method.isFinal() && !method.isPrivate() && !method.isSynthetic()) {
                return true;
            }
        }
        return false;
    }

    public static boolean validateNormalScopePublicFields(VisitorContext context, ClassElement classElement) {
        if (!classElement.hasStereotype(jakarta.enterprise.context.NormalScope.class.getName())) {
            return false;
        }
        for (FieldElement field : classElement.getEnclosedElements(ElementQuery.ALL_FIELDS)) {
            if (field.isPublic() && !field.isStatic()) {
                context.fail("Managed bean classes with non-static public fields must have @Dependent scope", field);
                return true;
            }
        }
        return false;
    }

    public static boolean validateNormalScopeFinalMethods(VisitorContext context, ClassElement classElement) {
        if (!classElement.hasStereotype(jakarta.enterprise.context.NormalScope.class.getName())) {
            return false;
        }
        for (MethodElement method : classElement.getEnclosedElements(ElementQuery.ALL_METHODS
                .onlyInstance()
                .includeOverriddenMethods()
                .includeHiddenElements())) {
            if (method.isFinal() && !method.isPrivate() && !method.isSynthetic()) {
                context.fail(
                        DEPLOYMENT_EXCEPTION_MARKER + "Managed bean classes with non-private, non-static final methods must have @Dependent scope",
                        method
                );
                return true;
            }
        }
        return false;
    }

    public static boolean validateProducerType(VisitorContext context, ClassElement producerType, Element producerElement) {
        if (containsWildcard(producerType)) {
            context.fail("Producer type must not contain wildcard type parameters", producerElement);
            return true;
        }
        if (isTypeVariable(producerType) || isArrayWithTypeVariableComponent(producerType)) {
            context.fail("Producer type must not be a type variable", producerElement);
            return true;
        }
        if (!hasDependentScope(producerElement, context) && containsTypeVariable(producerType)) {
            context.fail("Producer types with type variables must have @Dependent scope", producerElement);
            return true;
        }
        if (!hasDependentScope(producerElement, context) && (producerType.isPrimitive() || producerType.isArray())) {
            context.fail(
                    DEPLOYMENT_EXCEPTION_MARKER + "Producer types that are primitive or arrays must have @Dependent scope",
                    producerElement
            );
            return true;
        }
        return false;
    }

    public static boolean validateDisposerInjectionPointMetadata(VisitorContext context, ParameterElement parameterElement) {
        if (!parameterElement.hasDeclaredAnnotation(Disposes.class)
                && InjectionPoint.class.getName().equals(parameterElement.getGenericType().getName())) {
            context.fail("Disposer methods cannot inject InjectionPoint metadata", parameterElement);
            return true;
        }
        return false;
    }

    private static boolean validateInjectionPointMetadata(VisitorContext context,
                                                          ClassElement injectPointType,
                                                          TypedElement injectPoint) {
        if (!InjectionPoint.class.getName().equals(injectPointType.getName())
                || !injectPoint.hasAnnotation(Default.class)
                || !hasOnlyDefaultInjectionPointQualifier(context, injectPoint)) {
            return false;
        }
        Element declaringBean = resolveDeclaringBean(injectPoint);
        if (declaringBean != null && !hasDependentScope(declaringBean, context)) {
            context.fail("InjectionPoint metadata may only be injected into @Dependent beans", injectPoint);
            return true;
        }
        return false;
    }

    private static Element resolveDeclaringBean(TypedElement injectPoint) {
        if (injectPoint instanceof FieldElement) {
            return ((FieldElement) injectPoint).getOwningType();
        }
        if (injectPoint instanceof ParameterElement) {
            try {
                MethodElement methodElement = ((ParameterElement) injectPoint).getMethodElement();
                if (methodElement.hasDeclaredAnnotation(Produces.class)) {
                    return methodElement;
                }
                return methodElement.getOwningType();
            } catch (IllegalStateException e) {
                return null;
            }
        }
        if (injectPoint instanceof MemberElement) {
            return ((MemberElement) injectPoint).getOwningType();
        }
        return null;
    }

    private static boolean validateResolvableInjectionPoint(VisitorContext context,
                                                            ClassElement injectPointType,
                                                            TypedElement injectPoint,
                                                            boolean declaredAnyWithoutDefault) {
        Set<String> configuredBeanClasses = configuredBeanClasses(context);
        boolean exhaustiveBeanClasses = !configuredBeanClasses.isEmpty();
        if (!exhaustiveBeanClasses
                && !isRawGenericType(injectPointType)
                && hasDisabledProducerCandidate(context, injectPointType, injectPoint, configuredBeanClasses)) {
            context.fail(DEPLOYMENT_EXCEPTION_MARKER
                    + "Unsatisfied dependency for injection point of type " + injectPointType.getName(), injectPoint);
            return true;
        }
        if (isBuildCompatibleExtensionDeployment(context)
                || isBuiltInInjectionPointType(injectPointType)
                || injectPointType.isPrimitive()
                || declaredAnyWithoutDefault
                || hasComplexTypeArguments(injectPointType)
                || (!exhaustiveBeanClasses && !isRawGenericType(injectPointType))
                || hasRequiredQualifierMembers(context, injectPoint)
                || hasKnownDefinitionError(injectPoint)) {
            return false;
        }
        Set<ResolvableCandidate> candidates = new LinkedHashSet<>();
        addManagedBeanCandidate(context, injectPointType, injectPoint, injectPointType, candidates);
        for (ClassElement candidate : candidateBeanClasses(context, injectPointType, injectPoint, configuredBeanClasses)) {
            addManagedBeanCandidate(context, injectPointType, injectPoint, candidate, candidates);
            addProducerCandidates(context, injectPointType, injectPoint, candidate, candidates);
        }
        boolean disabledProducerCandidate = hasDisabledProducerCandidate(context, injectPointType, injectPoint, configuredBeanClasses);
        if ((candidates.isEmpty() || isOnlyImplicitProducedTypeCandidate(injectPointType, candidates, disabledProducerCandidate))
                && (exhaustiveBeanClasses || disabledProducerCandidate)) {
            context.fail(DEPLOYMENT_EXCEPTION_MARKER
                    + "Unsatisfied dependency for injection point of type " + injectPointType.getName(), injectPoint);
            return true;
        }
        Set<ResolvableCandidate> nonReserveCandidates = candidates.stream()
                .filter(candidate -> !candidate.reserve)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!nonReserveCandidates.isEmpty() && nonReserveCandidates.size() < candidates.size()) {
            candidates = nonReserveCandidates;
        }
        candidates = selectPriorityCandidates(context, injectPointType, injectPoint, candidates);
        if (candidates.size() == 1 && candidates.iterator().next().unproxyable) {
            context.fail(DEPLOYMENT_EXCEPTION_MARKER
                    + "Unproxyable dependency for injection point of type " + injectPointType.getName(), injectPoint);
            return true;
        }
        if (candidates.size() > 1) {
            if (hasTypeArguments(injectPointType, false) || candidates.stream().anyMatch(ResolvableCandidate::requiresRuntimeResolution)) {
                return false;
            }
            context.fail(DEPLOYMENT_EXCEPTION_MARKER
                    + "Ambiguous dependency for injection point of type " + injectPointType.getName()
                    + ". Candidate beans: " + candidates.stream()
                    .map(ResolvableCandidate::description)
                    .sorted()
                    .collect(Collectors.joining(", ")), injectPoint);
            return true;
        }
        return false;
    }

    private static boolean isOnlyImplicitProducedTypeCandidate(ClassElement injectPointType,
                                                               Set<ResolvableCandidate> candidates,
                                                               boolean disabledProducerCandidate) {
        if (!disabledProducerCandidate || candidates.size() != 1) {
            return false;
        }
        ResolvableCandidate candidate = candidates.iterator().next();
        return !candidate.producer && candidate.description.equals(injectPointType.getName());
    }

    private static void addManagedBeanCandidate(VisitorContext context,
                                                ClassElement injectPointType,
                                                TypedElement injectPoint,
                                                ClassElement candidate,
                                                Set<ResolvableCandidate> candidates) {
        if (isResolvableBeanClass(context, candidate)
                && isBeanEnabled(context, candidate)
                && matchesRequiredQualifiers(context, injectPoint, candidate)
                && hasBeanTypeAssignableToRequiredType(injectPointType, candidate)) {
            candidates.add(new ResolvableCandidate(
                    candidate.getName(),
                    false,
                    requiresRuntimeResolution(candidate),
                    isUnproxyableNormalScopedBean(candidate),
                    isReserve(candidate),
                    isAlternative(candidate),
                    resolvePriority(context, candidate).orElse(0)));
        }
    }

    private static void addProducerCandidates(VisitorContext context,
                                              ClassElement injectPointType,
                                              TypedElement injectPoint,
                                              ClassElement candidate,
                                              Set<ResolvableCandidate> candidates) {
        if (!isProducerDeclaringBeanClass(context, candidate)) {
            return;
        }
        candidate.getEnclosedElements(ElementQuery.ALL_METHODS
                .onlyDeclared()
                .onlyConcrete()
                .annotated(annotationMetadata -> annotationMetadata.hasDeclaredAnnotation(Produces.class)))
                .forEach(method -> addProducerCandidate(
                        context,
                        injectPointType,
                        injectPoint,
                        method.getGenericReturnType(),
                        method,
                        candidates
                ));
        candidate.getEnclosedElements(ElementQuery.ALL_FIELDS
                .onlyDeclared()
                .annotated(annotationMetadata -> annotationMetadata.hasDeclaredAnnotation(Produces.class)))
                .forEach(field -> addProducerCandidate(
                        context,
                        injectPointType,
                        injectPoint,
                        field.getGenericField(),
                        field,
                        candidates
                ));
    }

    private static void addProducerCandidate(VisitorContext context,
                                             ClassElement injectPointType,
                                             TypedElement injectPoint,
                                             ClassElement producedType,
                                             MemberElement producer,
                                             Set<ResolvableCandidate> candidates) {
        if (matchesRequiredQualifiers(context, injectPoint, producer)
                && isProducerEnabled(context, producer)
                && hasBeanTypeAssignableToRequiredType(injectPointType, producedType)) {
            candidates.add(new ResolvableCandidate(
                    producer.getDeclaringType().getName() + "." + producer.getName(),
                    true,
                    requiresRuntimeResolution(producer) || requiresRuntimeResolution(producer.getDeclaringType()),
                    false,
                    isReserve(producer),
                    isAlternative(producer) || isAlternative(producer.getDeclaringType()),
                    resolvePriority(context, producer).orElseGet(() -> resolvePriority(context, producer.getDeclaringType()).orElse(0)))
            );
        }
    }

    private static Set<ResolvableCandidate> selectPriorityCandidates(VisitorContext context,
                                                                     ClassElement injectPointType,
                                                                     TypedElement injectPoint,
                                                                     Set<ResolvableCandidate> candidates) {
        Set<ResolvableCandidate> alternatives = candidates.stream()
                .filter(candidate -> candidate.alternative)
                .filter(candidate -> candidate.priority > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!alternatives.isEmpty()) {
            return selectHighestPriorityCandidates(context, injectPointType, injectPoint, alternatives);
        }
        if (candidates.stream().allMatch(candidate -> candidate.reserve)) {
            return selectHighestPriorityCandidates(context, injectPointType, injectPoint, candidates.stream()
                    .filter(candidate -> candidate.priority > 0)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        return candidates;
    }

    private static Set<ResolvableCandidate> selectHighestPriorityCandidates(VisitorContext context,
                                                                           ClassElement injectPointType,
                                                                           TypedElement injectPoint,
                                                                           Set<ResolvableCandidate> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        int highestPriority = candidates.stream()
                .mapToInt(candidate -> candidate.priority)
                .max()
                .orElse(0);
        Set<ResolvableCandidate> highestPriorityCandidates = candidates.stream()
                .filter(candidate -> candidate.priority == highestPriority)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (highestPriorityCandidates.size() > 1) {
            context.fail(DEPLOYMENT_EXCEPTION_MARKER
                    + "Ambiguous dependency for injection point of type " + injectPointType.getName()
                    + ". Candidate beans: " + highestPriorityCandidates.stream()
                    .map(ResolvableCandidate::description)
                    .sorted()
                    .collect(Collectors.joining(", ")), injectPoint);
        }
        return highestPriorityCandidates;
    }

    private static Collection<ClassElement> candidateBeanClasses(VisitorContext context,
                                                                 ClassElement injectPointType,
                                                                 TypedElement injectPoint,
                                                                 Set<String> configuredBeanClasses) {
        if (!configuredBeanClasses.isEmpty()) {
            List<ClassElement> candidates = new ArrayList<>(configuredBeanClasses.size());
            for (String configuredBeanClass : configuredBeanClasses) {
                context.getClassElement(configuredBeanClass).ifPresent(candidates::add);
            }
            return candidates;
        }
        List<ClassElement> candidates = new ArrayList<>();
        for (String packageName : candidatePackages(injectPointType, injectPoint)) {
            candidates.addAll(List.of(context.getClassElements(packageName, "*")));
        }
        return candidates;
    }

    private static Set<String> configuredBeanClasses(VisitorContext context) {
        String classNames = context.getOptions().get(BEAN_CLASSES_OPTION);
        if (classNames == null || classNames.isBlank()) {
            classNames = System.getProperty(BEAN_CLASSES_OPTION);
        }
        if (classNames == null || classNames.isBlank()) {
            return Set.of();
        }
        Set<String> beanClasses = new LinkedHashSet<>();
        for (String className : classNames.split(",")) {
            String trimmedClassName = className.trim();
            if (!trimmedClassName.isEmpty()) {
                beanClasses.add(trimmedClassName);
            }
        }
        return beanClasses;
    }

    private static Set<String> candidatePackages(ClassElement injectPointType, TypedElement injectPoint) {
        Set<String> packages = new LinkedHashSet<>();
        packages.add(injectPointType.getPackageName());
        if (injectPoint instanceof MemberElement) {
            packages.add(((MemberElement) injectPoint).getOwningType().getPackageName());
            packages.add(((MemberElement) injectPoint).getDeclaringType().getPackageName());
        }
        return packages;
    }

    private static boolean isBuiltInInjectionPointType(ClassElement injectPointType) {
        String typeName = injectPointType.getName();
        return typeName.equals(Object.class.getName())
                || typeName.equals(Bean.class.getName())
                || typeName.equals(Event.class.getName())
                || typeName.equals(EventMetadata.class.getName())
                || typeName.equals(InjectionPoint.class.getName())
                || typeName.equals(Instance.class.getName())
                || typeName.equals(jakarta.enterprise.context.control.RequestContextController.class.getName())
                || typeName.equals(jakarta.enterprise.inject.spi.BeanContainer.class.getName())
                || typeName.equals(jakarta.enterprise.inject.spi.BeanManager.class.getName())
                || typeName.equals(jakarta.enterprise.inject.spi.Interceptor.class.getName())
                || typeName.equals(jakarta.inject.Provider.class.getName());
    }

    private static boolean hasComplexTypeArguments(ClassElement injectPointType) {
        return hasTypeArguments(injectPointType, false)
                && (containsTypeVariable(injectPointType) || containsWildcard(injectPointType));
    }

    private static boolean isBuildCompatibleExtensionDeployment(VisitorContext context) {
        return Boolean.parseBoolean(context.getOptions().get(BUILD_COMPATIBLE_EXTENSIONS_OPTION));
    }

    private static boolean isDeclaredAnyWithoutDefault(Element injectPoint) {
        return hasDeclaredAnyAnnotation(injectPoint)
                && !injectPoint.hasDeclaredAnnotation(Default.class);
    }

    private static boolean hasAnyAnnotation(Element element) {
        return element.hasAnnotation(Any.class)
                || element.hasAnnotation(io.micronaut.context.annotation.Any.class);
    }

    private static boolean hasDeclaredAnyAnnotation(Element element) {
        return element.hasDeclaredAnnotation(Any.class)
                || element.hasDeclaredAnnotation(io.micronaut.context.annotation.Any.class);
    }

    private static boolean isProducerDeclaringBeanClass(VisitorContext context, ClassElement candidate) {
        return !candidate.hasStereotype(Interceptor.class)
                && !candidate.hasAnnotation(io.micronaut.core.annotation.Vetoed.class)
                && !candidate.hasAnnotation(jakarta.enterprise.inject.Vetoed.class)
                && hasResolvableBeanDefiningAnnotation(candidate)
                && isBeanClass(candidate);
    }

    private static boolean hasResolvableBeanDefiningAnnotation(ClassElement candidate) {
        return candidate.hasAnnotation(org.eclipse.odi.cdi.processor.AnnotationUtil.ANN_ODI_BEAN_DEFINITION)
                || hasDeclaredBeanDefiningAnnotation(candidate);
    }

    private static boolean hasDeclaredBeanDefiningAnnotation(ClassElement candidate) {
        return candidate.hasDeclaredAnnotation(Factory.class)
                || candidate.hasDeclaredAnnotation(jakarta.enterprise.context.Dependent.class)
                || candidate.hasDeclaredStereotype(jakarta.enterprise.context.NormalScope.class)
                || candidate.hasDeclaredStereotype(Stereotype.class)
                || candidate.hasDeclaredStereotype(Interceptor.class)
                || candidate.hasDeclaredStereotype(io.micronaut.context.annotation.Bean.class)
                || candidate.hasDeclaredStereotype(io.micronaut.core.annotation.AnnotationUtil.SCOPE);
    }

    private static boolean isProducerEnabled(VisitorContext context, MemberElement producer) {
        return isBeanEnabled(context, producer)
                && (isBeanEnabled(context, producer.getDeclaringType()) || hasPriority(producer));
    }

    private static boolean hasDisabledProducerCandidate(VisitorContext context,
                                                        ClassElement injectPointType,
                                                        TypedElement injectPoint,
                                                        Set<String> configuredBeanClasses) {
        for (ClassElement candidate : candidateBeanClasses(context, injectPointType, injectPoint, configuredBeanClasses)) {
            if (!isProducerDeclaringBeanClass(context, candidate)) {
                continue;
            }
            if (hasDisabledProducerCandidate(context, injectPointType, injectPoint, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDisabledProducerCandidate(VisitorContext context,
                                                        ClassElement injectPointType,
                                                        TypedElement injectPoint,
                                                        ClassElement candidate) {
        for (MethodElement method : candidate.getEnclosedElements(ElementQuery.ALL_METHODS
                .onlyDeclared()
                .onlyConcrete()
                .annotated(annotationMetadata -> annotationMetadata.hasDeclaredAnnotation(Produces.class)))) {
            if (matchesDisabledProducer(context, injectPointType, injectPoint, method.getGenericReturnType(), method)) {
                return true;
            }
        }
        for (FieldElement field : candidate.getEnclosedElements(ElementQuery.ALL_FIELDS
                .onlyDeclared()
                .annotated(annotationMetadata -> annotationMetadata.hasDeclaredAnnotation(Produces.class)))) {
            if (matchesDisabledProducer(context, injectPointType, injectPoint, field.getGenericField(), field)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesDisabledProducer(VisitorContext context,
                                                   ClassElement injectPointType,
                                                   TypedElement injectPoint,
                                                   ClassElement producedType,
                                                   MemberElement producer) {
        return matchesRequiredQualifiers(context, injectPoint, producer)
                && !isProducerEnabled(context, producer)
                && hasBeanTypeAssignableToRequiredType(injectPointType, producedType);
    }

    public static boolean isBeanEnabled(VisitorContext context, Element element) {
        if (isReserve(element) && !hasPriority(element)) {
            return false;
        }
        if (!isAlternative(element)) {
            return true;
        }
        if (hasPriority(element)) {
            return true;
        }
        String beanClassName = selectedAlternativeBeanClassName(element);
        if (beanClassName == null) {
            return false;
        }
        String selectedAlternatives = context.getOptions().get(SELECTED_ALTERNATIVES_OPTION);
        if (selectedAlternatives == null || selectedAlternatives.isBlank()) {
            selectedAlternatives = System.getProperty(SELECTED_ALTERNATIVES_OPTION);
        }
        if (selectedAlternatives == null || selectedAlternatives.isBlank()) {
            return false;
        }
        AntPathMatcher matcher = new AntPathMatcher();
        for (String selectedAlternative : selectedAlternatives.split(",")) {
            String pattern = selectedAlternative.trim();
            if (!pattern.isEmpty() && matcher.matches(pattern, beanClassName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAlternative(Element element) {
        return element.hasStereotype(jakarta.enterprise.inject.Alternative.class)
                || element.hasAnnotation(jakarta.enterprise.inject.Alternative.class);
    }

    public static boolean hasPriority(Element element) {
        return element.hasStereotype(Priority.class) || element.hasAnnotation(Priority.class);
    }

    public static boolean isReserve(Element element) {
        if (element instanceof MemberElement) {
            return element.hasDeclaredStereotype(Reserve.class) || element.hasDeclaredAnnotation(Reserve.class);
        }
        return element.hasStereotype(Reserve.class) || element.hasAnnotation(Reserve.class);
    }

    private static String selectedAlternativeBeanClassName(Element element) {
        if (element instanceof ClassElement) {
            return ((ClassElement) element).getName();
        }
        if (element instanceof MemberElement) {
            return ((MemberElement) element).getDeclaringType().getName();
        }
        return null;
    }

    private static boolean requiresRuntimeResolution(Element element) {
        return isAlternative(element) || hasPriority(element) || isReserve(element);
    }

    private static boolean matchesRequiredQualifiers(VisitorContext context, Element injectPoint, Element candidate) {
        Set<String> requiredQualifiers = bindingQualifierNames(context, injectPoint);
        if (requiredQualifiers.isEmpty()) {
            if (hasAnyAnnotation(injectPoint) && !injectPoint.hasAnnotation(Default.class)) {
                return true;
            }
            Set<String> candidateQualifiers = bindingQualifierNames(context, candidate);
            return candidate.hasAnnotation(Default.class) || candidateQualifiers.isEmpty();
        }
        return bindingQualifierNames(context, candidate).containsAll(requiredQualifiers);
    }

    private static boolean hasKnownDefinitionError(TypedElement injectPoint) {
        if (!(injectPoint instanceof ParameterElement)) {
            return false;
        }
        try {
            MethodElement methodElement = ((ParameterElement) injectPoint).getMethodElement();
            if (methodElement.hasDeclaredAnnotation(Inject.class) && hasSpecialParameter(methodElement)) {
                return true;
            }
            if (methodElement instanceof ConstructorElement) {
                return methodElement.getOwningType().getEnclosedElements(ElementQuery.CONSTRUCTORS)
                        .stream()
                        .filter(constructor -> constructor.hasDeclaredAnnotation(Inject.class))
                        .limit(2)
                        .count() > 1;
            }
            return false;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static boolean hasSpecialParameter(MethodElement methodElement) {
        for (ParameterElement parameter : methodElement.getParameters()) {
            if (parameter.hasDeclaredAnnotation(Disposes.class)
                    || parameter.hasDeclaredAnnotation(Observes.class)
                    || parameter.hasDeclaredAnnotation(ObservesAsync.class)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRequiredQualifierMembers(VisitorContext context, Element injectPoint) {
        for (String qualifierName : bindingQualifierNames(context, injectPoint)) {
            AnnotationValue<Annotation> annotation = injectPoint.getAnnotation(qualifierName);
            if (annotation != null && !annotation.getValues().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> bindingQualifierNames(VisitorContext context, Element element) {
        Set<String> qualifierNames = new LinkedHashSet<>(element.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER));
        for (String annotationName : element.getAnnotationNames()) {
            if (isQualifierAnnotation(context, annotationName)) {
                qualifierNames.add(annotationName);
            }
        }
        qualifierNames.remove(Any.class.getName());
        qualifierNames.remove(io.micronaut.context.annotation.Any.class.getName());
        qualifierNames.remove(Default.class.getName());
        return qualifierNames;
    }

    private static boolean isRawGenericType(ClassElement classElement) {
        return classElement.isRawType()
                && !classElement.getDeclaredGenericPlaceholders().isEmpty()
                && classElement.getBoundGenericTypes().isEmpty()
                && classElement.getName().indexOf('.') > -1
                && !classElement.getName().equals(Instance.class.getName())
                && !classElement.getName().equals(Event.class.getName());
    }

    private static boolean isResolvableBeanClass(VisitorContext context, ClassElement candidate) {
        return !candidate.isInterface()
                && !candidate.isAbstract()
                && !candidate.hasStereotype(Interceptor.class)
                && !candidate.hasAnnotation(io.micronaut.core.annotation.Vetoed.class)
                && !candidate.hasAnnotation(jakarta.enterprise.inject.Vetoed.class)
                && hasResolvableBeanDefiningAnnotation(candidate)
                && isBeanClass(candidate);
    }

    private static boolean isUnproxyableNormalScopedBean(ClassElement candidate) {
        return candidate.hasAnnotation(ODI_UNPROXYABLE_BEAN)
                || (candidate.hasStereotype(jakarta.enterprise.context.NormalScope.class.getName())
                && (candidate.isFinal()
                || !hasNonPrivateNoArgsConstructor(candidate)
                || hasNonPrivateFinalMethod(candidate)));
    }

    private static boolean hasBeanTypeAssignableToRequiredType(ClassElement injectPointType, ClassElement candidateType) {
        if (isRawGenericType(injectPointType)) {
            return hasBeanTypeAssignableToRawRequiredType(injectPointType, candidateType);
        }
        Set<ClassElement> beanTypes = new LinkedHashSet<>();
        collectBeanTypes(candidateType, beanTypes);
        for (ClassElement beanType : beanTypes) {
            if (injectPointType.getName().equals(beanType.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBeanTypeAssignableToRawRequiredType(ClassElement injectPointType, ClassElement candidate) {
        Set<ClassElement> beanTypes = new LinkedHashSet<>();
        collectBeanTypes(candidate, beanTypes);
        for (ClassElement beanType : beanTypes) {
            if (injectPointType.getName().equals(beanType.getName()) && isBeanTypeAssignableToRawRequiredType(beanType)) {
                return true;
            }
        }
        return false;
    }

    private static final class ResolvableCandidate {
        private final String description;
        private final boolean producer;
        private final boolean runtimeResolution;
        private final boolean unproxyable;
        private final boolean reserve;
        private final boolean alternative;
        private final int priority;

        private ResolvableCandidate(String description,
                                    boolean producer,
                                    boolean runtimeResolution,
                                    boolean unproxyable,
                                    boolean reserve,
                                    boolean alternative,
                                    int priority) {
            this.description = description;
            this.producer = producer;
            this.runtimeResolution = runtimeResolution;
            this.unproxyable = unproxyable;
            this.reserve = reserve;
            this.alternative = alternative;
            this.priority = priority;
        }

        String description() {
            return description;
        }

        boolean requiresRuntimeResolution() {
            return producer || runtimeResolution;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ResolvableCandidate)) {
                return false;
            }
            ResolvableCandidate that = (ResolvableCandidate) other;
            return description.equals(that.description);
        }

        @Override
        public int hashCode() {
            return description.hashCode();
        }
    }

    private static boolean isBeanTypeAssignableToRawRequiredType(ClassElement beanType) {
        List<ClassElement> typeArguments = resolveTypeArguments(beanType, false);
        if (typeArguments.isEmpty()) {
            return true;
        }
        for (ClassElement typeArgument : typeArguments) {
            if (!isObjectType(typeArgument) && !isUnboundedTypeVariable(typeArgument)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUnboundedTypeVariable(ClassElement typeArgument) {
        if (!(typeArgument instanceof GenericPlaceholderElement)) {
            return false;
        }
        GenericPlaceholderElement placeholder = (GenericPlaceholderElement) typeArgument;
        List<? extends ClassElement> bounds = placeholder.getBounds();
        return bounds.isEmpty() || (bounds.size() == 1 && isObjectType(bounds.get(0)));
    }

    private static boolean isObjectType(ClassElement typeArgument) {
        return Object.class.getName().equals(typeArgument.getName());
    }

    private static boolean containsWildcard(ClassElement classElement) {
        if (classElement instanceof WildcardElement || classElement.isWildcard()) {
            return true;
        }
        if (classElement.isArray()) {
            return containsWildcard(classElement.fromArray());
        }
        for (ClassElement typeArgument : resolvedTypeArguments(classElement)) {
            if (containsWildcard(typeArgument)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTypeVariable(ClassElement classElement) {
        if (isTypeVariable(classElement)) {
            return true;
        }
        if (classElement.isArray()) {
            return containsTypeVariable(classElement.fromArray());
        }
        for (ClassElement typeArgument : resolvedTypeArguments(classElement)) {
            if (containsTypeVariable(typeArgument)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isArrayWithTypeVariableComponent(ClassElement classElement) {
        return classElement.isArray() && containsTypeVariable(classElement.fromArray());
    }

    private static boolean isTypeVariable(ClassElement classElement) {
        return classElement instanceof GenericPlaceholderElement
                || classElement.isGenericPlaceholder()
                || classElement.isTypeVariable();
    }

    private static boolean isDeclaredByVetoedType(Element element) {
        if (element instanceof ParameterElement) {
            try {
                return isDeclaredByVetoedType(((ParameterElement) element).getMethodElement());
            } catch (IllegalStateException e) {
                return false;
            }
        }
        if (element instanceof MemberElement) {
            MemberElement memberElement = (MemberElement) element;
            return isVetoed(memberElement.getDeclaringType()) || isVetoed(memberElement.getOwningType());
        }
        return false;
    }

    private static boolean isVetoed(ClassElement classElement) {
        return classElement.hasAnnotation(io.micronaut.core.annotation.Vetoed.class)
                || classElement.hasAnnotation(jakarta.enterprise.inject.Vetoed.class);
    }

    private static List<ClassElement> resolvedTypeArguments(ClassElement classElement) {
        List<ClassElement> typeArguments = new ArrayList<>(classElement.getBoundGenericTypes());
        if (typeArguments.isEmpty() && !classElement.getTypeArguments().isEmpty()) {
            typeArguments.addAll(classElement.getTypeArguments().values());
        }
        return typeArguments;
    }

    private static boolean hasOnlyDefaultQualifier(VisitorContext context, Element element) {
        Set<String> qualifierNames = new LinkedHashSet<>(element.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER));
        for (String annotationName : element.getAnnotationNames()) {
            if (isQualifierAnnotation(context, annotationName)) {
                qualifierNames.add(annotationName);
            }
        }
        qualifierNames.remove(Any.class.getName());
        qualifierNames.remove(io.micronaut.context.annotation.Any.class.getName());
        qualifierNames.remove(Default.class.getName());
        qualifierNames.remove(jakarta.inject.Named.class.getName());
        return qualifierNames.isEmpty();
    }

    private static boolean hasOnlyDefaultInjectionPointQualifier(VisitorContext context, Element element) {
        Set<String> qualifierNames = new LinkedHashSet<>(element.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER));
        for (String annotationName : element.getAnnotationNames()) {
            if (isQualifierAnnotation(context, annotationName)) {
                qualifierNames.add(annotationName);
            }
        }
        qualifierNames.remove(Any.class.getName());
        qualifierNames.remove(io.micronaut.context.annotation.Any.class.getName());
        qualifierNames.remove(Default.class.getName());
        return qualifierNames.isEmpty();
    }

    private static boolean isNoGenericType(ClassElement classElement) {
        return classElement.isRawType()
                || (classElement.getBoundGenericTypes().isEmpty() && classElement.getTypeArguments().isEmpty());
    }
}

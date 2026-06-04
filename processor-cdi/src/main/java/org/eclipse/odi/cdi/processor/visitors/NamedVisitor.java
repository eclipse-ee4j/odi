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
package org.eclipse.odi.cdi.processor.visitors;

import org.eclipse.odi.cdi.processor.AnnotationUtil;
import org.eclipse.odi.cdi.processor.CdiUtil;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.Stereotype;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates elements annotated with {@link jakarta.inject.Named}.
 */
public class NamedVisitor implements TypeElementVisitor<Object, Object> {
    private static final String DEPLOYMENT_EXCEPTION_MARKER = "[ODI_DEPLOYMENT_EXCEPTION] ";

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.singleton(io.micronaut.core.annotation.AnnotationUtil.NAMED);
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        applyCdiDefaultName(element);
        validateElement(element, context);
        validateAmbiguousBeanName(element, context);
    }

    private void validateElement(Element element, VisitorContext context) {
        if (element.hasAnnotation(AnnotationUtil.ANN_NAME) || element.hasStereotype(AnnotationUtil.ANN_NAME)) {
            if (element instanceof ParameterElement && element.stringValue(AnnotationUtil.ANN_NAME).isEmpty()) {
                context.fail("@Named injection points that are not fields must specify a value", element);
                return;
            }
            element.stringValue(AnnotationUtil.ANN_NAME).ifPresent((name) -> validateIdentifier(name, element, context));
            // now validate stereotypes are correct
            // stereotypes can only have an empty @Named qualifier
            // see https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#named_stereotype
            final List<String> stereotypes = element.getAnnotationNamesByStereotype(Stereotype.class);

            // if @Named is inherited via a stereotype
            if (!element.hasDeclaredAnnotation(AnnotationUtil.ANN_NAME)) {
                final List<String> namedStereotypes = element.getAnnotationNamesByStereotype(AnnotationUtil.ANN_NAME);
                if (stereotypes.containsAll(namedStereotypes)) {
                    if (element.stringValue(AnnotationUtil.ANN_NAME).isPresent()) {
                        context.fail("Stereotypes [" + CdiUtil
                                             .toAnnotationDescription(stereotypes) + "] cannot define a @Named qualifier with a"
                                             + " value. See " + CdiUtil.SPEC_LOCATION + "#named_stereotype",
                                     element);
                    }
                }
            }
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        applyCdiDefaultName(element);
        validatedNamedIfPresent(element, context);
        for (ParameterElement parameter : element.getParameters()) {
            validateElement(parameter, context);
        }
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        for (ParameterElement parameter : element.getParameters()) {
            validateElement(parameter, context);
        }
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        applyCdiDefaultName(element);
        validatedNamedIfPresent(element, context);
        validateElement(element, context);
    }

    private static void applyCdiDefaultName(Element element) {
        boolean namedByStereotype = isNamedByStereotype(element);
        if (namedByStereotype) {
            element.annotate(AnnotationUtil.ANN_NAMED_BY_STEREOTYPE);
            if (element.stringValue(AnnotationUtil.ANN_NAME).isEmpty() && isBeanNamingElement(element)) {
                element.annotate(AnnotationUtil.ANN_NAME, builder -> builder.value(cdiDefaultBeanName(element)));
            }
        }
        if (element.hasDeclaredAnnotation(AnnotationUtil.ANN_NAME)
                && element.stringValue(AnnotationUtil.ANN_NAME).isEmpty()
                && isBeanNamingElement(element)) {
            element.annotate(AnnotationUtil.ANN_NAME, builder -> builder.value(cdiDefaultBeanName(element)));
        }
    }

    private static boolean isNamedByStereotype(Element element) {
        if (element.hasDeclaredAnnotation(AnnotationUtil.ANN_NAME)) {
            return false;
        }
        List<String> stereotypes = element.getAnnotationNamesByStereotype(Stereotype.class);
        if (stereotypes.isEmpty()) {
            return false;
        }
        List<String> namedStereotypes = element.getAnnotationNamesByStereotype(AnnotationUtil.ANN_NAME);
        return !namedStereotypes.isEmpty() && stereotypes.containsAll(namedStereotypes);
    }

    private static boolean isBeanNamingElement(Element element) {
        if (element instanceof ClassElement) {
            return !((ClassElement) element).isInterface();
        }
        return true;
    }

    private static String cdiDefaultBeanName(Element element) {
        String name;
        if (element instanceof ClassElement) {
            name = element.getSimpleName();
        } else if (element instanceof MethodElement) {
            String methodName = element.getName();
            if (NameUtils.isGetterName(methodName)) {
                name = NameUtils.getPropertyNameForGetter(methodName);
            } else {
                name = methodName;
            }
        } else {
            name = element.getName();
        }
        return lowerFirstCodePoint(name);
    }

    private static String lowerFirstCodePoint(String name) {
        if (name.isEmpty()) {
            return name;
        }
        int firstCodePoint = name.codePointAt(0);
        int lowerFirstCodePoint = Character.toLowerCase(firstCodePoint);
        if (firstCodePoint == lowerFirstCodePoint) {
            return name;
        }
        int firstCodePointLength = Character.charCount(firstCodePoint);
        return new StringBuilder(name.length())
                .appendCodePoint(lowerFirstCodePoint)
                .append(name.substring(firstCodePointLength))
                .toString();
    }

    private void validatedNamedIfPresent(Element element, VisitorContext context) {
        if (element.hasAnnotation(AnnotationUtil.ANN_NAME) || element.hasStereotype(AnnotationUtil.ANN_NAME)) {
            element.stringValue(AnnotationUtil.ANN_NAME).ifPresent((name) -> validateIdentifier(name, element, context));
        }
    }

    private static void validateAmbiguousBeanName(ClassElement element, VisitorContext context) {
        Set<String> configuredBeanClasses = configuredBeanClasses(context);
        if (configuredBeanClasses.isEmpty()
                || !isNamedBeanClass(element)) {
            return;
        }
        Optional<String> beanName = resolveBeanName(element);
        if (beanName.isEmpty()) {
            return;
        }
        NameCandidate currentCandidate = toNameCandidate(context, element, beanName.get());
        if (currentCandidate == null) {
            return;
        }
        List<NameCandidate> candidates = new ArrayList<>();
        candidates.add(currentCandidate);
        for (String configuredBeanClass : configuredBeanClasses) {
            if (configuredBeanClass.equals(element.getName())) {
                continue;
            }
            Optional<ClassElement> candidate = context.getClassElement(configuredBeanClass);
            if (candidate.isEmpty() || !isNamedBeanClass(candidate.get())) {
                continue;
            }
            Optional<String> candidateName = resolveBeanName(candidate.get());
            if (candidateName.isPresent()
                    && isAmbiguousBeanName(beanName.get(), candidateName.get())) {
                NameCandidate nameCandidate = toNameCandidate(context, candidate.get(), candidateName.get());
                if (nameCandidate != null) {
                    candidates.add(nameCandidate);
                }
            }
        }
        List<NameCandidate> resolvedCandidates = selectNameResolutionCandidates(candidates);
        if (resolvedCandidates.size() > 1) {
            String conflictingName = resolvedCandidates.stream()
                    .map(NameCandidate::beanName)
                    .filter(candidateName -> !candidateName.equals(beanName.get()))
                    .findFirst()
                    .orElse(beanName.get());
            context.fail(
                    DEPLOYMENT_EXCEPTION_MARKER
                            + "Ambiguous bean name '" + beanName.get()
                            + "' conflicts with bean name '" + conflictingName + "'",
                    element
            );
        }
    }

    private static boolean isNamedBeanClass(ClassElement element) {
        return !element.isInterface()
                && AnnotationUtil.hasBeanDefiningAnnotation(element)
                && CdiUtil.isBeanClass(element)
                && (element.hasAnnotation(AnnotationUtil.ANN_NAME) || element.hasStereotype(AnnotationUtil.ANN_NAME));
    }

    private static Optional<String> resolveBeanName(ClassElement element) {
        Optional<String> explicitName = element.stringValue(AnnotationUtil.ANN_NAME);
        if (explicitName.isPresent() && StringUtils.isNotEmpty(explicitName.get())) {
            return explicitName;
        }
        if (isBeanNamingElement(element)) {
            return Optional.of(cdiDefaultBeanName(element));
        }
        return Optional.empty();
    }

    private static NameCandidate toNameCandidate(VisitorContext context, ClassElement element, String beanName) {
        if (!CdiUtil.isBeanEnabled(context, element)) {
            return null;
        }
        return new NameCandidate(
                beanName,
                CdiUtil.isAlternative(element),
                CdiUtil.isReserve(element),
                CdiUtil.resolvePriority(context, element).orElse(0)
        );
    }

    private static List<NameCandidate> selectNameResolutionCandidates(List<NameCandidate> candidates) {
        List<NameCandidate> nonReserveCandidates = candidates.stream()
                .filter(candidate -> !candidate.reserve)
                .collect(Collectors.toCollection(ArrayList::new));
        if (!nonReserveCandidates.isEmpty() && nonReserveCandidates.size() < candidates.size()) {
            candidates = nonReserveCandidates;
        }
        List<NameCandidate> alternatives = candidates.stream()
                .filter(candidate -> candidate.alternative)
                .collect(Collectors.toCollection(ArrayList::new));
        if (!alternatives.isEmpty()) {
            return selectHighestPriorityCandidates(alternatives);
        }
        if (candidates.stream().allMatch(candidate -> candidate.reserve)) {
            List<NameCandidate> priorityReserves = candidates.stream()
                    .filter(candidate -> candidate.priority > 0)
                    .collect(Collectors.toCollection(ArrayList::new));
            if (!priorityReserves.isEmpty()) {
                return selectHighestPriorityCandidates(priorityReserves);
            }
        }
        return candidates;
    }

    private static List<NameCandidate> selectHighestPriorityCandidates(List<NameCandidate> candidates) {
        int highestPriority = candidates.stream()
                .map(candidate -> candidate.priority)
                .max(Comparator.naturalOrder())
                .orElse(0);
        return candidates.stream()
                .filter(candidate -> candidate.priority == highestPriority)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static boolean isAmbiguousBeanName(String beanName, String candidateName) {
        return beanName.equals(candidateName)
                || beanName.startsWith(candidateName + ".")
                || candidateName.startsWith(beanName + ".");
    }

    private static Set<String> configuredBeanClasses(VisitorContext context) {
        String classNames = context.getOptions().get(CdiUtil.BEAN_CLASSES_OPTION);
        if (classNames == null || classNames.isBlank()) {
            classNames = System.getProperty(CdiUtil.BEAN_CLASSES_OPTION);
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

    private static boolean isJavaIdentifier(String name) {
        int start = name.codePointAt(0);
        if (!Character.isJavaIdentifierStart(start)) {
            return false;
        }
        int charCount = Character.charCount(start);
        for (int i = charCount; i < name.length(); i += charCount) {
            int codePoint = name.codePointAt(i);
            if (!Character.isJavaIdentifierPart(codePoint)) {
                return false;
            }
        }
        return true;
    }

    private void validateIdentifier(String name, Element element, VisitorContext visitorContext) {
        if (StringUtils.isNotEmpty(name)) {

            final String[] parts = name.split("\\.");
            for (String part : parts) {
                if (!isJavaIdentifier(part)) {
                    visitorContext.fail(
                            "@Named annotation specifies an invalid name. See " + CdiUtil.SPEC_LOCATION + "#names",
                            element
                    );
                    break;
                }
            }
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    private static final class NameCandidate {
        private final String beanName;
        private final boolean alternative;
        private final boolean reserve;
        private final int priority;

        private NameCandidate(String beanName, boolean alternative, boolean reserve, int priority) {
            this.beanName = beanName;
            this.alternative = alternative;
            this.reserve = reserve;
            this.priority = priority;
        }

        String beanName() {
            return beanName;
        }
    }
}

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
package com.oracle.odi.cdi.events;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.inject.Singleton;

/**
 * The registry of all observed methods in the context.
 */
@Singleton
@Internal
public final class OdiObserverMethodRegistry {

    private static final Qualifier DEFAULT = Qualifiers.byAnnotation(
            AnnotationMetadata.EMPTY_METADATA,
            Default.Literal.INSTANCE.annotationType()
    );
    private final List<OdiObserverMethod<?>> observerMethods = new ArrayList<>();

    /**
     * Registers an observed ODI method.
     * @param observerMethod The observer method.
     */
    public void register(OdiObserverMethod<?> observerMethod) {
        observerMethods.add(observerMethod);
    }

    /**
     * @return A list of observed ODI methods.
     */
    public List<OdiObserverMethod<?>> getObserverMethods() {
        return observerMethods;
    }

    /**
     * Finds observer methods.
     * @param argument The argument
     * @param qualifier The qualifier
     * @param <K> The observer method generic type
     * @param <T> The argument generic type
     * @return A list of observer methods
     */
    public <K extends T, T> List<OdiObserverMethod<K>> findListOfObserverMethods(
            Argument<T> argument,
            @Nullable Qualifier<T> qualifier) {
        // TODO: caching
        List<OdiObserverMethod<?>> list = new ArrayList<>();
        collectMethods(argument, qualifier, list);
        list.sort(Comparator.comparing(ObserverMethod::getPriority));
        return (List) list;
    }

    /**
     * Finds observer methods.
     * @param argument The argument
     * @param qualifier The qualifier
     * @param <K> The observer method generic type
     * @param <T> The argument generic type
     * @return A list of observer methods
     */
    public <K extends T, T> Set<OdiObserverMethod<K>> findSetOfObserverMethods(
            Argument<T> argument,
            @Nullable Qualifier<T> qualifier) {
        // TODO: caching
        Set<OdiObserverMethod<?>> set = new TreeSet<>(Comparator.comparing(ObserverMethod::getPriority));
        collectMethods(argument, qualifier, set);
        return (Set) set;
    }

    private <T> void collectMethods(Argument<T> argument, Qualifier<T> qualifier, Collection<OdiObserverMethod<?>> method) {
        for (OdiObserverMethod<?> observer : observerMethods) {
            if (!observer.getObservedArgument().isAssignableFrom(argument)) {
                continue;
            }
            Qualifier observedQualifier = observer.getObservedQualifier();
            if (observedQualifier != null) {
                if (qualifier == null) {
                    if (!observedQualifier.contains(DEFAULT)) {
                        continue;
                    }
                } else if (!qualifier.contains(observedQualifier) && !observedQualifier.equals(AnyQualifier.INSTANCE)) {
                    continue;
                }
            }
            method.add(observer);
        }
    }
}

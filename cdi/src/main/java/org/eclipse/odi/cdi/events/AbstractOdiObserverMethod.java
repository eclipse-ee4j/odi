/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package org.eclipse.odi.cdi.events;

import io.micronaut.core.annotation.AnnotationValue;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import org.eclipse.odi.cdi.annotation.ObservesMethod;

import java.lang.reflect.Type;

abstract class AbstractOdiObserverMethod<E> implements OdiObserverMethod<E> {
    protected final AnnotationValue<ObservesMethod> observesMethodAnnotationValue;
    private final boolean isAsync;
    private final int priority;
    private final Reception notifyObserver;
    private final TransactionPhase transactionPhase;

    AbstractOdiObserverMethod(AnnotationValue<ObservesMethod> observesMethodAnnotationValue) {
        if (observesMethodAnnotationValue == null) {
            throw new IllegalStateException("ObservesMethod cannot be null");
        }
        this.observesMethodAnnotationValue = observesMethodAnnotationValue;
        this.isAsync = observesMethodAnnotationValue.isTrue("async");
        this.priority = observesMethodAnnotationValue.intValue("priority").orElse(DEFAULT_PRIORITY);
        this.notifyObserver = observesMethodAnnotationValue.enumValue("notifyObserver", Reception.class).orElse(Reception.ALWAYS);
        this.transactionPhase = observesMethodAnnotationValue
                .enumValue("during", TransactionPhase.class)
                .orElse(TransactionPhase.IN_PROGRESS);

    }

    @Override
    public final boolean isAsync() {
        return isAsync;
    }

    @Override
    public final Reception getReception() {
        return notifyObserver;
    }

    @Override
    public final TransactionPhase getTransactionPhase() {
        return transactionPhase;
    }

    @Override
    public final int getPriority() {
        return priority;
    }

    @Override
    public Type getObservedType() {
        return getObservedArgument().asType();
    }
}

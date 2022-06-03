package com.oracle.odi.cdi.events;

import java.lang.reflect.Type;

import com.oracle.odi.cdi.annotation.ObservesMethod;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;

abstract class AbstractOdiObserverMethod<E> implements OdiObserverMethod<E> {
    private final boolean isAsync;
    private final int priority;
    private final Reception notifyObserver;
    private final TransactionPhase transactionPhase;
    protected final AnnotationValue<ObservesMethod> observesMethodAnnotationValue;

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
    public final Type getObservedType() {
        Argument<E> eventArgument = getObservedArgument();
        if (eventArgument.getTypeParameters().length == 0) {
            return eventArgument.getType();
        }
        return eventArgument.asParameterizedType();
    }
}

package com.oracle.odi.cdi.processor.extensions;

import java.util.Collection;
import java.util.Collections;

import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.ObserverInfo;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationMember;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;
import jakarta.enterprise.lang.model.types.Type;

class MethodObserverInfoImpl implements ObserverInfo {
    private final BeanInfoImpl beanInfo;
    private final MethodElement methodElement;
    private final ParameterElement observerParameter;
    private final VisitorContext visitorContext;

    MethodObserverInfoImpl(BeanInfoImpl beanInfo,
                           MethodElement methodElement,
                           ParameterElement parameterElement, VisitorContext visitorContext) {
        this.beanInfo = beanInfo;
        this.methodElement = methodElement;
        this.observerParameter = parameterElement;
        this.visitorContext = visitorContext;
    }

    @Override
    public Type eventType() {
        return eventParameter().type();
    }

    @Override
    public Collection<AnnotationInfo> qualifiers() {
        // TODO
        return Collections.emptyList();
    }

    @Override
    public ClassInfo declaringClass() {
        return beanInfo.declaringClass();
    }

    @Override
    public MethodInfo observerMethod() {
        return new MethodInfoImpl(
                beanInfo.getClassInfo(),
                methodElement,
                new TypesImpl(visitorContext),
                visitorContext
        );
    }

    @Override
    public ParameterInfo eventParameter() {
        return new ParameterInfoImpl(
                observerMethod(),
                observerParameter,
                new TypesImpl(visitorContext),
                visitorContext
        );
    }

    @Override
    public BeanInfo bean() {
        return beanInfo;
    }

    @Override
    public boolean isSynthetic() {
        return false;
    }

    @Override
    public int priority() {
        final AnnotationInfo annotation = eventParameter().annotation(Priority.class);
        if (annotation != null) {
            return annotation.value().asInt();
        }
        return 0;
    }

    @Override
    public boolean isAsync() {
        return eventParameter().hasAnnotation(ObservesAsync.class);
    }

    @Override
    public Reception reception() {
        AnnotationInfo ann;
        if (isAsync()) {
            ann = eventParameter().annotation(ObservesAsync.class);
        } else {
            ann = eventParameter().annotation(Observes.class);
        }
        if (ann != null) {
            final AnnotationMember notifyObserver = ann.member("notifyObserver");
            if (notifyObserver != null) {
                return notifyObserver.asEnum(Reception.class);
            }
        }
        return Reception.ALWAYS;
    }

    @Override
    public TransactionPhase transactionPhase() {
        if (isAsync()) {
            return null;
        }
        AnnotationInfo ann = eventParameter().annotation(Observes.class);
        if (ann != null) {
            final AnnotationMember m = ann.member("during");
            if (m != null) {
                return m.asEnum(TransactionPhase.class);
            }
        }
        return TransactionPhase.IN_PROGRESS;
    }
}

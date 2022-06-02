package com.oracle.odi.cdi;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

import io.micronaut.core.annotation.NonNull;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.util.TypeLiteral;

final class ResolvedInstanceImpl<T> implements Instance<T> {
    private final T instance;

    public ResolvedInstanceImpl(@NonNull T instance) {
        this.instance = Objects.requireNonNull(instance);
    }

    @Override
    public Instance<T> select(Annotation... qualifiers) {
        throw new UnsatisfiedResolutionException("No bean exists of type: " + instance.getClass());
    }

    @Override
    public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        throw new UnsatisfiedResolutionException("No bean exists of type: " + subtype);
    }

    @Override
    public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        throw new UnsatisfiedResolutionException("No bean exists of type: " + subtype);
    }

    @Override
    public boolean isUnsatisfied() {
        return false;
    }

    @Override
    public boolean isAmbiguous() {
        return false;
    }

    @Override
    public void destroy(T instance) {
        // no-op
    }

    @Override
    public Handle<T> getHandle() {
        return new Handle<T>() {
            @Override
            public T get() {
                return instance;
            }

            @Override
            public Bean<T> getBean() {
                return null;
            }

            @Override
            public void destroy() {
                // no-op
            }

            @Override
            public void close() {
                // no-op
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterable<? extends Handle<T>> handles() {
        return (Iterable<? extends Handle<T>>) Collections.singleton(getHandle()).iterator();
    }

    @Override
    public T get() {
        return instance;
    }

    @Override
    public Iterator<T> iterator() {
        return Collections.singleton(instance).iterator();
    }
}

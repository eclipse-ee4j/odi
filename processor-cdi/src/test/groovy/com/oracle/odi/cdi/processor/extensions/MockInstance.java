package com.oracle.odi.cdi.processor.extensions;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import org.jetbrains.annotations.NotNull;

// Mock impl, this will need to be implemented at runtime on the ODI module
public class MockInstance implements Instance<Object> {
    @Override
    public Instance<Object> select(Annotation... qualifiers) {
        return null;
    }

    @Override
    public <U> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return null;
    }

    @Override
    public <U> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return null;
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
    public void destroy(Object instance) {

    }

    @Override
    public Handle<Object> getHandle() {
        return null;
    }

    @Override
    public Iterable<Handle<Object>> handles() {
        return null;
    }

    @Override
    public Object get() {
        return null;
    }

    @NotNull
    @Override
    public Iterator<Object> iterator() {
        return null;
    }
}

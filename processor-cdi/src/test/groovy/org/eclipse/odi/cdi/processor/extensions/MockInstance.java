package org.eclipse.odi.cdi.processor.extensions;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Iterator;

import io.micronaut.core.annotation.NonNull;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

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
        return Collections.emptyList();
    }

    @Override
    public Object get() {
        return null;
    }

    @NonNull
    @Override
    public Iterator<Object> iterator() {
        return Collections.emptyIterator();
    }
}

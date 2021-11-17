package com.oracle.odi.cdi.processor.extensions;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Type;
import java.util.Set;

import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;

// Mock impl, this will need to be implemented at runtime on the ODI module
public class MockInjectionPoint implements InjectionPoint {
    @Override
    public Type getType() {
        return null;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return null;
    }

    @Override
    public Bean<?> getBean() {
        return null;
    }

    @Override
    public Member getMember() {
        return null;
    }

    @Override
    public Annotated getAnnotated() {
        return null;
    }

    @Override
    public boolean isDelegate() {
        return false;
    }

    @Override
    public boolean isTransient() {
        return false;
    }
}

package com.oracle.odi.cdi.processor.extensions;

import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import org.jboss.cdi.lang.model.tck.LangModelVerifier;

public class LangModelVerifierExtension implements BuildCompatibleExtension {

    public static boolean enable;
    public static boolean passed;
    public static Throwable exception;

    @Discovery
    void registerInterceptorBindings(ScannedClasses app, MetaAnnotations meta) {
        app.add(LangModelVerifier.class.getName());
    }

    @Enhancement(types = LangModelVerifier.class)
    public void run(ClassInfo clazz) {
        if (!enable) {
            return;
        }
        try {
            LangModelVerifier.verify(clazz);
            passed = true;
        } catch (Throwable e) {
            exception = e;
        }
    }
}

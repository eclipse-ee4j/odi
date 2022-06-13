package org.eclipse.odi.cdi.processor.extensions;

import jakarta.annotation.Priority;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.smallrye.faulttolerance.CircuitBreakerMaintenanceImpl;
import io.smallrye.faulttolerance.DefaultAsyncExecutorProvider;
import io.smallrye.faulttolerance.ExecutorHolder;
import io.smallrye.faulttolerance.ExistingCircuitBreakerNames;
import io.smallrye.faulttolerance.FaultToleranceBinding;
import io.smallrye.faulttolerance.FaultToleranceInterceptor;
import io.smallrye.faulttolerance.FaultToleranceOperationProvider;
import io.smallrye.faulttolerance.api.CircuitBreakerName;
import io.smallrye.faulttolerance.internal.StrategyCache;
import io.smallrye.faulttolerance.metrics.MetricsProvider;
import jakarta.enterprise.inject.build.compatible.spi.*;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

//@SkipIfPortableExtensionPresent(FaultToleranceExtension.class)
public class BuildCompatibleFaultToleranceExtension implements BuildCompatibleExtension {
    public static final String FAULT_TOLERANCE_EXT_ENABLED = "Fault_Tolerance_Enabled";

    private static final Set<String> FAULT_TOLERANCE_ANNOTATIONS = new HashSet<>(Arrays.asList(
            Asynchronous.class.getName(),
            Bulkhead.class.getName(),
            CircuitBreaker.class.getName(),
            Fallback.class.getName(),
            Retry.class.getName(),
            Timeout.class.getName()));

    private final Set<String> faultToleranceClasses = new HashSet<>();
    private final Map<String, Set<MethodInfo>> existingCircuitBreakerNames = new HashMap<>();

    @Discovery
    void registerInterceptorBindings(ScannedClasses app, MetaAnnotations meta) {
        if (!Boolean.getBoolean(BuildCompatibleFaultToleranceExtension.FAULT_TOLERANCE_EXT_ENABLED)) {
            return;
        }

        app.add(FaultToleranceInterceptor.class.getName());
        app.add(ExecutorHolder.class.getName());
        app.add(TestFallbackProvider.class.getName());
        app.add(DefaultAsyncExecutorProvider.class.getName());
        app.add(MetricsProvider.class.getName());
        app.add(StrategyCache.class.getName());
        app.add(CircuitBreakerMaintenanceImpl.class.getName());
        app.add(TestRequestContextIntegration.class.getName());

        meta.addInterceptorBinding(Asynchronous.class)
                .addAnnotation(FaultToleranceBinding.class);
        meta.addInterceptorBinding(Bulkhead.class)
                .addAnnotation(FaultToleranceBinding.class);
        meta.addInterceptorBinding(CircuitBreaker.class)
                .addAnnotation(FaultToleranceBinding.class);
        meta.addInterceptorBinding(Fallback.class)
                .addAnnotation(FaultToleranceBinding.class);
        meta.addInterceptorBinding(Retry.class)
                .addAnnotation(FaultToleranceBinding.class);
        meta.addInterceptorBinding(Timeout.class)
                .addAnnotation(FaultToleranceBinding.class);
    }

    @Enhancement(types = FaultToleranceInterceptor.class)
    void changeInterceptorPriority(ClassConfig clazz) {
        if (!Boolean.getBoolean(BuildCompatibleFaultToleranceExtension.FAULT_TOLERANCE_EXT_ENABLED)) {
            return;
        }
        ConfigProvider.getConfig()
                .getOptionalValue("mp.fault.tolerance.interceptor.priority", Integer.class)
                .ifPresent(configuredInterceptorPriority -> {
                    clazz.removeAnnotation(it -> it.name().equals(Priority.class.getName()));
                    clazz.addAnnotation(AnnotationBuilder.of(Priority.class).member("value", configuredInterceptorPriority).build());
                });
    }

    @Registration(types = Object.class)
    void collectFaultToleranceOperations(BeanInfo bean) {
        if (!Boolean.getBoolean(BuildCompatibleFaultToleranceExtension.FAULT_TOLERANCE_EXT_ENABLED)) {
            return;
        }
        if (!bean.isClassBean()) {
            return;
        }

        if (hasFaultToleranceAnnotations(bean.declaringClass())) {
            faultToleranceClasses.add(bean.declaringClass().name());
        }

        for (MethodInfo method : bean.declaringClass().methods()) {
            if (method.hasAnnotation(CircuitBreakerName.class)) {
                String cbName = method.annotation(CircuitBreakerName.class).value().asString();
                existingCircuitBreakerNames.computeIfAbsent(cbName, ignored -> new HashSet<>())
                        .add(method);
            }
        }
    }

    @Synthesis
    void registerSyntheticBeans(SyntheticComponents syn) {
        if (!Boolean.getBoolean(BuildCompatibleFaultToleranceExtension.FAULT_TOLERANCE_EXT_ENABLED)) {
            return;
        }
        String[] classesArray = faultToleranceClasses.toArray(new String[0]);
        syn.addBean(BuildCompatibleFaultToleranceOperationProvider.class)
                .type(FaultToleranceOperationProvider.class)
                .type(BuildCompatibleFaultToleranceOperationProvider.class)
                .scope(Singleton.class)
                .alternative(true)
                .priority(1)
                .withParam("classes", classesArray)
                .createWith(BuildCompatibleFaultToleranceOperationProvider.Creator.class);

//        syn.addObserver()
//                .declaringClass(BuildCompatibleFaultToleranceOperationProvider.class)
//                .type(AfterStartup.class)
//                .observeWith(BuildCompatibleFaultToleranceOperationProvider.EagerInitializationTrigger.class);

        String[] circuitBreakersArray = existingCircuitBreakerNames.keySet().toArray(new String[0]);
        syn.addBean(BuildCompatibleExistingCircuitBreakerNames.class)
                .type(ExistingCircuitBreakerNames.class)
                .type(BuildCompatibleExistingCircuitBreakerNames.class)
                .scope(Singleton.class)
                .alternative(true)
                .priority(1)
                .withParam("names", circuitBreakersArray)
                .createWith(BuildCompatibleExistingCircuitBreakerNames.Creator.class);
    }

    @Validation
    void validate(Messages msg) {
        if (!Boolean.getBoolean(BuildCompatibleFaultToleranceExtension.FAULT_TOLERANCE_EXT_ENABLED)) {
            return;
        }
        for (Map.Entry<String, Set<MethodInfo>> entry : existingCircuitBreakerNames.entrySet()) {
            if (entry.getValue().size() > 1) {
                Set<String> methodNames = entry.getValue()
                        .stream()
                        .map(it -> it.declaringClass().name() + "." + it.name())
                        .collect(Collectors.toSet());
                msg.error(new DefinitionException("Multiple circuit breakers have the same name '"
                                                          + entry.getKey() + "': " + methodNames));
            }
        }
    }

    static boolean hasFaultToleranceAnnotations(ClassInfo clazz) {
        if (clazz.hasAnnotation(it -> FAULT_TOLERANCE_ANNOTATIONS.contains(it.name()))) {
            return true;
        }

        for (MethodInfo method : clazz.methods()) {
            if (method.hasAnnotation(it -> FAULT_TOLERANCE_ANNOTATIONS.contains(it.name()))) {
                return true;
            }
        }

        ClassInfo superClass = clazz.superClassDeclaration();
        if (superClass != null) {
            return hasFaultToleranceAnnotations(superClass);
        }

        return false;
    }
}
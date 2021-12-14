package com.oracle.odi.cdi.processor.extensions;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.smallrye.faulttolerance.FaultToleranceOperationProvider;
import io.smallrye.faulttolerance.config.FaultToleranceOperation;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

public class BuildCompatibleFaultToleranceOperationProvider implements FaultToleranceOperationProvider {
    private final Map<CacheKey, FaultToleranceOperation> operationCache = new ConcurrentHashMap<>();

    void init(Map<CacheKey, FaultToleranceOperation> operationCache) {
        this.operationCache.putAll(operationCache);
    }

    @Override
    public FaultToleranceOperation get(Class<?> beanClass, Method method) {
        CacheKey key = new CacheKey(beanClass, method);
        return operationCache.computeIfAbsent(key, ignored -> FaultToleranceOperation.of(key.beanClass, key.method));
    }

    static class CacheKey {
        private final Class<?> beanClass;
        private final Method method;

        public CacheKey(Class<?> beanClass, Method method) {
            this.beanClass = beanClass;
            this.method = method;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CacheKey cacheKey = (CacheKey) o;
            return Objects.equals(beanClass, cacheKey.beanClass) &&
                    Objects.equals(method, cacheKey.method);
        }

        @Override
        public int hashCode() {
            return Objects.hash(beanClass, method);
        }
    }

    public static class Creator implements SyntheticBeanCreator<BuildCompatibleFaultToleranceOperationProvider> {
        private Set<Method> getAllMethods(Class<?> beanClass) {
            Set<Method> allMethods = new HashSet<>();
            Class<?> currentClass = beanClass;
            while (currentClass != null && !currentClass.equals(Object.class)) {
                Collections.addAll(allMethods, currentClass.getDeclaredMethods());
                currentClass = currentClass.getSuperclass(); // this will be null for interfaces
            }
            return allMethods;
        }

        @Override
        public BuildCompatibleFaultToleranceOperationProvider create(Instance<Object> lookup, Parameters params) {
            String[] faultToleranceClasses = params.get("classes", String[].class);

            List<Throwable> allExceptions = new ArrayList<>();
            Map<CacheKey, FaultToleranceOperation> operationCache = new HashMap<>(faultToleranceClasses.length);
            for (String faultToleranceClass : faultToleranceClasses) {
                try {
                    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                    Class<?> clazz = Class.forName(faultToleranceClass, true, classLoader);

                    for (Method method : getAllMethods(clazz)) {
                        FaultToleranceOperation operation = FaultToleranceOperation.of(clazz, method);
                        if (operation.isLegitimate()) {
                            try {
                                operation.validate();

                                // register the operation at validation time to avoid re-creating it at runtime
                                CacheKey cacheKey = new CacheKey(clazz, method);
                                operationCache.put(cacheKey, operation);
                            } catch (FaultToleranceDefinitionException e) {
                                allExceptions.add(e);
                            }
                        }
                    }
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }

            if (!allExceptions.isEmpty()) {
                if (allExceptions.size() == 1) {
                    Throwable error = allExceptions.get(0);
                    if (error instanceof DefinitionException) {
                        throw (DefinitionException) error;
                    } else {
                        throw new DefinitionException(allExceptions.get(0));
                    }
                } else {
                    StringBuilder message = new StringBuilder("Found " + allExceptions.size() + " deployment problems: ");
                    int idx = 1;
                    for (Throwable error : allExceptions) {
                        message.append("\n").append("[").append(idx++).append("] ").append(error.getMessage());
                    }
                    DefinitionException definitionException = new DefinitionException(message.toString());
                    for (Throwable error : allExceptions) {
                        definitionException.addSuppressed(error);
                    }
                    throw definitionException;
                }
            }

            BuildCompatibleFaultToleranceOperationProvider result = new BuildCompatibleFaultToleranceOperationProvider();
            result.init(operationCache);
            return result;
        }
    }

//    public static class EagerInitializationTrigger implements SyntheticObserver<ApplicationS> {
//        @Override
//        public void observe(EventContext<AfterStartup> event) {
//            CDI.current().select(BuildCompatibleFaultToleranceOperationProvider.class).get();
//        }
//    }
}
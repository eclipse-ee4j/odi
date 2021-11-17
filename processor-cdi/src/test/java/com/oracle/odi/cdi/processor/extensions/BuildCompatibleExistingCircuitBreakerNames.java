package com.oracle.odi.cdi.processor.extensions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


import io.smallrye.faulttolerance.ExistingCircuitBreakerNames;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.spi.InjectionPoint;

public class BuildCompatibleExistingCircuitBreakerNames implements ExistingCircuitBreakerNames {
    private Set<String> names;

    void init(Set<String> names) {
        this.names = names;
    }

    @Override
    public boolean contains(String name) {
        return names.contains(name);
    }

    public static class Creator implements SyntheticBeanCreator<BuildCompatibleExistingCircuitBreakerNames> {
        @Override
        public BuildCompatibleExistingCircuitBreakerNames create(
                CreationalContext<BuildCompatibleExistingCircuitBreakerNames> creationalContext,
                InjectionPoint injectionPoint, Map<String, Object> map) {

            String[] existingCircuitBreakerNames = (String[]) map.get("names");

            BuildCompatibleExistingCircuitBreakerNames result = new BuildCompatibleExistingCircuitBreakerNames();
            result.init(new HashSet<>(Arrays.asList(existingCircuitBreakerNames)));
            return result;
        }
    }
}
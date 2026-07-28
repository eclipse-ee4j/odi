package org.eclipse.odi.cdi.processor.extensions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


import io.smallrye.faulttolerance.ExistingCircuitBreakerNames;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;

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
        public BuildCompatibleExistingCircuitBreakerNames create(Instance<Object> lookup, Parameters params) {
            String[] existingCircuitBreakerNames = params.get("names", String[].class);
            if (existingCircuitBreakerNames.length > 0) {
                if (BuildCompatibleFaultToleranceExtension.MUTATED_ARRAY_VALUE.equals(existingCircuitBreakerNames[0])) {
                    throw new AssertionError("Synthetic bean parameter arrays must be copied when registered");
                }
                String original = existingCircuitBreakerNames[0];
                existingCircuitBreakerNames[0] = BuildCompatibleFaultToleranceExtension.MUTATED_ARRAY_VALUE;
                if (BuildCompatibleFaultToleranceExtension.MUTATED_ARRAY_VALUE.equals(params.get("names", String[].class)[0])) {
                    throw new AssertionError("Synthetic bean parameter arrays must be copied when read");
                }
                existingCircuitBreakerNames[0] = original;
            }
            BuildCompatibleExistingCircuitBreakerNames result = new BuildCompatibleExistingCircuitBreakerNames();
            result.init(new HashSet<>(Arrays.asList(existingCircuitBreakerNames)));
            return result;
        }
    }
}

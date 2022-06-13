package org.eclipse.odi.cdi.processor.extensions;

import io.smallrye.faulttolerance.FallbackHandlerProvider;
import io.smallrye.faulttolerance.config.FaultToleranceOperation;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;

public class TestFallbackProvider implements FallbackHandlerProvider {
    @Override
    public <T> FallbackHandler<T> get(FaultToleranceOperation operation) {
        return null;
    }
}

package org.eclipse.odi.cdi.processor.extensions;

import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.control.RequestContextController;

import io.smallrye.faulttolerance.RequestContextIntegration;

public class TestRequestContextIntegration extends RequestContextIntegration {
    @Override
    public RequestContextController get() {
        return new RequestContextController() {
            @Override
            public boolean activate() {
                return false;
            }

            @Override
            public void deactivate() throws ContextNotActiveException {

            }
        };
    }
}

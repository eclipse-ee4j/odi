package org.eclipse.odi.docs.cdi.extension;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Synthetic bean produced by {@link PaymentBuildExtension}.
 *
 * <p>The bean is not declared by application source. Instead, the extension
 * synthesizes it during annotation processing and passes the discovered gateway
 * names to {@link Creator} as build-time parameters.</p>
 */
public class PaymentCatalog {
    private final Set<String> gateways;

    protected PaymentCatalog() {
        this.gateways = Collections.emptySet();
    }

    private PaymentCatalog(Set<String> gateways) {
        this.gateways = Collections.unmodifiableSet(new LinkedHashSet<>(gateways));
    }

    /**
     * Returns the gateway names discovered by the build-compatible extension.
     *
     * @return the gateway names discovered by the build-compatible extension
     */
    public Set<String> gateways() {
        return gateways;
    }

    /**
     * Checks whether a gateway was discovered by the extension.
     *
     * @param gateway gateway name to check
     * @return whether the gateway was discovered at build time
     */
    public boolean contains(String gateway) {
        return gateways.contains(gateway);
    }

    /**
     * Runtime creator used by ODI to instantiate the synthetic bean.
     */
    public static final class Creator implements SyntheticBeanCreator<PaymentCatalog> {
        /**
         * Creates the synthetic bean creator.
         */
        public Creator() {
        }

        @Override
        public PaymentCatalog create(Instance<Object> lookup, Parameters params) {
            String[] gatewayNames = params.get("gateways", String[].class);
            return new PaymentCatalog(new LinkedHashSet<>(Arrays.asList(gatewayNames)));
        }
    }
}

/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.eclipse.odi.cdispec._23._5;

import org.eclipse.odi.cdispec.annotations.Asynchronous;
import org.eclipse.odi.cdispec.annotations.Synchronous;
import org.eclipse.odi.test.junit5.OdiTest;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@OdiTest
public class QualifiersOnProducesTest {
    private final PaymentProcessor paymentProcessor;

    public QualifiersOnProducesTest(@Named("test") PaymentProcessor paymentProcessor) {
        this.paymentProcessor = paymentProcessor;
    }

    @Test
    @DisplayName("2.3.5.  Specifying qualifiers of a method or constructor parameter https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#method_constructor_parameter_qualifiers")
    void testQualifiersOnProduces() {
        Assertions.assertTrue(paymentProcessor instanceof AsyncPaymentProcessor);
    }
}

@Singleton
class PaymentProcessorProducer {
    @Produces
    @Named("test")
    PaymentProcessor getPaymentProcessor(@Synchronous PaymentProcessor sync,
                                         @Asynchronous PaymentProcessor async) {
        return isSynchronous() ? sync : async;
    }

    private boolean isSynchronous() {
        return false;
    }
}

interface PaymentProcessor {
}

@Synchronous
class SyncPaymentProcessor implements PaymentProcessor {
}

@Asynchronous
class AsyncPaymentProcessor implements PaymentProcessor {
}
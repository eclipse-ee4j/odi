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

package com.oracle.odi.cdispec._23;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.oracle.odi.test.junit5.OdiTest;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@OdiTest
public class AnnotationQualifiersTest {
    @Inject
    @Synchronous PaymentProcessor syncProcessor;

    @Inject
    @Asynchronous PaymentProcessor asyncProcessor;

    @Test
    @DisplayName("2.3. Qualifiers https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#qualifiers")
    void testQualifiers() {
        assertTrue(syncProcessor instanceof SynchronousPaymentProcessor);
        assertTrue(asyncProcessor instanceof AsynchronousPaymentProcessor);
    }
}

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@interface Synchronous {}

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@interface Asynchronous {}

interface PaymentProcessor {}

@Synchronous
class SynchronousPaymentProcessor
        implements PaymentProcessor {
}

@Asynchronous
class AsynchronousPaymentProcessor
        implements PaymentProcessor {
}
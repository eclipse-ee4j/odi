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

package com.oracle.odi.cdispec._23._2;

import com.oracle.odi.cdispec.annotations.Asynchronous;
import com.oracle.odi.cdispec.annotations.PayBy;
import com.oracle.odi.cdispec.annotations.Synchronous;
import com.oracle.odi.test.junit5.OdiTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@OdiTest
public class QualifiersTest {
    @Inject
    @PayBy(PayBy.PaymentMethod.CREDIT_CARD)
    @Asynchronous
    PaymentProcessor asyncCreditCartProcessor;

    @Inject
    @PayBy(PayBy.PaymentMethod.CREDIT_CARD)
    @Synchronous
    PaymentProcessor syncCreditCartProcessor;

    @Inject
    @PayBy(PayBy.PaymentMethod.TRANSFER)
    @Asynchronous
    PaymentProcessor asyncBankTransferProcessor;

    @Inject
    @PayBy(PayBy.PaymentMethod.TRANSFER)
    @Synchronous
    PaymentProcessor syncBankTransferProcessor;

    @Test
    @DisplayName("2.3.2. Defining new qualifier types https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#defining_qualifier_types")
    void testQualifiers() {
        assertTrue(asyncCreditCartProcessor instanceof AsyncCreditCardProcessor);
        assertTrue(syncCreditCartProcessor instanceof CreditCardProcessor);
        assertTrue(asyncBankTransferProcessor instanceof AsyncBankTransferProcessor);
        assertTrue(syncBankTransferProcessor instanceof BankTransferProcessor);
    }
}

interface PaymentProcessor {
}

@PayBy(PayBy.PaymentMethod.CREDIT_CARD)
@Synchronous
class CreditCardProcessor implements PaymentProcessor {
}

@PayBy(PayBy.PaymentMethod.TRANSFER)
@Synchronous
class BankTransferProcessor implements PaymentProcessor {
}

@PayBy(PayBy.PaymentMethod.CREDIT_CARD)
@Asynchronous
class AsyncCreditCardProcessor implements PaymentProcessor {
}

@PayBy(PayBy.PaymentMethod.TRANSFER)
@Asynchronous
class AsyncBankTransferProcessor implements PaymentProcessor {
}


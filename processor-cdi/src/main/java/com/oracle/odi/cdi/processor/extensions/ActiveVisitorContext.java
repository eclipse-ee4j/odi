/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oracle.odi.cdi.processor.extensions;

import io.micronaut.inject.visitor.VisitorContext;

/**
 * Tracks the active visitor context.
 */
final class ActiveVisitorContext {

    private static VisitorContext visitorContext = null;

    private ActiveVisitorContext() {
    }

    /**
     * @return The current visitor context.
     */
    static VisitorContext currentVisitorContext() {
        if (visitorContext == null) {
            final String message = "No active visitor context, this API can only be called at"
                    + " build time within the context of compilation";
            throw new IllegalStateException(message);
        }
        return visitorContext;
    }

    static void setVisitorContext(VisitorContext vc) {
        visitorContext = vc;
    }
}

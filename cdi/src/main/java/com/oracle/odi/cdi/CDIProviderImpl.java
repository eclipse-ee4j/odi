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
package com.oracle.odi.cdi;

import java.util.Map;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Internal;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.CDIProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the {@link CDIProvider} interface.
 */
@Internal
public class CDIProviderImpl implements CDIProvider {
    private static final Logger LOG = LoggerFactory.getLogger(CDIProviderImpl.class);
    @Override
    public CDI<Object> getCDI() {
        LOG.trace("Resolving CDI Provider");
        final Map<ApplicationContext, OdiSeContainer> runningContainers = OdiSeContainer.RUNNING_CONTAINERS;
        if (!runningContainers.isEmpty()) {
            if (runningContainers.size() == 1) {
                final OdiSeContainer container = runningContainers.values().iterator().next();
                LOG.trace("Resolved CDI container. Running: {}", container.isRunning());
                return container;
            } else {
                throw new IllegalStateException("Multiple running SeContainers present");
            }
        }
        throw new IllegalStateException("No running SeContainer present");
    }
}

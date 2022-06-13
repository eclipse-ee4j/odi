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
package org.eclipse.odi.cdi.processor.extensions;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.ObserverInfo;
import jakarta.enterprise.lang.model.AnnotationTarget;

// Implementation node: A custom messages impl is needed for discovery
// since we have to find all the annotations before having access to compiler warnings / errors
// in order to feed the processed annotation names to the incremental compiler
final class DiscoveryMessagesImpl implements Messages {
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> infos = new ArrayList<>();

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getInfos() {
        return infos;
    }

    @Override
    public void info(String message) {
        if (message != null) {
            infos.add(message);
        }
    }

    @Override
    public void info(String message, AnnotationTarget relatedTo) {
        if (message != null) {
            infos.add(message);
        }

    }

    @Override
    public void info(String message, BeanInfo relatedTo) {
        if (message != null) {
            infos.add(message);
        }
    }

    @Override
    public void info(String message, ObserverInfo relatedTo) {
        if (message != null) {
            infos.add(message);
        }
    }

    @Override
    public void warn(String message) {
        if (message != null) {
            warnings.add(message);
        }
    }

    @Override
    public void warn(String message, AnnotationTarget relatedTo) {
        if (message != null) {
            warnings.add(message);
        }
    }

    @Override
    public void warn(String message, BeanInfo relatedTo) {
        if (message != null) {
            warnings.add(message);
        }
    }

    @Override
    public void warn(String message, ObserverInfo relatedTo) {
        if (message != null) {
            warnings.add(message);
        }
    }

    @Override
    public void error(String message) {
        if (message != null) {
            errors.add(message);
        }
    }

    @Override
    public void error(String message, AnnotationTarget relatedTo) {
        if (message != null) {
            errors.add(message);
        }
    }

    @Override
    public void error(String message, BeanInfo relatedTo) {
        if (message != null) {
            errors.add(message);
        }
    }

    @Override
    public void error(String message, ObserverInfo relatedTo) {
        if (message != null) {
            errors.add(message);
        }
    }

    @Override
    public void error(Exception exception) {
        if (exception != null) {
            errors.add(exception.getMessage());
        }
    }
}

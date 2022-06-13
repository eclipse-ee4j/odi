/*
 * Copyright 2017-2020 original authors
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
package org.eclipse.odi.cdispec._100;

import io.micronaut.context.event.ApplicationEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;

import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class DeadlockProducer {
    private final Event event;

    public DeadlockProducer(Event event) {
        this.event = event;
    }

    @PostConstruct
    public void init() {
        try {
            event.fireAsync(new ApplicationEvent("Event")).toCompletableFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public String method() {
        return "value";
    }
}

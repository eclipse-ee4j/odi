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

package org.eclipse.odi.cdi.interceptors.ontype;

import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Monitored
@Interceptor
class MonitoringInterceptor {
    private final MonitoringService monitoringService;

    MonitoringInterceptor(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @AroundInvoke
    public Object monitorInvocation(InvocationContext ctx)
            throws Exception {
        monitoringService.addInvoked(ctx.getMethod().getName());
        return ctx.proceed();
    }
}
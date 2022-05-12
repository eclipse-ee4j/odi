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
package com.oracle.odi.tck.porting;

import com.oracle.odi.cdi.context.AbstractContext;
import com.oracle.odi.cdi.context.DependentContext;
import jakarta.enterprise.context.spi.Context;
import org.jboss.cdi.tck.spi.Contexts;

/**
 * TCK's contexts implementation.
 */
public class ContextsImpl implements Contexts<Context> {

    @Override
    public void setActive(Context context) {
        if (context instanceof AbstractContext) {
            ((AbstractContext) context).activate();
        } else {
            throw new IllegalStateException("Unknown context");
        }
    }

    @Override
    public void setInactive(Context context) {
        if (context instanceof AbstractContext) {
            ((AbstractContext) context).deactivate();
        } else {
            throw new IllegalStateException("Unknown context");
        }
    }

    @Override
    public Context getRequestContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Context getDependentContext() {
        return new DependentContext(null);
    }

    @Override
    public void destroyContext(Context context) {
        if (context instanceof AbstractContext) {
            ((AbstractContext) context).destroy();
        } else {
            throw new IllegalStateException("Unknown context");
        }
    }

}

/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package org.eclipse.odi.cdi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class OdiExecutableInvokerInfoTest {

    @Test
    void parameterMetadataArraysAreDefensivelyCopied() {
        String[] parameterTypeNames = {"java.lang.String"};
        int[] parameterArrayDimensions = {0};
        OdiExecutableInvokerInfo invokerInfo = new OdiExecutableInvokerInfo(
                "test.Bean",
                "test.Bean",
                "execute",
                parameterTypeNames,
                parameterArrayDimensions,
                false
        );

        parameterTypeNames[0] = "java.lang.Integer";
        parameterArrayDimensions[0] = 1;

        assertArrayEquals(new String[]{"java.lang.String"}, invokerInfo.getParameterTypeNames());
        assertArrayEquals(new int[]{0}, invokerInfo.getParameterArrayDimensions());

        String[] returnedTypeNames = invokerInfo.getParameterTypeNames();
        int[] returnedArrayDimensions = invokerInfo.getParameterArrayDimensions();
        returnedTypeNames[0] = "java.lang.Long";
        returnedArrayDimensions[0] = 2;

        assertArrayEquals(new String[]{"java.lang.String"}, invokerInfo.getParameterTypeNames());
        assertArrayEquals(new int[]{0}, invokerInfo.getParameterArrayDimensions());
    }
}

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

package org.eclipse.odi.cdispec._34._2;

import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeclaringDisposerMethodTest {
    @Test
    void testDisposerMethod() {
        EntityManager entityManager;
        try (SeContainer container = SeContainerInitializer.newInstance().initialize()) {
            entityManager = container.select(EntityManager.class).get();
            assertFalse(entityManager.closed);
        }
        assertNotNull(entityManager);
        assertTrue(entityManager.closed);
    }
}

@Singleton
class UserDatabaseEntityManager {

    @Produces
    @Singleton
    public EntityManager create(EntityManagerFactory emf) {
        return emf.createEntityManager();
    }

    @Produces
    EntityManagerFactory factory() {
        return new EntityManagerFactory();
    }

    public void close(@Disposes EntityManager em) {
        em.close();
    }
}

class EntityManagerFactory {
    EntityManager createEntityManager() {
        return new EntityManager();
    }
}

class EntityManager implements AutoCloseable {
    boolean closed = false;

    @Override
    public void close() {
        this.closed = true;
    }
}

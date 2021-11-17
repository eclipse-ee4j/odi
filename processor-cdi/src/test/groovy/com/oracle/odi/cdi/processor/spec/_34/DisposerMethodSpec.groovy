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

package com.oracle.odi.cdi.processor.spec._34

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class DisposerMethodSpec extends AbstractTypeElementSpec {

    void "test fail compilation on 2 disposer methods for the same type"() {
        when:
        def context = buildContext('''
package distest;

import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;


@Singleton
class UserDatabaseEntityManager extends Other {

    @Produces
    @Singleton
    public EntityManager create(EntityManagerFactory emf) {
        return emf.createEntityManager();
    }

    @Produces
    EntityManagerFactory factory() {
        return new EntityManagerFactory();
    }

    public void close(@Disposes EntityManager em) throws Exception {
        em.close();
    }
    
    public void close2(@Disposes EntityManager em) throws Exception {
        em.close();
    }
}

class Other {}

class EntityManagerFactory {
    EntityManager createEntityManager() {
        return new EntityManager();
    }
}

class EntityManager implements AutoCloseable {
    boolean closed = false;

    @Override
    public void close() throws Exception {
        this.closed = true;
    }
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Only a single @Disposes method is permitted, found: void close2(EntityManager) and void close(EntityManager)")
    }

    void "test fail compilation when a disposer method is defined with no producer"() {
        when:
        def context = buildContext('''
package distest;

import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;


@Singleton
class UserDatabaseEntityManager {

    public void close(@Disposes EntityManager em) throws Exception {
        em.close();
    }

}

class EntityManager implements AutoCloseable {
    boolean closed = false;

    @Override
    public void close() throws Exception {
        this.closed = true;
    }
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("No associated @Produces method found for @Disposes method.")
    }
}

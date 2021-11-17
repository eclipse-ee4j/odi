package com.oracle.odi.cdispec._100

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class EventDeadlockSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void 'test publishing an event on a different does not deadlock'() {
        given:
        DeadlockProducer producer = context.getBean(DeadlockProducer)

        expect:
        producer != null
    }
}

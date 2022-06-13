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

package org.eclipse.odi.cdispec._100;

import org.eclipse.odi.test.junit5.OdiTest;
import io.micronaut.aop.InterceptedProxy;
import io.micronaut.context.BeanLocator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

@OdiTest
public class EventsTest {

    @Test
    void testThatProxiedInstancesAreEqual(LoginService loginService1, BeanLocator beanLocator) {
        LoginService loginService2 = beanLocator.getProxyTargetBean(LoginService.class, null);
        LoginService loginService3 = beanLocator.getProxyTargetBean(LoginService.class, null);
        Assertions.assertTrue(loginService1 instanceof InterceptedProxy);
        Assertions.assertSame(((InterceptedProxy<?>) loginService1).interceptedTarget(), loginService2);
        Assertions.assertSame(((InterceptedProxy<?>) loginService1).interceptedTarget(), loginService3);
        Assertions.assertSame(loginService2, loginService3);
    }

    @Test
    void testEventDefinition(Bean<Event<Object>> objectEventBean,
                             Bean<Event<LoggedInEvent>> loggedInEventBean,
                             @Mobile @USA Bean<Event<LoggedInEvent>> loggedInEventAnnotatedBean) {
        Assertions.assertNull(objectEventBean.getName());
        Assertions.assertNull(loggedInEventBean.getName());
        Assertions.assertNull(loggedInEventAnnotatedBean.getName());
//        Assertions.assertEquals(1, objectEventBean.getQualifiers().size());
//        Assertions.assertEquals(1, loggedInEventBean.getQualifiers().size());
//        Assertions.assertEquals(1, loggedInEventAnnotatedBean.getQualifiers().size());
        Assertions.assertTrue(loggedInEventBean.getQualifiers().stream().anyMatch(q -> q instanceof Any));
        Assertions.assertTrue(loggedInEventBean.getQualifiers().stream().anyMatch(q -> q instanceof Any));
        Assertions.assertTrue(loggedInEventAnnotatedBean.getQualifiers().stream().anyMatch(q -> q instanceof Any));
        Assertions.assertEquals(Dependent.class, objectEventBean.getScope());
        Assertions.assertEquals(Dependent.class, loggedInEventBean.getScope());
        Assertions.assertEquals(Dependent.class, loggedInEventAnnotatedBean.getScope());
    }

    @Test
    void testBeanContainerObjectEvent(BeanContainer beanContainer, LoginService loginService) {
        loginService.reset();
        beanContainer.getEvent().fire(new LoggedInEvent());
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertTrue(loginService.isLoginInEventDefaultTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileAndroidTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());

        Assertions.assertNotNull(loginService.getLoggedInEventMetadata());
        Assertions.assertNotNull(loginService.getLoggedInDefaultEventMetadata());
        assertEventMetadataWithEmptyAnnotations(loginService.getLoggedInEventMetadata());
        assertEventMetadataWithEmptyAnnotations(loginService.getLoggedInDefaultEventMetadata());
    }

    @Test
    void testInjectedObjectEvent(Event<Object> objectEvent, LoginService loginService) {
        loginService.reset();
        objectEvent.fire(new LoggedInEvent());
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertTrue(loginService.isLoginInEventDefaultTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileAndroidTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());

        Assertions.assertNotNull(loginService.getLoggedInEventMetadata());
        Assertions.assertNotNull(loginService.getLoggedInDefaultEventMetadata());
        assertEventMetadataWithEmptyAnnotations(loginService.getLoggedInEventMetadata());
        assertEventMetadataWithEmptyAnnotations(loginService.getLoggedInDefaultEventMetadata());
    }

    @Test
    void testInjectedObjectSelectedEvent(Event<Object> objectEvent, LoginService loginService) {
        loginService.reset();
        objectEvent.select(LoggedInEvent.class).fire(new LoggedInEvent());
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertTrue(loginService.isLoginInEventDefaultTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileAndroidTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testInjectedEvent(Event<LoggedInEvent> objectEvent, LoginService loginService) {
        loginService.reset();
        objectEvent.fire(new LoggedInEvent());
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertTrue(loginService.isLoginInEventDefaultTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileAndroidTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testSelectedAnnotationEvent(Event<Object> objectEvent, LoginService loginService) {
        loginService.reset();
        objectEvent.select(annotations()).fire(new LoggedInEvent());
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertFalse(loginService.isLoginInEventDefaultTriggered());
        Assertions.assertTrue(loginService.isLoginInEventMobileTriggered());
        Assertions.assertTrue(loginService.isLoginInEventMobileAndroidTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testInjectedObjectAnnotatedAnnotationEvent(@Mobile @Android @USA Event<Object> objectEvent, LoginService loginService) {
        loginService.reset();
        objectEvent.fire(new LoggedInEvent());
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertFalse(loginService.isLoginInEventDefaultTriggered());
        Assertions.assertTrue(loginService.isLoginInEventMobileTriggered());
        Assertions.assertTrue(loginService.isLoginInEventMobileAndroidTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testInjectedAnnotatedEvent(@Mobile @Android @USA Event<LoggedInEvent> objectEvent, LoginService loginService) {
        loginService.reset();
        objectEvent.fire(new LoggedInEvent());
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertFalse(loginService.isLoginInEventDefaultTriggered());
        Assertions.assertTrue(loginService.isLoginInEventMobileTriggered());
        Assertions.assertTrue(loginService.isLoginInEventMobileAndroidTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testSelectedTypeAndAnnotationEvent(Event<Object> objectEvent, LoginService loginService) {
        loginService.reset();
        objectEvent.select(LoggedInEvent.class, annotations()).fire(new LoggedInEvent());
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertFalse(loginService.isLoginInEventDefaultTriggered());
        Assertions.assertTrue(loginService.isLoginInEventMobileTriggered());
        Assertions.assertTrue(loginService.isLoginInEventMobileAndroidTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testSelectedTypeLiteralAndAnnotationEvent(Event<Object> objectEvent, LoginService loginService) {
        loginService.reset();
        objectEvent.select(new TypeLiteral<LoggedInEvent>() {
        }, annotations()).fire(new LoggedInEvent());
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertFalse(loginService.isLoginInEventDefaultTriggered());
        Assertions.assertTrue(loginService.isLoginInEventMobileTriggered());
        Assertions.assertTrue(loginService.isLoginInEventMobileAndroidTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testAnnotationValueSelector(
            @Platform(platform = PlatformType.MOBILE) Event<LoggedInEvent> mobileEvent,
            @Platform(platform = PlatformType.WEB) Event<LoggedInEvent> webEvent,
            LoginService loginService) {
        loginService.reset();
        Assertions.assertFalse(loginService.isLoginInEventPlatformMobileTriggered());
        Assertions.assertFalse(loginService.isLoginInEventPlatformWebTriggered());
        mobileEvent.fire(new LoggedInEvent());
        Assertions.assertTrue(loginService.isLoginInEventPlatformMobileTriggered());
        Assertions.assertFalse(loginService.isLoginInEventPlatformWebTriggered());
        loginService.reset();
        webEvent.fire(new LoggedInEvent());
        Assertions.assertFalse(loginService.isLoginInEventPlatformMobileTriggered());
        Assertions.assertTrue(loginService.isLoginInEventPlatformWebTriggered());
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertFalse(loginService.isLoginInEventDefaultTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileAndroidTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testAnnotationValueObjectTypeSelected1(
            Event<Object> webEvent,
            LoginService loginService) {
        loginService.reset();
        webEvent.select(LoggedInEvent.class, new PlatformWebAnnotationLiteral() {

            @Override
            public PlatformType platform() {
                return PlatformType.WEB;
            }
        }).fire(new LoggedInEvent());
        Assertions.assertFalse(loginService.isLoginInEventPlatformMobileTriggered());
        Assertions.assertTrue(loginService.isLoginInEventPlatformWebTriggered());
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertFalse(loginService.isLoginInEventDefaultTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileAndroidTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testAnnotationValueObjectTypeSelected12(
            Event<Object> webEvent,
            LoginService loginService) {
        loginService.reset();
        webEvent.select(LoggedInEvent.class, new Platform() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Platform.class;
            }

            @Override
            public PlatformType platform() {
                return PlatformType.WEB;
            }
        }).fire(new LoggedInEvent());
        Assertions.assertFalse(loginService.isLoginInEventPlatformMobileTriggered());
        Assertions.assertTrue(loginService.isLoginInEventPlatformWebTriggered());
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertFalse(loginService.isLoginInEventDefaultTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileAndroidTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    private void assertEventMetadataWithEmptyAnnotations(EventMetadata loggedInEventMetadata) {
        Assertions.assertNotNull(loggedInEventMetadata);
        Assertions.assertEquals(LoggedInEvent.class, loggedInEventMetadata.getType());
        InjectionPoint injectionPoint = loggedInEventMetadata.getInjectionPoint();
        Assertions.assertNotNull(injectionPoint);
        Set<Annotation> qualifiers = loggedInEventMetadata.getQualifiers();
        Assertions.assertNotNull(qualifiers);
        Assertions.assertTrue(qualifiers.isEmpty());
    }

    private Annotation[] annotations() {
        return new Annotation[]{
                new AnnotationLiteral<Mobile>() {
                }, new AnnotationLiteral<Android>() {
        }, new AnnotationLiteral<USA>() {
        }
        };
    }
}

@ApplicationScoped
class LoginService {

    private boolean loginInEventTriggered = false;
    private boolean loginInEventDefaultTriggered = false;
    private boolean loginInEventMobileTriggered = false;
    private boolean loginInEventPlatformMobileTriggered = false;
    private boolean loginInEventPlatformWebTriggered = false;
    private boolean loginInEventMobileAndroidTriggered = false;
    private boolean abstractInEventTriggered = false;
    private EventMetadata loggedInEventMetadata;
    private EventMetadata loggedInDefaultEventMetadata;
    private EventMetadata loggedInMobileEventMetadata;
    private EventMetadata loggedInPlatformMobileEventMetadata;
    private EventMetadata loggedInMobileAndroidEventMetadata;

    void catchLoggedInEvent(@Observes LoggedInEvent event, EventMetadata eventMetadata) {
        loginInEventTriggered = true;
        loggedInEventMetadata = eventMetadata;
    }

    void catchLoggedInDefaultEvent(@Observes @Default LoggedInEvent event, EventMetadata eventMetadata) {
        loginInEventDefaultTriggered = true;
        loggedInDefaultEventMetadata = eventMetadata;
    }

    void catchLoggedInMobileEvent(@Observes @Mobile LoggedInEvent event, EventMetadata eventMetadata) {
        loginInEventMobileTriggered = true;
        loggedInMobileEventMetadata = eventMetadata;
    }

    void catchLoggedInPlatformMobileEvent(@Observes @Platform(platform = PlatformType.MOBILE) LoggedInEvent event, EventMetadata eventMetadata) {
        loginInEventPlatformMobileTriggered = true;
        loggedInPlatformMobileEventMetadata = eventMetadata;
    }

    void catchLoggedInPlatformWebEvent(@Observes @Platform(platform = PlatformType.WEB) LoggedInEvent event) {
        loginInEventPlatformWebTriggered = true;
    }

    void catchLoggedInMobileAndroidEvent(@Observes @Mobile @Android LoggedInEvent event, EventMetadata eventMetadata) {
        loginInEventMobileAndroidTriggered = true;
        loggedInMobileAndroidEventMetadata = eventMetadata;
    }

    void catchAbstractInEvent(@Observes AbstractInEvent event) {
        abstractInEventTriggered = true;
    }

    public boolean isLoginInEventTriggered() {
        return loginInEventTriggered;
    }

    public boolean isLoginInEventDefaultTriggered() {
        return loginInEventDefaultTriggered;
    }

    public boolean isLoginInEventMobileTriggered() {
        return loginInEventMobileTriggered;
    }

    public boolean isLoginInEventPlatformMobileTriggered() {
        return loginInEventPlatformMobileTriggered;
    }

    public boolean isLoginInEventPlatformWebTriggered() {
        return loginInEventPlatformWebTriggered;
    }

    public boolean isLoginInEventMobileAndroidTriggered() {
        return loginInEventMobileAndroidTriggered;
    }

    public boolean isAbstractInEventTriggered() {
        return abstractInEventTriggered;
    }

    public EventMetadata getLoggedInEventMetadata() {
        return loggedInEventMetadata;
    }

    public EventMetadata getLoggedInDefaultEventMetadata() {
        return loggedInDefaultEventMetadata;
    }

    public EventMetadata getLoggedInMobileEventMetadata() {
        return loggedInMobileEventMetadata;
    }

    public EventMetadata getLoggedInPlatformMobileEventMetadata() {
        return loggedInPlatformMobileEventMetadata;
    }

    public EventMetadata getLoggedInMobileAndroidEventMetadata() {
        return loggedInMobileAndroidEventMetadata;
    }

    public void reset() {
        loginInEventDefaultTriggered = false;
        loginInEventTriggered = false;
        loginInEventMobileTriggered = false;
        loginInEventPlatformMobileTriggered = false;
        loginInEventPlatformWebTriggered = false;
        loginInEventMobileAndroidTriggered = false;
        abstractInEventTriggered = false;
        loggedInEventMetadata = null;
        loggedInDefaultEventMetadata = null;
        loggedInMobileEventMetadata = null;
        loggedInPlatformMobileEventMetadata = null;
        loggedInMobileAndroidEventMetadata = null;
    }
}

class LoggedInEvent extends AbstractInEvent {
}

abstract class AbstractInEvent {

}

@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
@interface Platform {

    PlatformType platform();
}

abstract class PlatformWebAnnotationLiteral extends AnnotationLiteral<Platform> implements Platform {
}

enum PlatformType {
    MOBILE, WEB
}


@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
@interface Mobile {
}

@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
@interface Android {
}

@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
@interface USA {
}
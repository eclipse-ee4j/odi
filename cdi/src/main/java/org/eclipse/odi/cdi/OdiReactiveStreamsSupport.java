/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package org.eclipse.odi.cdi;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Optional Reactive Streams support. This class must only be loaded after
 * {@code org.reactivestreams.Publisher} is known to be present.
 */
final class OdiReactiveStreamsSupport {
    private OdiReactiveStreamsSupport() {
    }

    static boolean isPublisher(Object result) {
        return result instanceof Publisher<?>;
    }

    static Object destroyOnCompletion(Object publisher, OdiExecutableInvokerExecutor.Completion completion) {
        return destroyOnCompletionTyped((Publisher<?>) publisher, completion);
    }

    private static <T> Publisher<T> destroyOnCompletionTyped(Publisher<T> publisher,
                                                             OdiExecutableInvokerExecutor.Completion completion) {
        return subscriber -> {
            try {
                publisher.subscribe(new Subscriber<T>() {
                    @Override
                    public void onSubscribe(Subscription subscription) {
                        subscriber.onSubscribe(subscription);
                    }

                    @Override
                    public void onNext(T item) {
                        subscriber.onNext(item);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        try {
                            subscriber.onError(throwable);
                        } finally {
                            completion.complete();
                        }
                    }

                    @Override
                    public void onComplete() {
                        try {
                            subscriber.onComplete();
                        } finally {
                            completion.complete();
                        }
                    }
                });
            } catch (Throwable e) {
                completion.fail();
                throw e;
            }
        };
    }
}

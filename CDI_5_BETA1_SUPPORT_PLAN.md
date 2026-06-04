# CDI 5.0 Beta1 Support Draft PR

## Summary

Move ODI from CDI 4.1 to CDI 5.0 Beta1 and implement the CDI Lite-facing API/SPI deltas: `@Eager`, `@Reserve`, `@AutoClose`, async invoker handlers, `SyntheticInjections`, record lang-model support, and the new container/interface methods. CDI 5.0 lists these as new feature areas and also changes CDI API/TCK Maven GAVs.

## Key Changes

- Update dependency metadata to CDI 5:
  - `cdi = "5.0.0.Beta1"` using the resolvable Maven Central version.
  - Move API/TCK artifacts from `jakarta.enterprise:*` to `jakarta.cdi:*`.
  - Update TCK runner jar names to `jakarta.cdi-tck-*` and signature extraction to `cdi-api-jdk17.sigfile`.
  - Update README/docs dependency snippets, without overwriting the existing untracked `docs/` tree blindly.
- Compile against CDI 5 APIs:
  - Add `OdiSeContainerInitializer.addBuildCompatibleExtensions(...)`.
  - Add `OdiBeanContainerImpl.unwrapClientProxy(T)` using `InterceptedProxy.interceptedTarget()` with a no-op fallback.
  - Remove obsolete EL methods from the TCK `BeanManagerFactory` shim.
- Add CDI 5 annotation support:
  - Map `@Eager` to Micronaut `@Context`; validate that eager beans are `@ApplicationScoped`, including producer and stereotype cases.
  - Map selected `@Reserve` beans to Micronaut `@Secondary`; disable unselected reserves without `@Priority`; update CDI resolution paths so non-reserve beans win over reserve beans, and validate `@Reserve + @Alternative` errors.
  - Implement `@AutoClose` by marking `close()` as pre-destroy for class beans and by setting `@Bean(preDestroy = "close")` for producer/synthetic beans when the produced type is `AutoCloseable`; verify close exceptions are swallowed by the disposable path.
- Add BCE/SPI support:
  - Implement `BeanInfo.isReserve()`, `isEager()`, and `isAutoClose()` from direct and stereotype metadata.
  - Implement `SyntheticBeanBuilder.reserve/eager/autoClose` and all `withInjectionPoint(...)` overloads.
  - Add a runtime `SyntheticInjections` adapter that only resolves registered injection points, supports `Class` and `TypeLiteral`, handles qualifiers, exposes `InjectionPoint` where valid, and releases dependent objects with the synthetic bean or disposer invocation as required.
  - Support both CDI 5 `SyntheticBeanCreator/Disposer` signatures and deprecated CDI 4.x signatures, selecting the directly implemented CDI 5 method first and failing clearly if both/neither are usable.
- Add invoker and lang-model support:
  - Implement `InvokerValidation.ensureAsyncHandlerExists(...)`.
  - Add async handler discovery for `AsyncHandler.ReturnType` and `AsyncHandler.ParameterType`, plus built-ins for `CompletionStage`, `CompletableFuture`, `Flow.Publisher`, and soft optional `org.reactivestreams.Publisher`.
  - Delay dependent-context destruction for async invokers until the matched handler invokes completion; still destroy immediately for synchronous exceptions.
  - Add `RecordComponentInfoImpl`, real `ClassInfo.recordComponents()`, `ClassInfo.isSealed()`, and `ClassInfo.permittedSubclasses()` using Micronaut AST model data where available.
  - Model record components from Micronaut `PropertyElement`; Java records are already exposed there, so ODI should not depend directly on `javax.lang.model.element.RecordComponentElement` or other APT-specific APIs for this.
  - Upstream only the missing language-neutral sealed-class APIs to Core (`ClassElement.isSealed()` and `ClassElement.getPermittedSubclasses()`).
- Known draft gap:
  - `SeContainerInitializer.addBuildCompatibleExtensions(...)` cannot truly run compile-time BCE discovery after application classes are already compiled. The draft should implement the CDI 5 method but throw a clear unsupported exception, document the limitation, and leave full programmatic BCE registration as a follow-up build-time registration channel.

## Test Plan

Final verification target: the CDI Lite 5 TCK must pass without regressions. The compile, signature, unit, and targeted TCK runs below are checkpoints toward that target, not substitutes for it.

- Local unit/compile checks:
  - `./gradlew -Plocal.git.odi.micronaut-core=/Users/graemerocher/dev/micronaut/core.cdi :micronaut-odi-processor-cdi:test :micronaut-odi-cdi:test`
  - `./gradlew -Plocal.git.odi.micronaut-core=/Users/graemerocher/dev/micronaut/core.cdi :micronaut-odi-tck-runner:cdiSignatureTest`
- Targeted TCK smoke tests with `:micronaut-odi-tck-runner:singleTest`:
  - `org.jboss.cdi.tck.tests.eager.bean.EagerBeanTest`
  - `org.jboss.cdi.tck.tests.eager.producer.method.EagerProducerMethodTest`
  - `org.jboss.cdi.tck.tests.reserve.basic.SelectedReserveTest`
  - `org.jboss.cdi.tck.tests.reserve.selection.priority.ReservePriorityTest`
  - `org.jboss.cdi.tck.tests.autoclose.bean.AutoCloseBeanTest`
  - `org.jboss.cdi.tck.tests.autoclose.producer.method.AutoCloseProducerMethodTest`
  - `org.jboss.cdi.tck.tests.build.compatible.extensions.syntheticBeanInjections.SyntheticInjectionsTest`
  - `org.jboss.cdi.tck.tests.build.compatible.extensions.syntheticBeanInjectionsUnregistered.SyntheticInjectionsUnregisteredTest`
  - `org.jboss.cdi.tck.tests.invokers.lookup.dependent.async.builtin.AsyncHandlerBuiltinTest`
  - `org.jboss.cdi.tck.tests.invokers.lookup.dependent.async.returntype.AsyncHandlerReturnTypeTest`
  - `org.jboss.cdi.tck.tests.invokers.lookup.dependent.async.paramtype.AsyncHandlerParamTypeTest`
- Run `./gradlew -Plocal.git.odi.micronaut-core=/Users/graemerocher/dev/micronaut/core.cdi :micronaut-odi-tck-runner:fullTckTest` after targeted tests are green; treat CDI Full portable-extension-only failures as out of ODI Lite scope unless they overlap with Lite behavior.

## Assumptions

- Use the adjacent `/Users/graemerocher/dev/micronaut/core.cdi` checkout on branch `cdi-5.1.x`.
- Until that Core change is merged into the include-git branch, pass `-Plocal.git.odi.micronaut-core=/Users/graemerocher/dev/micronaut/core.cdi`; otherwise ODI compiles against the cached include-git checkout and will not see the new language-neutral sealed-class APIs.
- No Micronaut Core change is expected for `@Eager`, `@Reserve`, or `@AutoClose`; only add core support if sealed/permitted class metadata is not available through current AST/native element APIs.
- The first PR targets CDI 5 Beta1 compatibility and TCK progress, not final Jakarta EE 12 certification metadata.

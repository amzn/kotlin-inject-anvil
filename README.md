# kotlin-inject-anvil

[![Maven Central](https://img.shields.io/maven-central/v/software.amazon.lastmile.kotlin.inject.anvil/compiler.svg?label=Maven%20Central)](https://central.sonatype.com/search?smo=true&namespace=software.amazon.lastmile.kotlin.inject.anvil)
[![CI](https://github.com/amzn/kotlin-inject-anvil/workflows/CI/badge.svg)](https://github.com/amzn/kotlin-inject-anvil/actions?query=branch%3Amain)
[![Slack channel](https://img.shields.io/badge/chat-slack-blue.svg?logo=slack)](https://kotlinlang.slack.com/messages/kotlin-inject/)

[kotlin-inject](https://github.com/evant/kotlin-inject) is a compile-time dependency injection
framework for Kotlin Multiplatform similar to Dagger 2 for Java.
[Anvil](https://github.com/square/anvil) extends Dagger 2 to simplify dependency injection.

This project provides a similar feature set for the `kotlin-inject` framework. The extensions provided
by `kotlin-inject-anvil` allow you to contribute and automatically merge component interfaces without explicit
references in code.

```kotlin
@ContributesTo(AppScope::class)
interface AppIdComponent {
    @Provides
    fun provideAppId(): String = "demo app"
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAuthenticator : Authenticator

// The final kotlin-inject component.
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
interface AppComponent

// Instantiate the component at runtime.
val component = AppComponent::class.create()
```
From the above example code snippet:

* `AppIdComponent` will be made a super type of the final component and the
provider method is known to the object graph, so you can inject and use AppId anywhere.
* A binding for `RealAuthenticator` will be generated and the type `Authenticator` can safely be injected anywhere.
* Note that neither `AppIdComponent` nor `RealAuthenticator` need to be referenced anywhere else in your code.

## Setup

The project comes with a KSP plugin and a runtime module:
```groovy
dependencies {
    kspCommonMainMetadata "software.amazon.lastmile.kotlin.inject.anvil:compiler:$version"
    commonMainImplementation "software.amazon.lastmile.kotlin.inject.anvil:runtime:$version"

    // Optional module, but strongly suggested to import. It contains the
    // @SingleIn scope and @ForScope qualifier annotation together with the
    // AppScope::class marker.
    commonMainImplementation "software.amazon.lastmile.kotlin.inject.anvil:runtime-optional:$version"
}
```

You should setup kotlin-inject as described in the [official docs](https://github.com/evant/kotlin-inject).
For details how to setup KSP itself for multiplatform projects, see the
[official documentation](https://kotlinlang.org/docs/ksp-multiplatform.html).

#### Snapshot builds

To import snapshot builds use following repository:
```groovy
maven {
    url 'https://aws.oss.sonatype.org/content/repositories/snapshots/'
}
```

## Usage

### Contributions

#### `@ContributesTo`

Component interfaces can be contributed using the `@ContributesTo` annotation:
```kotlin
@ContributesTo(AppScope::class)
interface AppIdComponent {
    @Provides
    fun provideAppId(): String = "demo app"
}
```
The scope `AppScope::class` tells `kotlin-inject-anvil` in which component to merge this
interface.

#### `@ContributesBinding`

`kotlin-inject` requires you to write
[binding / provider methods](https://github.com/evant/kotlin-inject#usage) in order to provide a
type in the object graph. Imagine this API:
```kotlin
interface Authenticator

class RealAuthenticator : Authenticator
```
Whenever you inject `Authenticator` the expectation is to receive an instance of
`RealAuthenticator`. With vanilla `kotlin-inject` you can achieve this with a provider
method:
```kotlin
@Inject
@SingleIn(AppScope::class)
class RealAuthenticator : Authenticator

@ContributesTo(AppScope::class)
interface AuthenticatorComponent {
    @Provides
    fun provideAuthenticator(authenticator: RealAuthenticator): Authenticator = authenticator
}
```
Note that `@ContributesTo` is leveraged to automatically add the interface to the final component.

However, this is still too much code and can be simplified further with `@ContributesBinding`:
```kotlin
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAuthenticator : Authenticator
```
`@ContributesBinding` will generate a provider method similar to the one above and automatically
add it to the final component.

##### Multi-bindings

`@ContributesBinding` supports `Set` multi-bindings via its `multibinding` parameter.

```kotlin
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
class LoggingInterceptor : Interceptor

@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class AppComponent {
    // Will be contributed to this set multi-binding.
    abstract val interceptors: Set<Interceptor>
}
```

#### `@ContributesSubcomponent`

The `@ContributesSubcomponent` annotation allows you to define a subcomponent in any Gradle module,
but the final `@Component` will be generated when the parent component is merged.
```kotlin
@ContributesSubcomponent(LoggedInScope::class)
@SingleIn(LoggedInScope::class)
interface RendererComponent {

    @ContributesSubcomponent.Factory(AppScope::class)
    interface Factory {
        fun createRendererComponent(): RendererComponent
    }
}
```
For more details on usage of the annotation and behavior
[see the documentation](runtime/src/commonMain/kotlin/software/amazon/lastmile/kotlin/inject/anvil/ContributesSubcomponent.kt).

### Merging

With `kotlin-inject`, components are defined similar to the one below in order to instantiate your
object graph at runtime:
```kotlin
@Component
@SingleIn(AppScope::class)
interface AppComponent
```
In order to pick up all contributions, you must change the `@Component` annotation to
`@MergeComponent`:
```kotlin
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
interface AppComponent
```
This will generate a new component class with the original `@Component` annotation and merge all
contributions to the scope `AppScope`.

To instantiate the component at runtime, call the generated `create()` function:
```kotlin
val component = AppComponent::class.create()
```

#### Parameters

Parameters are supported the same way as with `kotlin-inject`:
```kotlin
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class AppComponent(
    @get:Provides val userId: String,
)

val component = AppComponent::class.create("userId")
```

#### Kotlin Multiplatform

With Kotlin Multiplatform there is a high chance that the generated code cannot be referenced
from common Kotlin code or from common platform code like `iosMain`. This is due to how
[common source folders are separated from platform source folders](https://kotlinlang.org/docs/whatsnew20.html#separation-of-common-and-platform-sources-during-compilation).
For more details and recommendations setting up kotlin-inject in Kotlin Multiplatform projects
see the [official guide](https://github.com/evant/kotlin-inject/blob/main/docs/multiplatform.md).

To address this issue, you can define an `expect fun` in the common source code next to
component class itself. The `actual fun` will be generated and create the component. The
function must be annotated with `@MergeComponent.CreateComponent`. It's optional to have a
receiver type of `KClass` with your component type as argument. The number of parameters
must match the arguments of your component and the return type must be your component, e.g.
your component in common code could be declared as:
```kotlin
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class AppComponent(
    @get:Provides userId: String,
)

// Create this function next to your component class. The actual function will be generated.
@CreateComponent
expect fun create(appId: String): AppComponent

// Or with receiver type:
@CreateComponent
expect fun KClass<AppComponent>.create(appId: String): AppComponent
```
The generated `actual fun` will be generated and will look like this:
```kotlin
actual fun create(appId: String): AppComponent {
    return KotlinInjectAppComponent::class.create(appId)
}
```

### Scopes

The plugin builds a connection between contributions and merged components through the scope
parameters. Scope classes are only markers and have no further meaning besides building a
connection between contributions and merging them. The class `AppScope` from the sample could
look like this:
```kotlin
object AppScope
```

Scope classes are independent of the `kotlin-inject`
[scopes](https://github.com/evant/kotlin-inject#scopes). It's still necessary to set a scope for
the `kotlin-inject` components or to make instances a singleton in a scope, e.g.
```kotlin
@Inject
@SingleIn(AppScope::class) // scope for kotlin-inject
@ContributesBinding(AppScope::class)
class RealAuthenticator : Authenticator

@MergeComponent(AppScope::class)
@SingleIn(AppScope::class) // scope for kotlin-inject
interface AppComponent
```

`kotlin-inject-anvil` provides the
[`@SingleIn` scope annotation](runtime-optional/src/commonMain/kotlin/software/amazon/lastmile/kotlin/inject/anvil/SingleIn.kt)
optionally by importing following module. We strongly recommend to use the annotation for
consistency.
```groovy
dependencies {
    commonMainImplementation "software.amazon.lastmile.kotlin.inject.anvil:runtime-optional:$version"
}
```

## Sample

A [sample project](sample) for Android and iOS is available.

## Talk

The idea and more background about this library is covered in this
[public talk](https://ralf-wondratschek.com/presentation/extending-kotlin-inject-nyc.html).

## Advanced options

### Custom symbol processors

`kotlin-inject-anvil` is extensible and you can create your own annotations and KSP symbol
processors. In the generated code you can reference annotations from `kotlin-inject-anvil` itself
and build logic on top of them.

For example, assume this is your annotation:
```kotlin
@Target(CLASS)
@ContributingAnnotation // see below for details
annotation class MyCustomAnnotation
```

Your custom KSP symbol processor uses this annotation as trigger and generates following code:
```kotlin
@ContributesTo(AppScope::class)
interface MyCustomComponent {
    @Provides
    fun provideMyCustomType(): MyCustomType = ...
}
```
This generated component interface `MyCustomComponent` will be picked up by `kotlin-inject-anvil's`
symbol processors and contributed to the `AppScope` due to the `@ContributesTo` annotation.

**Custom annotations and symbol processors are very powerful and allow you to adjust
`kotlin-inject-anvil` to your needs and your codebase.**

There are two ways to indicate these to `kotlin-inject-anvil`. This is important for incremental
compilation and multi-round support.

1. **This is the preferred option**: Annotate your annotation with the `@ContributingAnnotation`
    marker and run `kotlin-inject-anvil`'s compiler over the project the annotation is hosted in.
    Adding the compiler as described in the [the setup](#setup) is important, otherwise the
    `@ContributingAnnotation` has no effect. With this the annotation is understood as a
    contributing annotation in all downstream usages of this annotation.
    ```kotlin
    @ContributingAnnotation // <--- add this!
    @Target(CLASS)
    annotation class MyCustomAnnotation
    ```
2. Alternatively, if you don't control the annotation or otherwise cannot use option 1, you can
    specify custom annotations via the `kotlin-inject-anvil-contributing-annotations` KSP option.
    This option value is a colon-delimited string whose values are the canonical class names of
    your custom annotations.
    ```kotlin
    ksp {
      arg("kotlin-inject-anvil-contributing-annotations", "com.example.MyCustomAnnotation")
    }
    ```

### Disabling processors

In some occasions the behavior of certain built-in symbol processors of `kotlin-inject-anvil`
doesn't meet expectations or should be changed. The recommendation in this case is to disable
the built-in processors and create your own. A processor can be disabled through KSP options, e.g.

```groovy
ksp {
    arg("software.amazon.lastmile.kotlin.inject.anvil.processor.ContributesBindingProcessor", "disabled")
}
```

The key of the option must match the fully qualified name of the symbol processor and the value
must be `disabled`. All other values will keep the processor enabled. All built-in symbol
processors are part of
[this package](compiler/src/main/kotlin/software/amazon/lastmile/kotlin/inject/anvil/processor).

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

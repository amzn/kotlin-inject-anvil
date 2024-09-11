# kotlin-inject-anvil

[![Maven Central](https://img.shields.io/maven-central/v/software.amazon.lastmile.kotlin.inject.anvil/compiler.svg?label=Maven%20Central)](https://central.sonatype.com/search?smo=true&namespace=software.amazon.lastmile.kotlin.inject.anvil)
[![CI](https://github.com/amzn/kotlin-inject-anvil/workflows/CI/badge.svg)](https://github.com/amzn/kotlin-inject-anvil/actions?query=branch%3Amain)

[kotlin-inject](https://github.com/evant/kotlin-inject) is a compile-time dependency injection
framework for Kotlin Multiplatform similar to Dagger 2 for Java.
[Anvil](https://github.com/square/anvil) extends Dagger 2 to simplify dependency injection. This
project provides a similar feature set for the `kotlin-inject` framework.

The extensions provided by `kotlin-inject-anvil` allow you to contribute and automatically merge
component interfaces without explicit references in code.
```kotlin
@ContributesTo
@SingleInAppScope
interface AppIdComponent {
    @Provides
    fun provideAppId(): String = "demo app"
}

@Inject
@SingleInAppScope
@ContributesBinding
class RealAuthenticator : Authenticator

// The final kotlin-inject component.
@Component
@MergeComponent
@SingleInAppScope
interface AppComponent : AppComponentMerged
```
The generated code will ensure that `AppIdComponent` is a super type of `AppComponent` and the
provider method is known to the object graph. A binding for `RealAuthenticator` will be generated
and the type `Authenticator` can safely be injected anywhere. Note that neither `AppIdComponent`
nor `RealAuthenticator` are referenced anywhere else.

## Setup

The project comes with a KSP plugin and a runtime module:
```groovy
dependencies {
    kspCommonMainMetadata "software.amazon.lastmile.kotlin.inject.anvil:compiler:$version"
    commonMainImplementation "software.amazon.lastmile.kotlin.inject.anvil:runtime:$version"
}
```
For details how to setup KSP itself for multiplatform projects see the
[official documentation](https://kotlinlang.org/docs/ksp-multiplatform.html). The setup for
`kotlin-inject` is described [here](https://github.com/evant/kotlin-inject).

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
@ContributesTo
@SingleInAppScope
interface AppIdComponent {
    @Provides
    fun provideAppId(): String = "demo app"
}
```
In order to know in which component the contributed interface should be merged, the scope
annotation `@SingleInAppScope` must be added to the component interface.

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
@SingleInAppScope
class RealAuthenticator : Authenticator

@ContributesTo
@SingleInAppScope
interface AuthenticatorComponent {
    @Provides
    fun provideAuthenticator(authenticator: RealAuthenticator): Authenticator = authenticator
}
```
Note that `@ContributesTo` is leveraged to automatically add the interface to the final component.

However, this is still too much code and can be simplified further with `@ContributesBinding`:
```kotlin
@Inject
@SingleInAppScope
@ContributesBinding
class RealAuthenticator : Authenticator
```
`@ContributesBinding` will generate a provider method similar to the one above and automatically
add it to the final component.

##### Multi-bindings

`@ContributesBinding` supports `Set` multi-bindings via its `multibinding` parameter.

```kotlin
@Inject
@SingleInAppScope
@ContributesBinding(multibinding = true)
class LoggingInterceptor : Interceptor

@Component
@MergeComponent
@SingleInAppScope
abstract class AppComponent {
    // Will be contributed to this set multi-binding.
    abstract val interceptors: Set<Interceptor>
}
```

#### `@ContributesSubcomponent`

The `@ContributesSubcomponent` annotation allows you to define a subcomponent in any Gradle module,
but the final `@Component` will be generated when the parent component is merged.
```kotlin
@ContributesSubcomponent
@SingleInRendererScope
interface RendererComponent {

    @ContributesSubcomponent.Factory
    @SingleInAppScope
    interface Factory {
        fun createRendererComponent(): RendererComponent
    }
}
```
For more details on usage of the annotation and behavior
[see the documentation](runtime/src/commonMain/kotlin/software/amazon/lastmile/kotlin/inject/anvil/ContributesSubcomponent.kt).

### Merging

With `kotlin-inject` components are defined similar to the one below in order to instantiate your
object graph at runtime:
```kotlin
@Component
@SingleInAppScope
interface AppComponent
```
In order to pick up all contributions, you must add the `@MergeComponent` annotation:
```kotlin
@Component
@MergeComponent
@SingleInAppScope
interface AppComponent
```
This will generate a new interface `AppComponentMerged` in the same package as `AppComponent`.
This generated interface must be added as super type:
```kotlin
@Component
@MergeComponent
@SingleInAppScope
interface AppComponent : AppComponentMerged
```
With this setup any contribution is automatically merged. These steps have to be repeated for
every component in your project.

### Scopes

The plugin builds a connection between contributions and merged components through the scope.
How scopes function with `kotlin-inject` is described in the
[documentation](https://github.com/evant/kotlin-inject#scopes). The same scope annotations are
reused for this plugin. E.g. a scope may be defined as:
```kotlin
import me.tatarka.inject.annotations.Scope

@Scope
annotation class SingleInAppScope
```

## Sample

A [sample project](sample) for Android and iOS is available.

## Talk

The idea and more background about this library is covered in this
[public talk](https://ralf-wondratschek.com/presentation/extending-kotlin-inject.html).

## Advanced options

### Custom symbol processors

`kotlin-inject-anvil` is extensible and you can create your own annotations and KSP symbol
processors. In the generated code you can reference annotations from `kotlin-inject-anvil` itself
and build logic on top of them.

For example, assume this is your annotation:
```kotlin
@Target(CLASS)
annotation class MyCustomAnnotation
```

Your custom KSP symbol processor uses this annotation as trigger and generates following code:
```kotlin
@ContributesTo
@SingleInAppScope
interface MyCustomComponent {
    @Provides
    fun provideMyCustomType(): MyCustomType = ...
}
```
This generated component interface `MyCustomComponent` will be picked up by `kotlin-inject-anvil's`
symbol processors and contributed to the `@SingleInAppScope` due to the `@ContributesTo`
annotation.

**Custom annotations and symbol processors are very powerful and allow you to adjust
`kotlin-inject-anvil` to your needs and your codebase.**

There are two ways to indicate these to `kotlin-inject-anvil`. This is important for incremental compilation and multi-round support.

1. Annotate your annotation with the `@ContributingAnnotation` marker and run `kotlin-inject-anvil`'s compiler over the project the annotation is hosted in. This ensures the annotation is understood as a contributing annotation in all downstream usages of this annotation.
    ```kotlin
    @ContributingAnnotation // <--- add this!
    @Target(CLASS)
    annotation class MyCustomAnnotation
    ```
2. Alternatively, if you don't control the annotation or otherwise cannot use option 1, you can specify custom annotations via the `kotlin-inject-anvil-contributing-annotations` KSP option. This option value is a colon-delimited string whose values are the canonical class names of your custom annotations.
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

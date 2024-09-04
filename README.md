# kotlin-inject-anvil

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
    kspCommonMainMetadata "com.amazon.lastmile.kotlin.inject.anvil:compiler:$version"
    commonMainImplementation "com.amazon.lastmile.kotlin.inject.anvil:runtime:$version"
}
```
For details how to setup KSP itself for multiplatform projects see the
[official documentation](https://kotlinlang.org/docs/ksp-multiplatform.html). The setup for
`kotlin-inject` is described [here](https://github.com/evant/kotlin-inject).

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

`kotlin-inject-anvil` is extensible with custom annotations and KSP symbol processors. In
generated code you can reference annotations from `kotlin-inject-anvil` and that code will be
picked up.

For example, assume this annotation:
```kotlin
@Target(CLASS)
@ContributingAnnotation
annotation class MyCustomAnnotation
```
Note the `@ContributingAnnotation` marker, which is important for incremental compilation and
multi-round support.

A custom KSP symbol processor uses this annotation as trigger and generates following code:
```
@ContributesTo
@SingleInAppScope
interface MyCustomComponent {
    @Provides
    fun provideMyCustomType(): MyCustomType = ...
}
```
This generated component interface `MyCustomComponent` will be picked up and merged in the
corresponding component.

Custom annotations and symbol processors are very powerful and allow you to adjust
`kotlin-inject-anvil` to your needs and your codebase.

### Disabling processors

In some occasions the behavior of certain built-in symbol processors of `kotlin-inject-anvil`
doesn't meet expectations or should be changed. The recommendation in this case is to disable
the built-in processors and create your own. A processor can be disabled through KSP options, e.g.

```groovy
ksp {
    arg("com.amazon.lastmile.kotlin.inject.anvil.processor.ContributesBindingProcessor", "disabled")
}
```

The key of the option must match the fully qualified name of the symbol processor and the value
must be `disabled`. All other values will keep the processor enabled. All built-in symbol
processors are part of
[this package](compiler/src/main/kotlin/com/amazon/lastmile/kotlin/inject/anvil/processor).

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

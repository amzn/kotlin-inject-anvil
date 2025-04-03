# Change Log

## [Unreleased]

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

### Other Notes & Contributions


## [0.1.3] - 2025-04-02

### Added

* Added support for assisted injection with `@ContributesBinding`, see #92.

### Changed

* Upgraded Kotlin to `2.1.20` and KSP to `2.1.20-1.0.32`.


## [0.1.2] - 2024-12-23

### Added

* Support `replaces` attribute in `@ContributesBinding` and `@ContributesTo`, see #79.

### Changed

* Upgraded Kotlin to `2.1.0` and KSP to `2.1.0-1.0.29`.


## [0.1.1] - 2024-11-21

### Added

* Allow `@MergeComponent`s to be `public`, `internal`, or `protected`, see #77.

### Changed

* Upgraded the KSP to Kotlin `2.0.21-1.0.28`.

### Fixed

* Add an `override` modifier if a subcomponent factory parameter matches a property in the subcomponent, see #75.
* Add a companion object to generated KotlinInjectComponent classes, see #74.
* Copy all annotations from `expect fun` functions annotated with `@CreateComponent` to the generated `actual fun` functions to avoid a Kotlin compiler warning, see #72.
* Support component parameters for components annotated with `@MergeComponent`, see #76.


## [0.1.0] - 2024-11-04

### Added

* Provide option to hide the merged interface and make it no longer required to add the super type, see #8.
* Support `expect / actual` for generated factory functions using `@CreateComponent`, see #20.

### Changed

* Upgraded the project to Kotlin `2.0.21`.

### Fixed

* Support qualifiers on the factory functions when contributing subcomponents and don't silently drop them, see #58.
* Generate binding method for subcomponent factory, see #49.
* Update module name for KLib for common sources, see #63.


## [0.0.5] - 2024-10-07

### Changed

* **BREAKING CHANGE:** Enforce scope parameter on all `@Contributes*` annotations and stop using the kotlin-inject scope implicitly, see #36.
* Made the generated property when using `@ContributingAnnotation` private. There is no reason to expose the property on the compile classpath.
* Support excluding contributions from custom annotations as long as they have the `@Origin` annotation added to generated code.

### Fixed

* Allow adding annotations from `:runtime-optional` to abstract properties.


## [0.0.4] - 2024-09-16

### Fixed

* Allow adding annotations from `:runtime-optional` to property getters and value parameters.


## [0.0.3] - 2024-09-13

### Added

- Added new artifact `:runtime-optional`, which provides access to `@SingleIn`, `@ForScope` and `AppScope`, see #16.
- Added support for scopes with parameters, e.g. to support `@SingleIn(AppScope::class)` and `@ContributesTo(AppScope::class)`, see #1.
- Allow specifying custom contributing annotations via KSP option instead of using `@ContributingAnnotation`, see #24.

### Changed

- Updated the documentation and decided to recommend scope references as parameter to contribute and merge types. In other words: we prefer using the `@SingleIn(SomeScope::class)` annotation and explicitly declaring the scope on the `@Contribute*(SomeScope::class)` annotations. Support for the old way may go away, see #36.

### Removed

- Removed `mingwX64()` target, because `kotlin-inject` doesn't support it.


## [0.0.2] - 2024-09-11

### Added

- Add multi-binding support to `@ContributesBinding` via a `multibinding` parameter.
- Add `mingwX64()` target to runtime.


## [0.0.1] - 2024-09-06

- Initial release.

[Unreleased]: https://github.com/amzn/kotlin-inject-anvil/compare/0.1.3...HEAD
[0.1.3]: https://github.com/square/anvil/releases/tag/0.1.3
[0.1.2]: https://github.com/square/anvil/releases/tag/0.1.2
[0.1.1]: https://github.com/square/anvil/releases/tag/0.1.1
[0.1.0]: https://github.com/square/anvil/releases/tag/0.1.0
[0.0.5]: https://github.com/square/anvil/releases/tag/0.0.5
[0.0.4]: https://github.com/square/anvil/releases/tag/0.0.4
[0.0.3]: https://github.com/square/anvil/releases/tag/0.0.3
[0.0.2]: https://github.com/square/anvil/releases/tag/0.0.2
[0.0.1]: https://github.com/square/anvil/releases/tag/0.0.1

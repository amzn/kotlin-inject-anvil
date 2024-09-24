# Change Log

## [Unreleased]

### Added

### Changed

* **BREAKING CHANGE:** Enforce scope parameter on all `@Contributes*` annotations and stop using the kotlin-inject scope implicitly, see #36.
* Made the generated property when using `@ContributingAnnotation` private. There is no reason to expose the property on the compile classpath.

### Deprecated

### Removed

### Fixed

### Security

### Other Notes & Contributions


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

[Unreleased]: https://github.com/amzn/kotlin-inject-anvil/compare/0.0.4...HEAD
[0.0.4]: https://github.com/square/anvil/releases/tag/0.0.4
[0.0.3]: https://github.com/square/anvil/releases/tag/0.0.3
[0.0.2]: https://github.com/square/anvil/releases/tag/0.0.2
[0.0.1]: https://github.com/square/anvil/releases/tag/0.0.1

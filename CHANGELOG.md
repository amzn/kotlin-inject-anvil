# Change Log

## [Unreleased]

### Added

- Added new artifact `:runtime-optional`, which provides access to `@SingleIn`, `@ForScope` and `AppScope`, see #16.
- Added support for scopes with parameters, e.g. to support `@SingleIn(AppScope::class)` and `@ContributesTo(AppScope::class)`, see #1.
- Allow specifying custom contributing annotations via KSP option instead of using `@ContributingAnnotation`.

### Changed

- Updated the documentation and decided to recommend scope references as parameter to contribute and merge types. In other words: we prefer using the `@SingleIn(SomeScope::class)` annotation and explicitly declaring the scope on the `@Contribute*(SomeScope::class)` annotations. Support for the old way may go away, see #36.

### Deprecated

### Removed

- Removed `mingwX64()` target, because `kotlin-inject` doesn't support it.

### Fixed

### Security

### Other Notes & Contributions


## [0.0.2] - 2024-09-11

### Added

- Add multi-binding support to `@ContributesBinding` via a `multibinding` parameter.
- Add `mingwX64()` target to runtime.


## [0.0.1] - 2024-09-06

- Initial release.

[Unreleased]: https://github.com/amzn/kotlin-inject-anvil/compare/0.0.2...HEAD
[0.0.2]: https://github.com/square/anvil/releases/tag/0.0.2
[0.0.1]: https://github.com/square/anvil/releases/tag/0.0.1

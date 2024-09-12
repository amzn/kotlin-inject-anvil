package software.amazon.lastmile.kotlin.inject.anvil

/**
 * Scope marker for the application scope, which gets created when the app launches. The
 * application scope stays alive as long as the app itself.
 *
 * To make an object a singleton within the application scope, use this marker class in conjunction
 * with [SingleIn], e.g.
 * ```
 * @Inject
 * @SingleIn(AppScope::class)
 * class MyClass : SuperType {
 *     ...
 * }
 * ```
 *
 * To contribute a component interface to the application scope, use the `ContributesTo`
 * annotation:
 * ```
 * @ContributesTo(AppScope::class)
 * interface AbcComponent {
 *     @Provides fun provideAbc(): Abc = ...
 *
 *     val abc: Abc
 * }
 * ```
 *
 * To contribute a binding to the application scope, use the `ContributesBinding` annotation:
 * ```
 * // Allows you to inject `Service` without adding any Dagger module.
 * @Inject
 * @ContributesBinding(AppScope::class)
 * @SingleIn(AppScope::class)
 * class ServiceImpl : Service {
 *     ...
 * }
 * ```
 */
public abstract class AppScope private constructor()

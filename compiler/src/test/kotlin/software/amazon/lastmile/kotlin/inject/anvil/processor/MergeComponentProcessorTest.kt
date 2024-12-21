@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.compile
import software.amazon.lastmile.kotlin.inject.anvil.componentInterface
import software.amazon.lastmile.kotlin.inject.anvil.inner
import software.amazon.lastmile.kotlin.inject.anvil.kotlinInjectComponent
import software.amazon.lastmile.kotlin.inject.anvil.mergedComponent
import software.amazon.lastmile.kotlin.inject.anvil.newComponent

class MergeComponentProcessorTest {

    @Test
    fun `component interfaces are merged`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @SingleIn(AppScope::class)
            class Impl : Base {
            
                @ContributesTo(AppScope::class)
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl

                    val string: String
                }
            }

            @ContributesTo(AppScope::class)
            interface StringComponent {
                @Provides fun provideString(): String = "abc"
            }

            @Component
            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            abstract class ComponentInterface : ComponentInterfaceMerged {
                abstract val base: Base
            }
            """,
        ) {
            assertThat(componentInterface.mergedComponent).isNotNull()

            assertThat(stringComponent.isAssignableFrom(componentInterface)).isTrue()
            assertThat(implComponent.isAssignableFrom(componentInterface)).isTrue()
        }
    }

    @Test
    fun `component interfaces are merged into inner class`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @SingleIn(AppScope::class)
            class Impl : Base {
            
                @ContributesTo(AppScope::class)
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl

                    val string: String
                }
            }

            @ContributesTo(AppScope::class)
            interface StringComponent {
                @Provides fun provideString(): String = "abc"
            }

            interface ComponentInterface {
                @Component
                @MergeComponent(AppScope::class)
                @SingleIn(AppScope::class)
                interface Inner : ComponentInterfaceInnerMerged
            }
            """,
        ) {
            assertThat(componentInterface.inner.mergedComponent).isNotNull()

            assertThat(stringComponent.isAssignableFrom(componentInterface.inner)).isTrue()
            assertThat(implComponent.isAssignableFrom(componentInterface.inner)).isTrue()
        }
    }

    @Test
    fun `contributed bindings are merged`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @ContributesBinding(AppScope::class)
            class Impl : Base

            @Component
            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            abstract class ComponentInterface : ComponentInterfaceMerged {
                abstract val base: Base
            }
            """,
        ) {
            assertThat(componentInterface.mergedComponent).isNotNull()

            assertThat(
                classLoader.loadClass("$LOOKUP_PACKAGE.SoftwareAmazonTestImpl")
                    .isAssignableFrom(componentInterface),
            ).isTrue()
        }
    }

    @Test
    fun `component interfaces from previous compilations are merged`() {
        val previousCompilation = compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @SingleIn(AppScope::class)
            class Impl : Base {
            
                @ContributesTo(AppScope::class)
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl

                    val string: String
                }
            }
            """,
        )

        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            @ContributesTo(AppScope::class)
            interface StringComponent {
                @Provides fun provideString(): String = "abc"
            }

            @Component
            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            abstract class ComponentInterface : ComponentInterfaceMerged {
                abstract val base: Base
            }
            """,
            previousCompilationResult = previousCompilation,
        ) {
            assertThat(componentInterface.mergedComponent).isNotNull()

            assertThat(stringComponent.isAssignableFrom(componentInterface)).isTrue()
            assertThat(implComponent.isAssignableFrom(componentInterface)).isTrue()
        }
    }

    @Test
    fun `component interfaces with a different scope are not merged`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @SingleIn(AppScope::class)
            class Impl : Base {
            
                @ContributesTo(AppScope::class)
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl
                }
            }

            @ContributesTo(Unit::class)
            interface StringComponent {
                @Provides fun provideString(): String = "abc"
            }

            @Component
            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            abstract class ComponentInterface : ComponentInterfaceMerged {
                abstract val base: Base
            }
            """,
        ) {
            assertThat(componentInterface.mergedComponent).isNotNull()

            assertThat(stringComponent.isAssignableFrom(componentInterface)).isFalse()
            assertThat(implComponent.isAssignableFrom(componentInterface)).isTrue()
        }
    }

    @Test
    fun `component interfaces can be excluded`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @SingleIn(AppScope::class)
            class Impl : Base {
            
                @ContributesTo(AppScope::class)
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl
                }
            }

            @ContributesTo(AppScope::class)
            interface StringComponent {
                val string: String
            }

            @Component
            @MergeComponent(AppScope::class, exclude = [StringComponent::class])
            @SingleIn(AppScope::class)
            abstract class ComponentInterface : ComponentInterfaceMerged {
                abstract val base: Base
            }
            """,
        ) {
            assertThat(componentInterface.mergedComponent).isNotNull()

            assertThat(stringComponent.isAssignableFrom(componentInterface)).isFalse()
            assertThat(implComponent.isAssignableFrom(componentInterface)).isTrue()
        }
    }

    @Test
    fun `component interfaces can be excluded without named parameter`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @SingleIn(AppScope::class)
            class Impl : Base {
            
                @ContributesTo(AppScope::class)
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl
                }
            }

            @ContributesTo(AppScope::class)
            interface StringComponent {
                val string: String
            }

            @Component
            @MergeComponent(AppScope::class, [StringComponent::class])
            @SingleIn(AppScope::class)
            abstract class ComponentInterface : ComponentInterfaceMerged {
                abstract val base: Base
            }
            """,
        ) {
            assertThat(componentInterface.mergedComponent).isNotNull()

            assertThat(stringComponent.isAssignableFrom(componentInterface)).isFalse()
            assertThat(implComponent.isAssignableFrom(componentInterface)).isTrue()
        }
    }

    @Test
    fun `contributed bindings can be excluded`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @SingleIn(AppScope::class)
            @ContributesBinding(AppScope::class)
            class Impl : Base

            @Component
            @MergeComponent(AppScope::class, exclude = [Impl::class])
            @SingleIn(AppScope::class)
            abstract class ComponentInterface : ComponentInterfaceMerged {
                abstract val base: Base
            }
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "e: [ksp] Cannot find an @Inject constructor or provider for: " +
                    "software.amazon.test.Base",
            )
        }
    }

    @Test
    fun `the super type must be declared`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            @Component
            @MergeComponent(AppScope::class)
            abstract class ComponentInterface
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "ComponentInterface is annotated with @MergeComponent and " +
                    "@Component. It's required to add ComponentInterfaceMerged as super " +
                    "type to ComponentInterface. If you don't want to add the super manually, " +
                    "then you must remove the @Component annotation.",
            )
        }
    }

    @Test
    fun `using a different kotlin-inject scope with marker scopes is allowed`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import me.tatarka.inject.annotations.Scope
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            @Scope
            annotation class Singleton

            interface Base

            @Inject
            @Singleton
            class Impl : Base {
            
                @ContributesTo(AppScope::class)
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl
                }
            }

            // Note this is contributed to a different scope.
            @ContributesTo(Unit::class)
            interface StringComponent {
                @Provides fun provideString(): String = "abc"
            }

            @Component
            @MergeComponent(AppScope::class)
            @Singleton
            abstract class ComponentInterface : ComponentInterfaceMerged {
                abstract val base: Base
            }
            """,
        ) {
            assertThat(componentInterface.mergedComponent).isNotNull()

            assertThat(implComponent.isAssignableFrom(componentInterface)).isTrue()
            assertThat(stringComponent.isAssignableFrom(componentInterface)).isFalse()
        }
    }

    @Test
    fun `contributed bindings can be replaced from another contributed binding`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            interface Base

            @Inject
            @ContributesBinding(AppScope::class)
            class Impl : Base

            @Inject
            @ContributesBinding(AppScope::class, replaces = [Impl::class])
            class Fake : Base

            @MergeComponent(AppScope::class)
            abstract class ComponentInterface {
                abstract val base: Base
            }
            """,
        ) {
            val component = componentInterface.kotlinInjectComponent.newComponent<Any>()

            val implValue = component::class.java.methods
                .single { it.name == "getBase" }
                .invoke(component)

            assertThat(impl.isInstance(implValue)).isFalse()
            assertThat(fake.isInstance(implValue)).isTrue()
        }
    }

    @Test
    fun `contributed components can be replaced from another contributed binding`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            interface Base

            @Inject
            class Impl : Base {
                @ContributesTo(AppScope::class)
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl                
                }
            }


            @Inject
            @ContributesBinding(AppScope::class, replaces = [Impl.Component::class])
            class Fake : Base

            @MergeComponent(AppScope::class)
            abstract class ComponentInterface {
                abstract val base: Base
            }
            """,
        ) {
            val component = componentInterface.kotlinInjectComponent.newComponent<Any>()

            val implValue = component::class.java.methods
                .single { it.name == "getBase" }
                .invoke(component)

            assertThat(impl.isInstance(implValue)).isFalse()
            assertThat(fake.isInstance(implValue)).isTrue()
        }
    }

    @Test
    fun `contributed bindings can be replaced from another contributed component`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            interface Base

            @Inject
            @ContributesBinding(AppScope::class)
            class Impl : Base

            @Inject
            class Fake : Base {
                @ContributesTo(AppScope::class, replaces = [Impl::class])
                interface Component {
                    @Provides fun provideFake(fake: Fake): Base = fake                
                }
            }

            @MergeComponent(AppScope::class)
            abstract class ComponentInterface {
                abstract val base: Base
            }
            """,
        ) {
            val component = componentInterface.kotlinInjectComponent.newComponent<Any>()

            val implValue = component::class.java.methods
                .single { it.name == "getBase" }
                .invoke(component)

            assertThat(impl.isInstance(implValue)).isFalse()
            assertThat(fake.isInstance(implValue)).isTrue()
        }
    }

    @Test
    fun `contributed components can be replaced from another contributed component`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            interface Base

            @Inject
            class Impl : Base {
                @ContributesTo(AppScope::class)
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl                
                }
            }


            @Inject
            class Fake : Base {
                @ContributesTo(AppScope::class, replaces = [Impl.Component::class])
                interface Component {
                    @Provides fun provideFake(fake: Fake): Base = fake                
                }
            }

            @MergeComponent(AppScope::class)
            abstract class ComponentInterface {
                abstract val base: Base
            }
            """,
        ) {
            val component = componentInterface.kotlinInjectComponent.newComponent<Any>()

            val implValue = component::class.java.methods
                .single { it.name == "getBase" }
                .invoke(component)

            assertThat(impl.isInstance(implValue)).isFalse()
            assertThat(fake.isInstance(implValue)).isTrue()
        }
    }

    @Test
    fun `a replaced binding cannot replace other bindings`() {
        compile(
            """
            package software.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            interface Base

            @Inject
            @ContributesBinding(AppScope::class)
            class Impl : Base

            @Inject
            @ContributesBinding(AppScope::class, replaces = [Impl::class])
            class Fake : Base

            @ContributesTo(AppScope::class, replaces = [Fake::class])
            interface SomeInterface

            @MergeComponent(AppScope::class)
            abstract class ComponentInterface {
                abstract val base: Base
            }
            """,
        ) {
            val component = componentInterface.kotlinInjectComponent.newComponent<Any>()

            val implValue = component::class.java.methods
                .single { it.name == "getBase" }
                .invoke(component)

            assertThat(impl.isInstance(implValue)).isTrue()
            assertThat(fake.isInstance(implValue)).isFalse()
        }
    }

    private val JvmCompilationResult.stringComponent: Class<*>
        get() = classLoader.loadClass("software.amazon.test.StringComponent")

    private val JvmCompilationResult.implComponent: Class<*>
        get() = classLoader.loadClass("software.amazon.test.Impl\$Component")

    private val JvmCompilationResult.impl: Class<*>
        get() = classLoader.loadClass("software.amazon.test.Impl")

    private val JvmCompilationResult.fake: Class<*>
        get() = classLoader.loadClass("software.amazon.test.Fake")
}

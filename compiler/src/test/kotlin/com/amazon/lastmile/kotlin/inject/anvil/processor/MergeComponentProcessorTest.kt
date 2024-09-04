@file:OptIn(ExperimentalCompilerApi::class)

package com.amazon.lastmile.kotlin.inject.anvil.processor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import com.amazon.lastmile.kotlin.inject.anvil.compile
import com.amazon.lastmile.kotlin.inject.anvil.componentInterface
import com.amazon.lastmile.kotlin.inject.anvil.inner
import com.amazon.lastmile.kotlin.inject.anvil.mergedComponent
import com.amazon.lastmile.kotlin.inject.anvil.otherScopeSource
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

class MergeComponentProcessorTest {

    @Test
    fun `component interfaces are merged`() {
        compile(
            """
            package com.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            interface Base

            @Inject
            @OtherScope
            class Impl : Base {
            
                @ContributesTo
                @OtherScope
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl

                    val string: String
                }
            }

            @ContributesTo
            @OtherScope
            interface StringComponent {
                @Provides fun provideString(): String = "abc"
            }

            @Component
            @MergeComponent
            @OtherScope
            abstract class ComponentInterface : ComponentInterfaceMerged {
                abstract val base: Base
            }
            """,
            otherScopeSource,
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
            package com.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            interface Base

            @Inject
            @OtherScope
            class Impl : Base {
            
                @ContributesTo
                @OtherScope
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl

                    val string: String
                }
            }

            @ContributesTo
            @OtherScope
            interface StringComponent {
                @Provides fun provideString(): String = "abc"
            }

            interface ComponentInterface {
                @Component
                @MergeComponent
                @OtherScope
                interface Inner : ComponentInterfaceInnerMerged
            }
            """,
            otherScopeSource,
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
            package com.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            interface Base

            @Inject
            @OtherScope
            @ContributesBinding
            class Impl : Base

            @Component
            @MergeComponent
            @OtherScope
            abstract class ComponentInterface : ComponentInterfaceMerged {
                abstract val base: Base
            }
            """,
            otherScopeSource,
        ) {
            assertThat(componentInterface.mergedComponent).isNotNull()

            assertThat(
                classLoader.loadClass("$LOOKUP_PACKAGE.ComAmazonTestImpl")
                    .isAssignableFrom(componentInterface),
            ).isTrue()
        }
    }

    @Test
    fun `component interfaces from previous compilations are merged`() {
        val previousCompilation = compile(
            """
            package com.amazon.test
                            
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesTo

            interface Base

            @Inject
            @OtherScope
            class Impl : Base {
            
                @ContributesTo
                @OtherScope
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl

                    val string: String
                }
            }
            """,
            otherScopeSource,
        )

        compile(
            """
            package com.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            @ContributesTo
            @OtherScope
            interface StringComponent {
                @Provides fun provideString(): String = "abc"
            }

            @Component
            @MergeComponent
            @OtherScope
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
    fun `a scope must be present`() {
        compile(
            """
            package com.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            @Component
            @MergeComponent
            abstract class ComponentInterface : ComponentInterfaceMerged
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains("Couldn't find scope annotation for ComponentInterface.")
        }
    }

    @Test
    fun `component interfaces with a different scope are not merged`() {
        compile(
            """
            package com.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            interface Base

            @Inject
            @OtherScope
            class Impl : Base {
            
                @ContributesTo
                @OtherScope
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl
                }
            }

            @ContributesTo
            @OtherScope2
            interface StringComponent {
                @Provides fun provideString(): String = "abc"
            }

            @Component
            @MergeComponent
            @OtherScope
            abstract class ComponentInterface : ComponentInterfaceMerged {
                abstract val base: Base
            }
            """,
            otherScopeSource,
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
            package com.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            interface Base

            @Inject
            @OtherScope
            class Impl : Base {
            
                @ContributesTo
                @OtherScope
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl
                }
            }

            @ContributesTo
            @OtherScope
            interface StringComponent {
                val string: String
            }

            @Component
            @MergeComponent(exclude = [StringComponent::class])
            @OtherScope
            abstract class ComponentInterface : ComponentInterfaceMerged {
                abstract val base: Base
            }
            """,
            otherScopeSource,
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
            package com.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            interface Base

            @Inject
            @OtherScope
            class Impl : Base {
            
                @ContributesTo
                @OtherScope
                interface Component {
                    @Provides fun provideImpl(impl: Impl): Base = impl
                }
            }

            @ContributesTo
            @OtherScope
            interface StringComponent {
                val string: String
            }

            @Component
            @MergeComponent([StringComponent::class])
            @OtherScope
            abstract class ComponentInterface : ComponentInterfaceMerged {
                abstract val base: Base
            }
            """,
            otherScopeSource,
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
            package com.amazon.test
                            
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Provides
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent

            interface Base

            @Inject
            @SingleInAppScope
            @ContributesBinding
            class Impl : Base

            @Component
            @MergeComponent(exclude = [Impl::class])
            @SingleInAppScope
            abstract class ComponentInterface : ComponentInterfaceMerged {
                abstract val base: Base
            }
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "e: [ksp] Cannot find an @Inject constructor or provider for: com.amazon.test.Base",
            )
        }
    }

    private val JvmCompilationResult.stringComponent: Class<*>
        get() = classLoader.loadClass("com.amazon.test.StringComponent")

    private val JvmCompilationResult.implComponent: Class<*>
        get() = classLoader.loadClass("com.amazon.test.Impl\$Component")
}

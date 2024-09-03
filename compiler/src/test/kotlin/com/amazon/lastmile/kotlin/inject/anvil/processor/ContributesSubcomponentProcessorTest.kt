@file:OptIn(ExperimentalCompilerApi::class)

package com.amazon.lastmile.kotlin.inject.anvil.processor

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.amazon.lastmile.kotlin.inject.anvil.Compilation
import com.amazon.lastmile.kotlin.inject.anvil.compile
import com.amazon.lastmile.kotlin.inject.anvil.componentInterface
import com.amazon.lastmile.kotlin.inject.anvil.newComponent
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

class ContributesSubcomponentProcessorTest {

    @Test
    fun `a contributed subcomponent is generated when the parent is merged`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Provides

            @MergeComponent
            @Component
            @ParentScope
            interface ComponentInterface : ComponentInterfaceMerged

            @ContributesSubcomponent
            @ChildScope
            interface OtherComponent {
                @ContributesSubcomponent.Factory
                @ParentScope
                interface Parent {
                    fun otherComponent(): OtherComponent 
                }
            }
            
            @ContributesTo
            @ChildScope
            interface ChildComponent {
                @Provides fun provideString(): String = "abc"

                val string: String
            }
            """,
            scopesSource,
        ) {
            val component = componentInterface.newComponent<Any>()
            val childComponent = component::class.java.methods
                .single { it.name == "otherComponent" }
                .invoke(component)

            assertThat(childComponent).isNotNull()

            val string = childComponent::class.java.methods
                .single { it.name == "getString" }
                .invoke(childComponent)

            assertThat(string).isEqualTo("abc")
        }
    }

    @Test
    fun `contributions to the child scope from a previous compilation are picked up`() {
        val previousResult1 = compile(scopesSource)

        val previousResult2 = compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import me.tatarka.inject.annotations.Provides

            @ContributesTo
            @ChildScope
            interface ChildComponent {
                @Provides fun provideString(): String = "abc"

                val string: String
            }
            """,
            previousCompilationResult = previousResult1,
        )

        val previousResult3 = compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent

            @ContributesSubcomponent
            @ChildScope
            interface OtherComponent {
                @ContributesSubcomponent.Factory
                @ParentScope
                interface Parent {
                    fun otherComponent(): OtherComponent 
                }
            }
            """,
            previousCompilationResult = previousResult1,
        )

        Compilation()
            .apply {
                addPreviousCompilationResult(previousResult1)
                addPreviousCompilationResult(previousResult2)
                addPreviousCompilationResult(previousResult3)
            }
            .configureKotlinInjectAnvilProcessor()
            .compile(
                """
                package com.amazon.test
        
                import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent
                import me.tatarka.inject.annotations.Component
    
                @MergeComponent
                @Component
                @ParentScope
                interface ComponentInterface : ComponentInterfaceMerged
                """,
            )
            .run {
                assertThat(exitCode).isEqualTo(OK)

                val component = componentInterface.newComponent<Any>()
                val childComponent = component::class.java.methods
                    .single { it.name == "otherComponent" }
                    .invoke(component)

                assertThat(childComponent).isNotNull()

                val string = childComponent::class.java.methods
                    .single { it.name == "getString" }
                    .invoke(childComponent)

                assertThat(string).isEqualTo("abc")
            }
    }

    @Test
    fun `a subcomponent can be contributed to multiple scopes`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Provides

            @MergeComponent
            @Component
            @ParentScope
            interface ComponentInterface : ComponentInterfaceMerged

            @MergeComponent
            @Component
            @ParentScope
            interface ComponentInterface2 : ComponentInterface2Merged

            @ContributesSubcomponent
            @ChildScope
            interface OtherComponent {
                @ContributesSubcomponent.Factory
                @ParentScope
                interface Parent {
                    fun otherComponent(): OtherComponent 
                }
            }
            
            @ContributesTo
            @ChildScope
            interface ChildComponent {
                @Provides fun provideString(): String = "abc"

                val string: String
            }
            """,
            scopesSource,
        ) {
            val components = listOf<Any>(
                componentInterface.newComponent(),
                componentInterface2.newComponent(),
            )
            components.forEach { component ->
                val childComponent = component::class.java.methods
                    .single { it.name == "otherComponent" }
                    .invoke(component)

                assertThat(childComponent).isNotNull()

                val string = childComponent::class.java.methods
                    .single { it.name == "getString" }
                    .invoke(childComponent)

                assertThat(string).isEqualTo("abc")
            }
        }
    }

    @Test
    fun `a contributed subcomponent can be excluded`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import me.tatarka.inject.annotations.Component

            @MergeComponent(exclude = [OtherComponent::class])
            @Component
            @ParentScope
            interface ComponentInterface : ComponentInterfaceMerged

            @ContributesSubcomponent
            @ChildScope
            interface OtherComponent {
                @ContributesSubcomponent.Factory
                @ParentScope
                interface Parent {
                    fun otherComponent(): OtherComponent 
                }
            }
            """,
            scopesSource,
        ) {
            val component = componentInterface.newComponent<Any>()

            assertThat(
                component::class.java.methods.filter { it.name == "otherComponent" },
            ).isEmpty()
        }
    }

    @Test
    fun `contributed subcomponents can be chained`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Provides

            @MergeComponent
            @Component
            @ParentScope
            interface ComponentInterface : ComponentInterfaceMerged

            @ContributesSubcomponent
            @ChildScope
            interface ComponentInterface2 {
                @ContributesSubcomponent.Factory
                @ParentScope
                interface Parent {
                    fun componentInterface2(): ComponentInterface2 
                }
            }

            @ContributesSubcomponent
            @GrandChildScope
            interface OtherComponent {
                @ContributesSubcomponent.Factory
                @ChildScope
                interface Parent {
                    fun otherComponent(): OtherComponent 
                }
            }
            
            @ContributesTo
            @GrandChildScope
            interface ChildComponent {
                @Provides fun provideString(): String = "abc"

                val string: String
            }
            """,
            scopesSource,
        ) {
            val component = componentInterface.newComponent<Any>()
            val childComponent = component::class.java.methods
                .single { it.name == "componentInterface2" }
                .invoke(component)

            assertThat(childComponent).isNotNull()

            val grandChildComponent = childComponent::class.java.methods
                .single { it.name == "otherComponent" }
                .invoke(childComponent)

            assertThat(grandChildComponent).isNotNull()

            val string = grandChildComponent::class.java.methods
                .single { it.name == "getString" }
                .invoke(grandChildComponent)

            assertThat(string).isEqualTo("abc")
        }
    }

    @Test
    fun `the factory function accepts parameters and the parameters are bound in the component`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import com.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Provides

            @MergeComponent
            @Component
            @ParentScope
            interface ComponentInterface : ComponentInterfaceMerged

            @ContributesSubcomponent
            @ChildScope
            interface OtherComponent {
                @ContributesSubcomponent.Factory
                @ParentScope
                interface Parent {
                    fun otherComponent(stringArg: String, intArg: Int): OtherComponent 
                }
            }
            
            @ContributesTo
            @ChildScope
            interface ChildComponent {
                val string: String
                val int: Int
            }
            """,
            scopesSource,
        ) {
            val component = componentInterface.newComponent<Any>()
            val childComponent = component::class.java.methods
                .single { it.name == "otherComponent" }
                .invoke(component, "some string", 5)

            assertThat(childComponent).isNotNull()

            assertThat(
                childComponent::class.java.methods
                    .single { it.name == "getString" }
                    .invoke(childComponent),
            ).isEqualTo("some string")
            assertThat(
                childComponent::class.java.methods
                    .single { it.name == "getInt" }
                    .invoke(childComponent),
            ).isEqualTo(5)
        }
    }

    private val JvmCompilationResult.componentInterface2: Class<*>
        get() = classLoader.loadClass("com.amazon.test.ComponentInterface2")

    @Language("kotlin")
    internal val scopesSource = """
        package com.amazon.test
        
        import me.tatarka.inject.annotations.Scope
    
        @Scope
        annotation class ParentScope
    
        @Scope
        annotation class ChildScope
    
        @Scope
        annotation class GrandChildScope
    """
}

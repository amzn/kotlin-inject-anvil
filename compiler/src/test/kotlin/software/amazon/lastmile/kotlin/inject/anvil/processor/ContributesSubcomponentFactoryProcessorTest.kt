@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.compile
import software.amazon.lastmile.kotlin.inject.anvil.generatedComponent
import software.amazon.lastmile.kotlin.inject.anvil.isAnnotatedWith
import software.amazon.lastmile.kotlin.inject.anvil.origin
import kotlin.reflect.KClass

class ContributesSubcomponentFactoryProcessorTest {

    @Test
    fun `a component interface is generated in the lookup package for a contributed subcomponent factory`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent

            @ContributesSubcomponent
            @ChildScope
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory
                @ParentScope
                interface Factory {
                    fun createSubcomponentInterface(): SubcomponentInterface
                }          
            }
            """,
            scopesSource,
        ) {
            val generatedComponent = subcomponent.factory.generatedComponent

            assertThat(generatedComponent.packageName).isEqualTo(LOOKUP_PACKAGE)
            assertThat(generatedComponent.interfaces).containsExactly(subcomponent.factory)
            assertThat(generatedComponent).isAnnotatedWith(parentScope)
            assertThat(generatedComponent.origin).isEqualTo(subcomponent.factory)
        }
    }

    @Test
    fun `the factory function must have a single function - multiple abstract functions`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent

            @ContributesSubcomponent
            @ChildScope
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory
                @ParentScope
                interface Factory {
                    fun createSubcomponentInterface(): SubcomponentInterface
                    fun createSubcomponentInterface2(): SubcomponentInterface
                }          
            }
            """,
            scopesSource,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Factory interfaces must contain exactly one function with the " +
                    "subcomponent as return type.",
            )
        }
    }

    @Test
    fun `the factory function must have a single function - multiple functions non-abstract are allowed`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent

            @ContributesSubcomponent
            @ChildScope
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory
                @ParentScope
                interface Factory {
                    fun createSubcomponentInterface(): SubcomponentInterface
                    fun createSubcomponentInterface2(): SubcomponentInterface = 
                        createSubcomponentInterface()

                    val string: String
                }          
            }
            """,
            scopesSource,
        ) {
            assertThat(subcomponent.factory.generatedComponent).isNotNull()
        }
    }

    @Test
    fun `the factory function must have a single function - no functions`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent

            @ContributesSubcomponent
            @ChildScope
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory
                @ParentScope
                interface Factory         
            }
            """,
            scopesSource,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Factory interfaces must contain exactly one function with the " +
                    "subcomponent as return type.",
            )
        }
    }

    @Test
    fun `the factory function must have a single function - wrong return type`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent

            @ContributesSubcomponent
            @ChildScope
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory
                @ParentScope
                interface Factory {
                    fun createSubcomponentInterface(): Any
                }          
            }
            """,
            scopesSource,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Factory interfaces must contain exactly one function with the " +
                    "subcomponent as return type.",
            )
        }
    }

    @Test
    fun `the factory function must have a single function - no return type`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent

            @ContributesSubcomponent
            @ChildScope
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory
                @ParentScope
                interface Factory {
                    fun createSubcomponentInterface()
                }          
            }
            """,
            scopesSource,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Factory interfaces must contain exactly one function with the " +
                    "subcomponent as return type.",
            )
        }
    }

    @Test
    fun `the factory must be an inner class of a subcomponent - not a subcomponent`() {
        compile(
            """
            package software.amazon.test

            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
    
            @ChildScope
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory
                @ParentScope
                interface Factory {
                    fun createSubcomponentInterface(): SubcomponentInterface
                }          
            }
            """,
            scopesSource,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Factory interfaces must be inner classes of the contributed " +
                    "subcomponent, which need to be annotated with @ContributesSubcomponent.",
            )
        }
    }

    @Test
    fun `the factory must be an inner class of a subcomponent - no outer class`() {
        compile(
            """
            package software.amazon.test

            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
    
            @ContributesSubcomponent.Factory
            @ParentScope
            interface Factory {
                fun createSubcomponentInterface(): Factory
            }          
            """,
            scopesSource,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Factory interfaces must be inner classes of the contributed subcomponent.",
            )
        }
    }

    @Test
    fun `a subcomponent must have a factory - no factory`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent

            @ContributesSubcomponent
            @ChildScope
            interface SubcomponentInterface {
                @ParentScope
                interface Factory {
                    fun createSubcomponentInterface(): SubcomponentInterface
                }          
            }
            """,
            scopesSource,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Contributed subcomponent must have exactly one inner interface " +
                    "annotated with @ContributesSubcomponent.Factory.",
            )
        }
    }

    @Test
    fun `a subcomponent must have a factory - multiple factories`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent

            @ContributesSubcomponent
            @ChildScope
            interface SubcomponentInterface {
                @ParentScope
                @ContributesSubcomponent.Factory
                interface Factory1 {
                    fun createSubcomponentInterface1(): SubcomponentInterface
                }          
                @ParentScope
                @ContributesSubcomponent.Factory
                interface Factory2 {
                    fun createSubcomponentInterface2(): SubcomponentInterface
                }          
            }
            """,
            scopesSource,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Contributed subcomponent must have exactly one inner interface " +
                    "annotated with @ContributesSubcomponent.Factory.",
            )
        }
    }

    private val JvmCompilationResult.subcomponent: Class<*>
        get() = classLoader.loadClass("software.amazon.test.SubcomponentInterface")

    @Suppress("UNCHECKED_CAST")
    private val JvmCompilationResult.parentScope: KClass<out Annotation>
        get() = classLoader.loadClass("software.amazon.test.ParentScope").kotlin
            as KClass<out Annotation>

    private val Class<*>.factory: Class<*>
        get() = classes.single { it.simpleName == "Factory" }

    @Language("kotlin")
    internal val scopesSource = """
        package software.amazon.test
        
        import me.tatarka.inject.annotations.Scope
    
        @Scope
        annotation class ParentScope
    
        @Scope
        annotation class ChildScope
    """
}

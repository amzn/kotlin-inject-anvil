@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.test.compile
import software.amazon.lastmile.kotlin.inject.anvil.test.generatedComponent
import software.amazon.lastmile.kotlin.inject.anvil.test.origin

class ContributesSubcomponentFactoryProcessorTest {

    @Test
    fun `a component interface is generated in the lookup package for a contributed subcomponent factory`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            @ContributesSubcomponent(Unit::class)
            @SingleIn(Unit::class)
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory(String::class)
                interface Factory {
                    fun createSubcomponentInterface(): SubcomponentInterface
                }          
            }
            """,
        ) {
            val generatedComponent = subcomponent.factory.generatedComponent

            assertThat(generatedComponent.packageName).isEqualTo(LOOKUP_PACKAGE)
            assertThat(generatedComponent.interfaces).containsExactly(subcomponent.factory)
            assertThat(generatedComponent.origin).isEqualTo(subcomponent.factory)
        }
    }

    @Test
    fun `the factory function must have a single function - multiple abstract functions`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            @ContributesSubcomponent(String::class)
            @SingleIn(String::class)
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory(Unit::class)
                interface Factory {
                    fun createSubcomponentInterface(): SubcomponentInterface
                    fun createSubcomponentInterface2(): SubcomponentInterface
                }          
            }
            """,
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
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            @ContributesSubcomponent(String::class)
            @SingleIn(String::class)
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory(Unit::class)
                interface Factory {
                    fun createSubcomponentInterface(): SubcomponentInterface
                    fun createSubcomponentInterface2(): SubcomponentInterface = 
                        createSubcomponentInterface()

                    val string: String
                }          
            }
            """,
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
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            @ContributesSubcomponent(String::class)
            @SingleIn(String::class)
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory(Unit::class)
                interface Factory         
            }
            """,
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
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            @ContributesSubcomponent(String::class)
            @SingleIn(String::class)
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory(Unit::class)
                interface Factory {
                    fun createSubcomponentInterface(): Any
                }          
            }
            """,
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
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            @ContributesSubcomponent(String::class)
            @SingleIn(String::class)
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory(Unit::class)
                interface Factory {
                    fun createSubcomponentInterface()
                }          
            }
            """,
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
    
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory(Unit::class)
                interface Factory {
                    fun createSubcomponentInterface(): SubcomponentInterface
                }          
            }
            """,
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
    
            @ContributesSubcomponent.Factory(String::class)
            interface Factory {
                fun createSubcomponentInterface(): Factory
            }          
            """,
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

            @ContributesSubcomponent(String::class)
            interface SubcomponentInterface {
                interface Factory {
                    fun createSubcomponentInterface(): SubcomponentInterface
                }          
            }
            """,
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
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            @ContributesSubcomponent(String::class)
            @SingleIn(String::class)
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory(Unit::class)
                interface Factory1 {
                    fun createSubcomponentInterface1(): SubcomponentInterface
                }          
                @ContributesSubcomponent.Factory(Unit::class)
                interface Factory2 {
                    fun createSubcomponentInterface2(): SubcomponentInterface
                }          
            }
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Contributed subcomponent must have exactly one inner interface " +
                    "annotated with @ContributesSubcomponent.Factory.",
            )
        }
    }

    @Test
    fun `a contributed subcomponent must have a kotlin-inject scope`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent

            @ContributesSubcomponent(String::class)
            interface SubcomponentInterface {
                @ContributesSubcomponent.Factory(Unit::class)
                interface Factory {
                    fun createSubcomponentInterface1(): SubcomponentInterface
                }          
            }
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "A kotlin-inject scope like @SingleIn(Abc::class) is missing.",
            )
        }
    }

    private val JvmCompilationResult.subcomponent: Class<*>
        get() = classLoader.loadClass("software.amazon.test.SubcomponentInterface")

    private val Class<*>.factory: Class<*>
        get() = classes.single { it.simpleName == "Factory" }
}

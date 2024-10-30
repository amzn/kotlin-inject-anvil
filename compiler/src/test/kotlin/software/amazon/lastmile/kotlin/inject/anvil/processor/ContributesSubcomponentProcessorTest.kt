@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import me.tatarka.inject.annotations.Component
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import software.amazon.lastmile.kotlin.inject.anvil.test.Compilation
import software.amazon.lastmile.kotlin.inject.anvil.test.compile
import software.amazon.lastmile.kotlin.inject.anvil.test.componentInterface
import software.amazon.lastmile.kotlin.inject.anvil.test.newComponent
import software.amazon.lastmile.kotlin.inject.anvil.test.origin
import kotlin.reflect.KClass

class ContributesSubcomponentProcessorTest {

    @Test
    fun `a contributed subcomponent is generated when the parent is merged`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Provides

            @MergeComponent(AppScope::class)
            @Component
            @SingleIn(AppScope::class)
            interface ComponentInterface : ComponentInterfaceMerged

            @ContributesSubcomponent(LoggedInScope::class)
            @SingleIn(LoggedInScope::class)
            interface OtherComponent {
                @ContributesSubcomponent.Factory(AppScope::class)
                interface Parent {
                    fun otherComponent(): OtherComponent 
                }
            }
            
            @ContributesTo(LoggedInScope::class)
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

            val generatedClass = otherComponent.generatedSubcomponent
            assertThat(generatedClass.getAnnotation(Component::class.java)).isNotNull()
            assertThat(generatedClass.getAnnotation(MergeComponent::class.java).scope)
                .isEqualTo(loggedInScope)

            assertThat(generatedClass.getAnnotation(SingleIn::class.java).scope)
                .isEqualTo(loggedInScope)
            assertThat(generatedClass.origin).isEqualTo(otherComponent)
        }
    }

    @Test
    fun `contributions to the child scope from a previous compilation are picked up`() {
        val previousResult1 = compile(scopesSource)

        val previousResult2 = compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import me.tatarka.inject.annotations.Provides

            @ContributesTo(LoggedInScope::class)
            interface ChildComponent {
                @Provides fun provideString(): String = "abc"

                val string: String
            }
            """,
            previousCompilationResult = previousResult1,
        )

        val previousResult3 = compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            @ContributesSubcomponent(LoggedInScope::class)
            @SingleIn(LoggedInScope::class)
            interface OtherComponent {
                @ContributesSubcomponent.Factory(AppScope::class)
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
                package software.amazon.test
        
                import software.amazon.lastmile.kotlin.inject.anvil.AppScope
                import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
                import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
                import me.tatarka.inject.annotations.Component
    
                @MergeComponent(AppScope::class)
                @Component
                @SingleIn(AppScope::class)
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
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Provides

            @Component
            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            interface ComponentInterface : ComponentInterfaceMerged

            @Component
            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            interface ComponentInterface2 : ComponentInterface2Merged

            @ContributesSubcomponent(LoggedInScope::class)
            @SingleIn(LoggedInScope::class)
            interface OtherComponent {
                @ContributesSubcomponent.Factory(AppScope::class)
                interface Parent {
                    fun otherComponent(): OtherComponent 
                }
            }
            
            @ContributesTo(LoggedInScope::class)
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
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
            import me.tatarka.inject.annotations.Component

            @Component
            @MergeComponent(Unit::class, exclude = [OtherComponent::class])
            @SingleIn(Unit::class)
            interface ComponentInterface : ComponentInterfaceMerged

            @ContributesSubcomponent(String::class)
            @SingleIn(String::class)
            interface OtherComponent {
                @ContributesSubcomponent.Factory(Unit::class)
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
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Provides

            @Component
            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            interface ComponentInterface : ComponentInterfaceMerged

            @ContributesSubcomponent(LoggedInScope::class)
            @SingleIn(LoggedInScope::class)
            interface ComponentInterface2 {
                @ContributesSubcomponent.Factory(AppScope::class)
                interface Parent {
                    fun componentInterface2(): ComponentInterface2 
                }
            }

            @ContributesSubcomponent(Unit::class)
            @SingleIn(Unit::class)
            interface OtherComponent {
                @ContributesSubcomponent.Factory(LoggedInScope::class)
                interface Parent {
                    fun otherComponent(): OtherComponent 
                }
            }
            
            @ContributesTo(Unit::class)
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
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Provides

            @Component
            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            interface ComponentInterface : ComponentInterfaceMerged

            @ContributesSubcomponent(LoggedInScope::class)
            @SingleIn(LoggedInScope::class)
            interface OtherComponent {
                @ContributesSubcomponent.Factory(AppScope::class)
                interface Parent {
                    fun otherComponent(stringArg: String, intArg: Int): OtherComponent 
                }
            }
            
            @ContributesTo(LoggedInScope::class)
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

    @Test
    @Suppress("ForbiddenComment")
    fun `the factory function accepts parameters with qualifier annotations and the parameters are bound in the component`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.ForScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Qualifier
            import me.tatarka.inject.annotations.Provides

            @Qualifier
            annotation class QualifiedString

            @Component
            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            interface ComponentInterface : ComponentInterfaceMerged

            @ContributesSubcomponent(LoggedInScope::class)
            @SingleIn(LoggedInScope::class)
            interface OtherComponent {
                @ContributesSubcomponent.Factory(AppScope::class)
                interface Parent {
                    fun otherComponent(
                        @QualifiedString stringArg: String, 
                        @ForScope(LoggedInScope::class) intArg: Int,
                    ): OtherComponent 
                }
            }
            
            @ContributesTo(LoggedInScope::class)
            interface ChildComponent {
                @QualifiedString
                val string: String
                
                @ForScope(LoggedInScope::class)
                val int: Int
            }
            """,
            scopesSource,
            // TODO: Enable KSP2 once this bug is solved:
            //  https://github.com/evant/kotlin-inject/issues/447
            useKsp2 = false,
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

    @Test
    fun `abstract classes are disallowed`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Provides

            @ContributesSubcomponent(LoggedInScope::class)
            @SingleIn(LoggedInScope::class)
            abstract class OtherComponent {
                @ContributesSubcomponent.Factory(Unit::class)
                interface Parent {
                    fun otherComponent(stringArg: String, intArg: Int): OtherComponent 
                }
            }
            """,
            scopesSource,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Only interfaces can be contributed. If you have parameters on " +
                    "your abstract class, then move them to the factory. See " +
                    "@ContributesSubcomponent for more details.",
            )
        }
    }

    @Test
    fun `the factory interface is provided and can be injected in the parent scope`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesSubcomponent
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Provides

            @MergeComponent(AppScope::class)
            @Component
            @SingleIn(AppScope::class)
            interface ComponentInterface : ComponentInterfaceMerged {
                val factory: LoggedInComponent.Factory
            }

            @ContributesSubcomponent(LoggedInScope::class)
            @SingleIn(LoggedInScope::class)
            interface LoggedInComponent {
                @ContributesSubcomponent.Factory(AppScope::class)
                interface Factory {
                    fun loggedInComponent(): LoggedInComponent 
                }
            }
            """,
            scopesSource,
        ) {
            val component = componentInterface.newComponent<Any>()
            val loggedInComponent = component::class.java.methods
                .single { it.name == "getFactory" }
                .invoke(component)

            assertThat(loggedInComponent).isNotNull()
        }
    }

    private val JvmCompilationResult.componentInterface2: Class<*>
        get() = classLoader.loadClass("software.amazon.test.ComponentInterface2")

    private val JvmCompilationResult.otherComponent: Class<*>
        get() = classLoader.loadClass("software.amazon.test.OtherComponent")

    private val JvmCompilationResult.loggedInScope: KClass<*>
        get() = classLoader.loadClass("software.amazon.test.LoggedInScope").kotlin

    private val Class<*>.generatedSubcomponent: Class<*>
        get() = classLoader.loadClass("${canonicalName}FinalComponentInterface")

    @Language("kotlin")
    internal val scopesSource = """
        package software.amazon.test
    
        object LoggedInScope
    """
}

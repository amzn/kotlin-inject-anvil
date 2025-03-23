@file:OptIn(ExperimentalCompilerApi::class)

package software.amazon.lastmile.kotlin.inject.anvil.processor

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.tschuchort.compiletesting.JvmCompilationResult
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import software.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import software.amazon.lastmile.kotlin.inject.anvil.compile
import software.amazon.lastmile.kotlin.inject.anvil.componentInterface
import software.amazon.lastmile.kotlin.inject.anvil.generatedComponent
import software.amazon.lastmile.kotlin.inject.anvil.kotlinInjectComponent
import software.amazon.lastmile.kotlin.inject.anvil.newComponent
import software.amazon.lastmile.kotlin.inject.anvil.origin

class ContributesAssistedFactoryProcessorTest {
    @Test
    fun `a component interface is generated with contributes assisted factory`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesAssistedFactory
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Assisted

            interface Base

            @Inject
            @ContributesAssistedFactory(
                scope = Unit::class,
                assistedFactory = BaseFactory::class,
            )
            class Impl(
                @Assisted val id: String,
            ) : Base   

            interface BaseFactory {
                fun create(id: String): Base
            }
            """,
        ) {
            val component = impl.generatedComponent

            assertThat(component.packageName).isEqualTo(LOOKUP_PACKAGE)
            assertThat(component.origin).isEqualTo(impl)

            val method = component.declaredMethods.single()
            assertThat(component.declaredMethods).hasSize(1)
            assertThat(method.returnType).isEqualTo(baseFactory)
            assertThat(method.name).isEqualTo("provideImplBase")

            val parameter = method.parameters.single()
            assertThat(method.parameters.size).isEqualTo(1)
            assertThat(parameter.type).isEqualTo(realAssistedFactory)
        }
    }

    @Test
    fun `a component interface is generated with contributes assisted factory as nested interface`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesAssistedFactory
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Assisted

            interface Base {
                interface Factory {
                    fun create(id: String): Base
                }
            }

            @Inject
            @ContributesAssistedFactory(
                scope = Unit::class,
                assistedFactory = Base.Factory::class,
            )
            class Impl(
                @Assisted val id: String,
            ) : Base
            """,
        ) {
            val component = impl.generatedComponent

            assertThat(component.packageName).isEqualTo(LOOKUP_PACKAGE)
            assertThat(component.origin).isEqualTo(impl)

            val method = component.declaredMethods.single()
            assertThat(component.declaredMethods).hasSize(1)
            assertThat(method.returnType).isEqualTo(nestedBaseFactory)
            assertThat(method.name).isEqualTo("provideImplBase")

            val parameter = method.parameters.single()
            assertThat(method.parameters.size).isEqualTo(1)
            assertThat(parameter.type).isEqualTo(realAssistedFactory)
        }
    }

    @Test
    fun `the kotlin-inject component contains assisted factory binding`() {
        compile(
            """
            package software.amazon.test
    
            import software.amazon.lastmile.kotlin.inject.anvil.ContributesAssistedFactory
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Assisted
            import software.amazon.lastmile.kotlin.inject.anvil.AppScope
            import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
            import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

            interface Base

            @Inject
            @ContributesAssistedFactory(
                scope = AppScope::class,
                assistedFactory = BaseFactory::class,
            )
            class Impl(
                @Assisted val id: String,
            ) : Base   

            interface BaseFactory {
                fun create(id: String): Base
            }

            @MergeComponent(AppScope::class)
            @SingleIn(AppScope::class)
            interface ComponentInterface {
                val baseFactory: BaseFactory
            }
            """,
        ) {
            val component = componentInterface.kotlinInjectComponent.newComponent<Any>()

            val implValue = component::class.java.methods
                .single { it.name == "provideImplBase" }
                .invoke(component, { id: String -> })

            assertThat(defaultBaseFactory.isInstance(implValue)).isTrue()
        }
    }

    private val JvmCompilationResult.baseFactory: Class<*>
        get() = classLoader.loadClass("software.amazon.test.BaseFactory")

    private val JvmCompilationResult.nestedBaseFactory: Class<*>
        get() = classLoader.loadClass("software.amazon.test.Base${'$'}Factory")

    private val JvmCompilationResult.defaultBaseFactory: Class<*>
        get() = classLoader.loadClass(
            "amazon.lastmile.inject.SoftwareAmazonTestImpl${'$'}DefaultBaseFactory",
        )

    private val JvmCompilationResult.impl: Class<*>
        get() = classLoader.loadClass("software.amazon.test.Impl")

    private val JvmCompilationResult.realAssistedFactory: Class<*>
        get() = classLoader.loadClass("kotlin.jvm.functions.Function1")
}

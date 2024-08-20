@file:OptIn(ExperimentalCompilerApi::class)

package com.amazon.lastmile.kotlin.inject.anvil.processor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.amazon.lastmile.kotlin.inject.anvil.LOOKUP_PACKAGE
import com.amazon.lastmile.kotlin.inject.anvil.compile
import com.amazon.lastmile.kotlin.inject.anvil.generatedComponent
import com.amazon.lastmile.kotlin.inject.anvil.inner
import com.amazon.lastmile.kotlin.inject.anvil.isAnnotatedWith
import com.amazon.lastmile.kotlin.inject.anvil.origin
import com.amazon.lastmile.kotlin.inject.anvil.otherScopeSource
import com.amazon.test.SingleInAppScope
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import me.tatarka.inject.annotations.Provides
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

class ContributesBindingProcessorTest {

    @Test
    fun `a component interface is generated in the lookup package for a contributed binding`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import me.tatarka.inject.annotations.Inject

            interface Base

            @Inject
            @SingleInAppScope
            @ContributesBinding
            class Impl : Base 
            """,
        ) {
            val generatedComponent = impl.generatedComponent

            assertThat(generatedComponent.packageName).isEqualTo(LOOKUP_PACKAGE)
            assertThat(generatedComponent).isAnnotatedWith(SingleInAppScope::class)
            assertThat(generatedComponent.origin).isEqualTo(impl)

            val method = generatedComponent.declaredMethods.single()
            assertThat(method.name).isEqualTo("provideImplBase")
            assertThat(method.parameters.single().type).isEqualTo(impl)
            assertThat(method.returnType).isEqualTo(base)
            assertThat(method).isAnnotatedWith(Provides::class)
        }
    }

    @Test
    fun `a component interface is generated in the lookup package for an inner contributed binding`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import me.tatarka.inject.annotations.Inject

            interface Base

            interface Impl {
                @Inject
                @SingleInAppScope
                @ContributesBinding
                class Inner : Base
            } 
            """,
        ) {
            val generatedComponent = impl.inner.generatedComponent

            assertThat(generatedComponent.packageName).isEqualTo(LOOKUP_PACKAGE)
            assertThat(generatedComponent).isAnnotatedWith(SingleInAppScope::class)
            assertThat(generatedComponent.origin).isEqualTo(impl.inner)

            val method = generatedComponent.declaredMethods.single()
            assertThat(method.name).isEqualTo("provideImplInnerBase")
            assertThat(method.parameters.single().type).isEqualTo(impl.inner)
            assertThat(method.returnType).isEqualTo(base)
            assertThat(method).isAnnotatedWith(Provides::class)
        }
    }

    @Test
    fun `the explicit bound type has a higher priority`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import me.tatarka.inject.annotations.Inject

            interface Base
            interface Base2 : Base

            @Inject
            @SingleInAppScope
            @ContributesBinding(boundType = Base::class)
            class Impl : Base2 

            @Inject
            @SingleInAppScope
            @ContributesBinding
            class Impl2 : Base2 
            """,
        ) {
            assertThat(impl.generatedComponent.declaredMethods.single().returnType)
                .isEqualTo(base)
            assertThat(impl2.generatedComponent.declaredMethods.single().returnType)
                .isEqualTo(base2)
        }
    }

    @Test
    fun `it's an error when there's no super type`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import me.tatarka.inject.annotations.Inject

            @Inject
            @SingleInAppScope
            @ContributesBinding
            class Impl 
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "The bound type could not be determined for Impl. There are no super types.",
            )
        }
    }

    @Test
    fun `it's an error when there are multiple super types`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import me.tatarka.inject.annotations.Inject

            interface Base
            interface Base2

            @Inject
            @SingleInAppScope
            @ContributesBinding
            class Impl : Base, Base2
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "The bound type could not be determined for Impl. " +
                    "There are multiple super types: Base, Base2.",
            )
        }
    }

    @Test
    fun `the scope must be added explicitly for unscoped bindings`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import me.tatarka.inject.annotations.Inject

            interface Base

            @Inject
            @ContributesBinding(scope = SingleInAppScope::class)
            class Impl : Base 
            """,
        ) {
            val generatedComponent = impl.generatedComponent

            assertThat(generatedComponent.packageName).isEqualTo(LOOKUP_PACKAGE)
            assertThat(generatedComponent).isAnnotatedWith(SingleInAppScope::class)
            assertThat(generatedComponent.origin).isEqualTo(impl)
        }
    }

    @Test
    fun `it's an error to set the scope explicitly when the class is scoped`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import me.tatarka.inject.annotations.Inject

            interface Base
            
            @Inject
            @SingleInAppScope
            @ContributesBinding(scope = SingleInAppScope::class)
            class Impl : Base 
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "A scope was defined explicitly on the @ContributesBinding annotation " +
                    "`com.amazon.test.SingleInAppScope` and the class itself is scoped using " +
                    "`com.amazon.test.SingleInAppScope`. In this case the explicit scope on " +
                    "the @ContributesBinding annotation can be removed.",
            )
        }
    }

    @Test
    fun `it's an error to set the scope explicitly when the class is scoped - different scopes`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import me.tatarka.inject.annotations.Inject

            interface Base

            @Inject
            @OtherScope
            @ContributesBinding(scope = SingleInAppScope::class)
            class Impl : Base 
            """,
            otherScopeSource,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "A scope was defined explicitly on the @ContributesBinding annotation " +
                    "`com.amazon.test.SingleInAppScope` and the class itself is scoped using " +
                    "`com.amazon.test.OtherScope`. It's not allowed to mix different scopes.",
            )
        }
    }

    @Test
    fun `it's an error to not specify the scope for unscoped bindings`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import me.tatarka.inject.annotations.Inject

            interface Base

            @Inject
            @ContributesBinding
            class Impl : Base 
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "Couldn't find scope for Impl. For unscoped objects it is required " +
                    "to specify the target scope on the @ContributesBinding annotation.",
            )
        }
    }

    @Test
    fun `bindings are repeatable`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import me.tatarka.inject.annotations.Inject

            interface Base
            interface Base2

            @Inject
            @SingleInAppScope
            @ContributesBinding(boundType = Base::class)
            @ContributesBinding(boundType = Base2::class)
            class Impl : Base, Base2 
            """,
        ) {
            val generatedComponent = impl.generatedComponent

            assertThat(generatedComponent.packageName).isEqualTo(LOOKUP_PACKAGE)
            assertThat(generatedComponent).isAnnotatedWith(SingleInAppScope::class)
            assertThat(generatedComponent.origin).isEqualTo(impl)

            with(generatedComponent.declaredMethods.single { it.name == "provideImplBase" }) {
                assertThat(parameters.single().type).isEqualTo(impl)
                assertThat(returnType).isEqualTo(base)
                assertThat(this).isAnnotatedWith(Provides::class)
            }

            with(generatedComponent.declaredMethods.single { it.name == "provideImplBase2" }) {
                assertThat(parameters.single().type).isEqualTo(impl)
                assertThat(returnType).isEqualTo(base2)
                assertThat(this).isAnnotatedWith(Provides::class)
            }
        }
    }

    @Test
    fun `it's an error to use different scopes for multiple bindings`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import me.tatarka.inject.annotations.Inject

            interface Base
            interface Base2

            @Inject
            @ContributesBinding(scope = SingleInAppScope::class, boundType = Base::class)
            @ContributesBinding(scope = OtherScope::class, boundType = Base2::class)
            class Impl : Base, Base2 
            """,
            otherScopeSource,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "All explicit scopes on @ContributesBinding annotations must be the same.",
            )
        }
    }

    @Test
    fun `it's an error to use different scopes for multiple bindings - on class`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import me.tatarka.inject.annotations.Inject

            interface Base
            interface Base2

            @Inject
            @OtherScope
            @ContributesBinding(scope = SingleInAppScope::class, boundType = Base::class)
            @ContributesBinding(boundType = Base2::class)
            class Impl : Base, Base2 
            """,
            otherScopeSource,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "If one @ContributesBinding annotation has an explicit scope, " +
                    "then all annotations must specify an explicit scope.",
            )
        }
    }

    @Test
    fun `it's an error to duplicate the same binding`() {
        compile(
            """
            package com.amazon.test
    
            import com.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Scope

            interface Base

            @Inject
            @SingleInAppScope
            @ContributesBinding(boundType = Base::class)
            @ContributesBinding(boundType = Base::class)
            class Impl : Base, Base2 
            """,
            exitCode = COMPILATION_ERROR,
        ) {
            assertThat(messages).contains(
                "The same type should not be contributed twice: com.amazon.test.Base.",
            )
        }
    }

    private val JvmCompilationResult.base: Class<*>
        get() = classLoader.loadClass("com.amazon.test.Base")

    private val JvmCompilationResult.base2: Class<*>
        get() = classLoader.loadClass("com.amazon.test.Base2")

    private val JvmCompilationResult.impl: Class<*>
        get() = classLoader.loadClass("com.amazon.test.Impl")

    private val JvmCompilationResult.impl2: Class<*>
        get() = classLoader.loadClass("com.amazon.test.Impl2")
}

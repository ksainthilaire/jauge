import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import io.jauge.buildlogic.jaugeNamespace
import io.jauge.buildlogic.libraries
import io.jauge.buildlogic.libraryOf
import io.jauge.buildlogic.versionOf
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention for every shared Kotlin Multiplatform module.
 *
 * It applies the Kotlin Multiplatform and Android Kotlin Multiplatform library plugins, declares the Android and iOS
 * targets, enables Android host unit tests and adds the common test dependencies.
 */
class KotlinMultiplatformLibraryConventionPlugin : Plugin<Project> {

    /** Applies the convention to [target]. */
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")

            extensions.configure<KotlinMultiplatformExtension> {
                extensions.getByType<KotlinMultiplatformAndroidLibraryTarget>().apply {
                    namespace = jaugeNamespace
                    compileSdk = libraries.versionOf("androidCompileSdk").toInt()
                    minSdk = libraries.versionOf("androidMinSdk").toInt()
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                    withHostTest {
                        isIncludeAndroidResources = true
                    }
                }

                iosArm64()
                iosSimulatorArm64()

                applyDefaultHierarchyTemplate()

                compilerOptions {
                    freeCompilerArgs.addAll("-Xexpect-actual-classes", "-opt-in=kotlin.time.ExperimentalTime")
                }

                sourceSets.apply {
                    commonMain.dependencies {
                        implementation(project.dependencies.platform(libraries.libraryOf("koin-bom")))
                        implementation(libraries.libraryOf("koin-core"))
                        implementation(libraries.libraryOf("kotlinx-coroutines-core"))
                    }
                    commonTest.dependencies {
                        implementation(kotlin("test"))
                        implementation(libraries.libraryOf("kotlinx-coroutines-test"))
                    }
                }
            }
        }
    }
}

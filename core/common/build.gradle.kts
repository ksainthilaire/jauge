import io.jauge.buildlogic.generateBuildConfiguration

plugins {
    alias(libs.plugins.jaugeKotlinMultiplatformLibrary)
}

generateBuildConfiguration(packageName = "io.jauge.core.common.configuration")

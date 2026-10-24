rootProject.name = "Jauge"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":composeApp")

include(":core:common")
include(":core:designsystem")
include(":core:ui")
include(":core:domain")
include(":core:data")
include(":core:network")
include(":core:billing")
include(":core:notifications")
include(":core:authentication")

include(":feature:onboarding")
include(":feature:analysis")
include(":feature:verdict")
include(":feature:twins")
include(":feature:share")
include(":feature:paywall")
include(":feature:account")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val rendererRepo = providers.gradleProperty("asswb.rendererRepo").orNull
        if (!rendererRepo.isNullOrBlank()) {
            maven(uri(rendererRepo)) {
                content { includeModuleByRegex("io\\.github\\.yuroyami", "libmpvkt.*") }
            }
        }
        google()
        mavenCentral()
        maven("https://yuroyami.github.io/maven") {
            content { includeModuleByRegex("io\\.github\\.yuroyami", "libmpvkt.*") }
        }
    }
}

rootProject.name = "ASS-Workbench-Android"
include(":app")
include(":core:domain")
include(":core:fonts")
include(":core:container")

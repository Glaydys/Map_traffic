pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Add two lines below to the repositories block
        maven { url = uri("https://plugins.gradle.org/m2") }
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "Map"
include(":app")
 
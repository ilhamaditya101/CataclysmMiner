import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        mavenCentral()

        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }

        maven {
            name = "aurelium"
            url = uri("https://repo.aurelium.dev/repository/maven-public/")
        }

        maven {
            name = "phoenix"
            url = uri("https://nexus.phoenixdevt.fr/repository/maven-public/")
        }
    }
}

rootProject.name = "CataclysmMiner"

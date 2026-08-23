pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.aurelium.dev/repository/maven-public/")
        maven("https://nexus.phoenixdevt.fr/repository/maven-public/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.aurelium.dev/repository/maven-public/")
        maven("https://nexus.phoenixdevt.fr/repository/maven-public/")
    }
}

rootProject.name = "CataclysmMiner"

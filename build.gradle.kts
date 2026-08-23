plugins {
    java
}

group = "id.example.cataclysmminer"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.aurelium.dev/repository/maven-public/")
    maven("https://nexus.phoenixdevt.fr/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("dev.aurelium:auraskills-api-bukkit:2.2.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.jar {
    archiveBaseName.set("CataclysmMiner")
}

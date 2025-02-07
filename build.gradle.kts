import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.1.10"
    id("com.gradleup.shadow").version("9.0.0-beta7")
}

group = "ru.kainlight.lightenderchest"
version = "1.0.3"

val kotlinVersion = "2.1.10"
val hikariCPVersion = "6.2.1"
val mysqlConnectorVersion = "9.2.0"
val sqliteVersion = "3.49.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io/")
}

dependencies {
    compileOnly(kotlin("stdlib"))

    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")

    compileOnly("com.zaxxer:HikariCP:$hikariCPVersion")
    compileOnly("com.mysql:mysql-connector-j:$mysqlConnectorVersion")
    compileOnly("org.xerial:sqlite-jdbc:$sqliteVersion")

    implementation(files(
        "C:/Users/danny/IdeaProjects/.Kotlin/.private/LightLibrary/bukkit/build/libs/LightLibraryBukkit-PUBLIC-1.0.jar"
    ))
}

val targetJavaVersion = 17
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
}
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf(
        "version" to version,
        "kotlinVersion" to kotlinVersion,
        "hikariCPVersion" to hikariCPVersion,
        "mysqlVersion" to mysqlConnectorVersion,
        "sqliteVersion" to sqliteVersion
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set(project.name)
    archiveFileName.set("${project.name}-${project.version}.jar")

    // Исключения
    exclude("META-INF/maven/**",
            "META-INF/INFO_BIN",
            "META-INF/INFO_SRC",
            "kotlin/**"
    )
    mergeServiceFiles()

    // Переименование пакетов
    val shadedPath = "ru.kainlight.lightenderchest.shaded"
    relocate("ru.kainlight.lightlibrary", "$shadedPath.lightlibrary")

    relocate("kotlinx", "$shadedPath.kotlinx")
    relocate("_COROUTINE", "$shadedPath.kotlinx._COROUTINE")
    relocate("com.google", "$shadedPath.com.google")
    relocate("com.github", "$shadedPath.com.github")
    relocate("org.jspecify", "$shadedPath.org.jspecify")
    relocate("org.intellij", "$shadedPath.org.intellij")
    relocate("org.jetbrains", "$shadedPath.org.jetbrains")
}
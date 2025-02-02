import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.1.10"
    id("com.gradleup.shadow").version("9.0.0-beta4")
}

group = "ru.kainlight.lightenderchest"
version = "1.0"

val kotlinVersion = "2.0.20"
val adventureVersion = "4.18.0"
val adventureBukkitVersion = "4.3.4"
val hikariCPVersion = "6.2.1"
val mysqlConnectorVersion = "9.1.0"
val sqliteVersion = "3.48.0.0"

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

    compileOnly("net.kyori:adventure-api:$adventureVersion")
    compileOnly("net.kyori:adventure-text-minimessage:$adventureVersion")
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
        "adventureVersion" to adventureVersion,
        "adventureBukkitVersion" to adventureBukkitVersion,
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
    // Настройки для Shadow JAR
    archiveBaseName.set(project.name)
    archiveFileName.set("${project.name}-${project.version}.jar")

    // Исключения и переименование пакетов
    exclude("META-INF/maven/**",
            "META-INF/INFO_BIN",
            "META-INF/INFO_SRC",
            "kotlin/**"
    )
    mergeServiceFiles()

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

tasks.register("server") {
    group = "build"
    description = "Копирует готовый JAR из shadowJar в серверную папку"

    val buildDir = "C:/testservers/1.21.3/plugins"

    dependsOn("shadowJar") // Используем результат shadowJar

    doLast {
        val shadowJarTask = tasks.named<ShadowJar>("shadowJar").get()
        val outputJar = shadowJarTask.archiveFile.get().asFile

        if (!outputJar.exists()) {
            throw GradleException("Сборка shadowJar завершилась неудачно: JAR файл не найден!")
        }

        copy {
            from(outputJar)
            into(buildDir)
        }

        println("Shadow JAR successfully copied in: $buildDir")
    }
}
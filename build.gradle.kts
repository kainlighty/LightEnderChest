import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.1.0"
    id("com.gradleup.shadow").version("9.0.0-beta4")
}

group = "ru.kainlight.lightenderchest"
version = "1.0"

val kotlinVersion = "2.0.20"
val adventureVersion = "4.18.0"
val adventureBukkitVersion = "4.3.4"
val hikariCPVersion = "6.2.1"
val mysqlConnectorVersion = "9.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io/")
}

dependencies {
    compileOnly(kotlin("stdlib"))

    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")

    compileOnly("net.kyori:adventure-api:$adventureVersion")
    compileOnly("net.kyori:adventure-text-minimessage:$adventureVersion")
    compileOnly("net.kyori:adventure-platform-bukkit:$adventureBukkitVersion")
    compileOnly("com.zaxxer:HikariCP:$hikariCPVersion")

    implementation("com.j256.ormlite:ormlite-jdbc:6.1")
    compileOnly("com.mysql:mysql-connector-j:$mysqlConnectorVersion")

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
        "mysqlVersion" to mysqlConnectorVersion
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
            "META-INF/INFO_SRC"
    )
    mergeServiceFiles()

    val shadedPath = "ru.kainlight.lightenderchest.shaded"
    relocate("ru.kainlight.lightlibrary", "$shadedPath.lightlibrary")
    relocate("com.j256.ormlite", "$shadedPath.ormlite-jdbc")
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

        println("Shadow JAR успешно скопирован в: $buildDir")
    }
}

tasks.register("servers") {
    group = "build"
    description = "Копирует готовый JAR из shadowJar в серверные папки"

    val buildDirs = listOf<String>(
        "C:/testservers/1.21.3/plugins"
    )

    dependsOn("shadowJar") // Используем результат shadowJar

    doLast {
        val shadowJarTask = tasks.named<ShadowJar>("shadowJar").get()
        val outputJar = shadowJarTask.archiveFile.get().asFile

        if (!outputJar.exists()) {
            throw GradleException("Сборка shadowJar завершилась неудачно: JAR файл не найден!")
        }

        copy {
            from(outputJar)

            for (dir in buildDirs) {
                into(dir)
                println("Shadow JAR успешно скопирован в: $dir")
            }
        }
    }
}
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = "dev.doshikking"
version = "0.1.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val platformPath = providers.gradleProperty("platformPath")

dependencies {
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.opentest4j:opentest4j:1.3.0")
    intellijPlatform {
        testFramework(TestFrameworkType.Bundled)
        if (platformPath.isPresent) {
            local(platformPath.get())
        } else {
            intellijIdeaCommunity("2025.1.3")
        }
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

intellijPlatform {
    instrumentCode = false
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }
}

val rslCheck by tasks.registering(JavaExec::class) {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.doshikking.rsl.RslAnalysisTest"
    enableAssertions = true
}

tasks.check {
    dependsOn(rslCheck)
}

plugins {
    kotlin("multiplatform") version "2.4.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0"
}

group = "com.example"
version = "0.1.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.5.0"
val sqlx4kVersion = "1.11.0"
val log4kVersion = "2.1.1"

kotlin {
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
                }
            }
        }
    }

    linuxX64 {
        binaries {
            executable {
                entryPoint = "com.example.main"
            }
        }
    }

    macosArm64 {
        binaries {
            executable {
                entryPoint = "com.example.main"
            }
        }
    }

    jvmToolchain(25)

    sourceSets {
        commonMain {
            kotlin.srcDir("src")
            resources.srcDir("resources")
            dependencies {
                implementation("io.ktor:ktor-server-core:$ktorVersion")
                implementation("io.ktor:ktor-server-cio:$ktorVersion")
                implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
                implementation("io.ktor:ktor-server-websockets:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

                implementation("io.github.smyrgeorge:sqlx4k-postgres:$sqlx4kVersion")
                implementation("io.github.smyrgeorge:log4k:$log4kVersion")
                implementation("io.github.smyrgeorge:log4k-classic:$log4kVersion")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            kotlin.srcDir("platform/jvm")
        }
        nativeMain {
            kotlin.srcDir("platform/native")
        }
    }
}

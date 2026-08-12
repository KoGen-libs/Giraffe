plugins {
    // AGP already registers Kotlin support for a library module with Kotlin sources - applying
    // org.jetbrains.kotlin.android here too conflicts ("extension 'kotlin' already registered"),
    // same as ":giraffe" doesn't apply it explicitly either.
    id("com.android.library")
    id("maven-publish")
    id("signing")
    alias(libs.plugins.jreleaser)
}

group = "io.github.eugenprog"
if (version == Project.DEFAULT_VERSION) {
    version = "0.1.0-SNAPSHOT"
}

android {
    namespace = "com.kogen.giraffe"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Only what's needed to implement the real module's public interceptor types
    // (io.grpc.ClientInterceptor, okhttp3.Interceptor, Ktor's client plugin API) - no Room, no
    // Compose, no media3/Coil: this artifact does nothing, so it carries none of the real
    // module's weight into a release APK that mistakenly depends on it instead of ":giraffe"
    // (io.github.eugenprog:giraffe).
    implementation(libs.grpc.stub)
    implementation(libs.okhttp)
    implementation(libs.ktor.client.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "io.github.eugenprog"
                artifactId = "giraffe-no-op"
                version = project.version.toString()

                pom {
                    name.set("Giraffe (no-op)")
                    description.set(
                        "Drop-in no-op counterpart to io.github.eugenprog:giraffe - same " +
                            "GiraffeInterceptor class and constructor, zero behavior. Wire it in via " +
                            "releaseImplementation (alongside debugImplementation on the real " +
                            "artifact) so the interceptor call site compiles unchanged for both " +
                            "variants, and Giraffe's real dependencies never ship in a release build."
                    )
                    url.set("https://github.com/EugenProg/GRaffe")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("EugenProg")
                            name.set("Eugen Kopp")
                            email.set("Eugen.kopp.kz@gmail.com")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/EugenProg/GRaffe.git")
                        developerConnection.set("scm:git:ssh://github.com:EugenProg/GRaffe.git")
                        url.set("https://github.com/EugenProg/GRaffe/tree/main")
                    }
                }
            }
        }
        repositories {
            maven {
                setUrl(layout.buildDirectory.dir("staging-deploy"))
            }
        }
    }

    val signingKey = System.getenv("JRELEASER_GPG_SECRET_KEY")
    val signingPassword = System.getenv("JRELEASER_GPG_PASSPHRASE")
    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        signing {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(publishing.publications["release"])
        }
    }
}

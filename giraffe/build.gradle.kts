plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GIRAFFE_VERSION", "\"${project.version}\"")
    }

    buildFeatures {
        buildConfig = true
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
    sourceSets {
        getByName("debug") {
            java.directories.add("build/generated/ksp/debug/kotlin")
        }
        getByName("release") {
            java.directories.add("build/generated/ksp/release/kotlin")
        }
    }
}

// The sourceSets registration above points AGP/the IDE at KSP's output directory directly by
// path, which Gradle can't trace back to the kspXxxKotlin task that produces it. Without an
// explicit dependency, tasks that read that directory (e.g. annotation extraction for the
// published AAR) can run before KSP has generated anything.
tasks.configureEach {
    if (name == "extractDebugAnnotations") dependsOn("kspDebugKotlin")
    if (name == "extractReleaseAnnotations") dependsOn("kspReleaseKotlin")
    if (name == "sourceReleaseJar") dependsOn("kspReleaseKotlin")
    if (name == "javaDocReleaseGeneration") dependsOn("kspReleaseKotlin")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)
    // REST interceptors: OkHttp (also covers Retrofit, which is built on it) and Ktor's client
    // core - a consumer only using one of grpc/OkHttp/Ktor still pulls in all three client
    // libraries' interface types via this module, same tradeoff already accepted for grpc-stub.
    implementation(libs.okhttp)
    implementation(libs.ktor.client.core)

    implementation(libs.kogen.di)
    ksp(libs.kogen.di.compiler)
    implementation(libs.androidx.navigation)
    implementation(libs.koGenNavigation)
    ksp(libs.koGenNavigationCompiler)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.coroutines)

    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.coil.base)
    implementation(libs.coil)
    implementation(libs.coil.video)
    implementation(libs.zoomable)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    // Real org.json implementation so JSON parsing works in plain JVM unit tests
    // (Android's stub android.jar throws "not mocked" for org.json otherwise).
    testImplementation(libs.org.json)
}

ksp {
    arg("packageName", "com.kogen.giraffe")
    arg("includeViewModelInjector", "true")
    arg("defaultAnimation", "slideLeft")
    arg("screenSuffix", "container")
    arg("room.schemaLocation", "$projectDir/schemas")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "io.github.eugenprog"
                artifactId = "giraffe"
                version = project.version.toString()

                pom {
                    name.set("Giraffe")
                    description.set("Android gRPC traffic interceptor and in-app debug viewer")
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

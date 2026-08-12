plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.kogen.giraffeapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kogen.giraffeapp"
        minSdk = 28
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true
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

dependencies {
    // Real Giraffe in debug builds, the no-op counterpart in release - see giraffe-no-op's
    // README section. GiraffeInterceptor(...) below compiles unchanged against either.
    debugImplementation(project(":giraffe"))
    releaseImplementation(project(":giraffe-no-op"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.runtime)
    implementation(libs.coroutines)
    implementation(libs.kogen.di)
    ksp(libs.kogen.di.compiler)

    // gRPC
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.android)
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.protobuf)

    // REST test screen - one client per HTTP stack Giraffe supports, so both interceptors get
    // real exercise from the UI, not just unit tests.
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

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

    testImplementation(libs.junit)
}

ksp {
    arg("packageName", "com.kogen.giraffeapp")
    arg("includeViewModelInjector", "true")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.35.1"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.82.0"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc") {
                    option("lite")
                }
                create("grpckt")
            }
            it.builtins {
                create("java") {
                    option("lite")
                }
                create("kotlin")
            }
        }
    }
}

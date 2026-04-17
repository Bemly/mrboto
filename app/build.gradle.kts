plugins {
    id("com.android.library")
    id("maven-publish")
}

android {
    namespace = "moe.bemly.mrboto"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.3.1"
        }
    }
}

dependencies {
    implementation("com.github.equationl.paddleocr4android:ncnnandroidppocr:v1.3.0")
    api("androidx.core:core-ktx:1.18.0")
    api("androidx.appcompat:appcompat:1.7.1")
    api("com.google.android.material:material:1.13.0")
    api("androidx.drawerlayout:drawerlayout:1.2.0")
    api("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
    api("androidx.viewpager2:viewpager2:1.1.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.3.20")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "moe.bemly.mrboto"
            artifactId = "mrboto"
            version = "26.4.17"

            pom {
                name.set("mrboto")
                description.set("Embed mruby 3.4.0 in Android applications")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}

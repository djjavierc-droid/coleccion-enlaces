plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.coleccionenlaces.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.coleccionenlaces.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric necesita los recursos de Android en las pruebas unitarias.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    // Huella por carpeta (Fase 9). Única librería extra fuera del plan.
    implementation(libs.androidx.biometric)
    // FragmentActivity para BiometricPrompt (Fase 9): el prompt exige
    // una FragmentActivity o un Fragment; ComponentActivity no basta.
    implementation(libs.androidx.fragment.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.android)
    // Clasificador LiteRT (Fix 3): el modelo DistilBERT (rango
    // dinámico) corre con el intérprete base; la tokenización
    // WordPiece vive en Kotlin (domain/TokenizadorWordPiece) porque
    // la librería de metadatos (tflite-support 0.4.4) no tiene ruedas
    // para Python 3.12 y Model Maker no se instala en este entorno
    // (ver fix-3-resumen.md).
    implementation(libs.tensorflow.lite)
    // Reentrenamiento de la cabeza en reposo (Fix 3): trabajo único
    // periódico con cargador y dispositivo en reposo.
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    // Pruebas del ViewModel (Fase 5): runTest y Main de prueba.
    testImplementation(libs.kotlinx.coroutines.test)
    // Pruebas del DAO (Fase 2): Room en memoria con Robolectric, para que
    // corran en testDebugUnitTest sin emulador. room-testing es el artefacto
    // de pruebas de Room (migraciones y utilidades); Robolectric aporta el
    // Context de Android que Room necesita en la JVM.
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}

// ---- Librería nativa de TF Lite en las pruebas unitarias (Fix 3) ----
// El intérprete de LiteRT carga `libtensorflowlite_jni.so` con
// System.loadLibrary. En el APK esa .so va empaquetada por el plugin
// de Android, pero en la JVM de las pruebas unitarias no hay nada en
// java.library.path y ClasificadorModeloTest fallaba con
// UnsatisfiedLinkError (run 45185628304: 301 pruebas, 4 fallidas).
// Esta tarea extrae las .so x86_64 del AAR (incluida libc++_shared.so,
// de la que depende la JNI) a build/libs-nativas-test y las tareas de
// test las ponen en java.library.path y LD_LIBRARY_PATH.
//
// El AAR se busca en debugRuntimeClasspath (la configuración que ya
// resuelve la dependencia para el APK). Una configuración propia sin
// atributos NO sirvió: en el run 45186874597 se resolvía vacía
// ("Collection contains no element matching the predicate").
tasks.register("extraerLibsNativasTest") {
    val salida = layout.buildDirectory.dir("libs-nativas-test")
    outputs.dir(salida)
    doLast {
        val classpath = configurations.getByName("debugRuntimeClasspath")
        val todos = classpath.files.map { it.name }
        val aar = classpath.files.firstOrNull {
            it.name.contains("tensorflow", ignoreCase = true) &&
                it.name.endsWith(".aar")
        } ?: error(
            "No se encontró el AAR de tensorflow-lite en " +
                "debugRuntimeClasspath (${todos.size} archivos). " +
                "Con 'tensor'/'lite': " +
                todos.filter {
                    it.contains("tensor", ignoreCase = true) ||
                        it.contains("lite", ignoreCase = true)
                }.ifEmpty { listOf("(ninguno)") } +
                ". Todos: " + todos.joinToString(", "),
        )
        copy {
            from(zipTree(aar)) {
                include("jni/x86_64/*.so")
                eachFile {
                    // Aplana: solo el nombre del .so, sin jni/x86_64/.
                    relativePath =
                        org.gradle.api.file.RelativePath(true, name)
                }
                includeEmptyDirs = false
            }
            into(salida)
        }
    }
}

tasks.withType<Test>().configureEach {
    dependsOn("extraerLibsNativasTest")
    val dirLibs = layout.buildDirectory.dir("libs-nativas-test")
    // Se antepone el directorio a la ruta por defecto de la JVM, no
    // se sustituye: otras librerías nativas siguen encontrándose.
    systemProperty(
        "java.library.path",
        dirLibs.map {
            it.asFile.absolutePath + System.getProperty("path.separator") +
                System.getProperty("java.library.path", "")
        },
    )
    // dlopen resuelve las dependencias de la .so (libc++_shared.so)
    // con LD_LIBRARY_PATH, no con java.library.path.
    environment(
        "LD_LIBRARY_PATH",
        dirLibs.map {
            val previo = System.getenv("LD_LIBRARY_PATH").orEmpty()
            if (previo.isBlank()) it.asFile.absolutePath
            else it.asFile.absolutePath + ":" + previo
        },
    )
}

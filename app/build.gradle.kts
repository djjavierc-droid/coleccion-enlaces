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
        versionCode = 4
        versionName = "1.0.3"
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
    // Bloqueo automático al pasar a segundo plano (Fix 4, requisito 4):
    // ProcessLifecycleOwner observa el ciclo de vida de toda la app.
    implementation(libs.androidx.lifecycle.process)
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

// NOTA (Fix 3): NO se extrae la .so de LiteRT para las pruebas
// unitarias. Se intentó (runs 45186469733–45188146136): la extracción
// funciona, pero `libtensorflowlite_jni.so` del AAR está enlazada
// para Android (NEEDED: libm.so, libdl.so, liblog.so, libc.so sin
// versión) y en la JVM Linux de CI no carga ("libm.so: invalid ELF
// header", liblog.so inexistente). Ningún java.library.path lo
// arregla. Las pruebas que construyen el intérprete se saltan con
// JUnit Assume en esa JVM y corren de verdad en teléfono/emulador
// (ver ClasificadorModeloTest.kt). El modelo está verificado en
// Python con los mismos bytes del .tflite (ver fix-3-resumen.md).

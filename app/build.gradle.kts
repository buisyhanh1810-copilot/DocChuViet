plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.docchu.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.docchu.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Bản release dùng khóa debug để cài được ngay. Muốn đưa lên CH Play thì thay bằng khóa riêng.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Không nén dữ liệu nhận dạng để sao chép nhanh hơn
    androidResources {
        noCompress += "traineddata"
    }
}

dependencies {
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.8.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}

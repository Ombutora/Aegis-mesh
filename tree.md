.
├── app
│   ├── build.gradle.kts
│   └── src
│       ├── androidTest
│       │   └── java
│       │       └── com
│       │           └── aegismesh
│       │               └── ExampleInstrumentedTest.kt
│       ├── main
│       │   ├── AndroidManifest.xml
│       │   ├── cpp
│       │   │   ├── ble_mesh.cpp
│       │   │   ├── CMakeLists.txt
│       │   │   ├── gesture_processing.cpp
│       │   │   ├── native-lib.cpp
│       │   │   └── wifi_direct.cpp
│       │   ├── java
│       │   │   └── com
│       │   │       └── aegismesh
│       │   │           ├── activities
│       │   │           │   ├── EmergencyActivity.java
│       │   │           │   ├── HomeActivity.java
│       │   │           │   ├── LoginActivity.java
│       │   │           │   └── ProfileActivity.java
│       │   │           ├── database
│       │   │           │   └── EmergencyDbHelper.java
│       │   │           ├── models
│       │   │           │   ├── DispatchResult.java
│       │   │           │   ├── Emergency.java
│       │   │           │   ├── Hospital.java
│       │   │           │   └── User.java
│       │   │           ├── network
│       │   │           │   └── ApiClient.java
│       │   │           ├── sensors
│       │   │           │   └── GestureDetector.java
│       │   │           ├── services
│       │   │           │   ├── EmergencyResendWorker.java
│       │   │           │   ├── LocationService.java
│       │   │           │   ├── MeshService.java
│       │   │           │   └── SOSService.java
│       │   │           └── ui
│       │   │               └── theme
│       │   │                   ├── Color.kt
│       │   │                   ├── Theme.kt
│       │   │                   └── Type.kt
│       │   ├── keepRules
│       │   │   └── rules.keep
│       │   └── res
│       │       ├── drawable
│       │       │   ├── ic_launcher_background.xml
│       │       │   └── ic_launcher_foreground.xml
│       │       ├── mipmap-anydpi-v26
│       │       │   ├── ic_launcher_round.xml
│       │       │   └── ic_launcher.xml
│       │       ├── mipmap-hdpi
│       │       │   ├── ic_launcher_round.webp
│       │       │   └── ic_launcher.webp
│       │       ├── mipmap-mdpi
│       │       │   ├── ic_launcher_round.webp
│       │       │   └── ic_launcher.webp
│       │       ├── mipmap-xhdpi
│       │       │   ├── ic_launcher_round.webp
│       │       │   └── ic_launcher.webp
│       │       ├── mipmap-xxhdpi
│       │       │   ├── ic_launcher_round.webp
│       │       │   └── ic_launcher.webp
│       │       ├── mipmap-xxxhdpi
│       │       │   ├── ic_launcher_round.webp
│       │       │   └── ic_launcher.webp
│       │       ├── values
│       │       │   ├── colors.xml
│       │       │   ├── strings.xml
│       │       │   └── themes.xml
│       │       └── xml
│       │           ├── backup_rules.xml
│       │           └── data_extraction_rules.xml
│       └── test
│           └── java
│               └── com
│                   └── aegismesh
│                       └── ExampleUnitTest.kt
├── build.gradle.kts
├── gradle
│   ├── gradle-daemon-jvm.properties
│   ├── libs.versions.toml
│   └── wrapper
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradle.properties
├── gradlew
├── gradlew.bat
├── local.properties
├── settings.gradle.kts
└── tree.md

37 directories, 58 files

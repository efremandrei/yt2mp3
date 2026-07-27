plugins {
    id("com.android.application") version "8.12.3" apply false
}

val yt2mp3VersionCode = providers.gradleProperty("yt2mp3VersionCode").get()
val yt2mp3VersionName = providers.gradleProperty("yt2mp3VersionName").get()

tasks.register<Copy>("packageDebugApks") {
    dependsOn(":app:assembleDebug")
    doFirst {
        delete(fileTree("artifacts") {
            include("*.apk")
        })
    }
    from("app/build/outputs/apk/debug")
    include("*.apk")
    into("artifacts")
}

tasks.register("printYt2mp3Version") {
    doLast {
        println("yt2mp3 v$yt2mp3VersionName build $yt2mp3VersionCode")
    }
}

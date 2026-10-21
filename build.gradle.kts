// Intentionally empty of plugin declarations: each module declares and resolves its own plugins
// (see core/build.gradle.kts and app/build.gradle.kts). This lets `:core` configure and build in
// total isolation from `:app` when invoked with `--configure-on-demand` — notably, without ever
// needing to resolve the Android Gradle Plugin, which this sandbox's network policy cannot reach
// (see android-tv-client/README.md).
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

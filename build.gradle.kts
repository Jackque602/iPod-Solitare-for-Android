// Plugin versions are pinned centrally in gradle/libs.versions.toml.
// Each module applies exactly the plugins it needs, so the pure-JVM
// :engine module can be configured and tested without the Android
// toolchain being resolvable (useful for restricted CI environments:
// run `gradle :engine:test --configure-on-demand`).

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

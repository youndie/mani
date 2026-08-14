plugins {
    alias(libs.plugins.ktlint)
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.pluginSerialization) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.jib) apply false
}

/**
 * Стиль проверяется во всех модулях сразу: правила лежат в `.editorconfig`, здесь только запуск.
 *
 * Вендоренная compose-charts исключена файлом, а не правилом: плагин обходит исходники по путям,
 * и `ktlint = disabled` из `.editorconfig` до неё уже не доходит.
 */
// Каталог версий виден только в корневом скоупе, поэтому версия достаётся до `allprojects`.
val ktlintToolVersion = libs.versions.ktlintTool

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(ktlintToolVersion)
        filter {
            exclude { it.file.path.contains("/ir/ehsannarmani/") }
            exclude { it.file.path.contains("/build/") }
        }
    }
}

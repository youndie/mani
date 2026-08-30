plugins {
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
    // Общие соглашения сборки. Объявлены здесь и применяются в модулях — `apply false`, как и всё
    // остальное в этом блоке: плагин попадает на общий classpath сборки один раз.
    alias(libs.plugins.sborkaBase) apply false
    alias(libs.plugins.sborkaLint) apply false
}

// Стиль и координата приходят из `ru.workinprogress.sborka`: версия форматтера, исключение
// сгенерированных исходников, группа, версия и тулчейн — по одной строке в `gradle.properties`.
// Плагины применяет каждый модуль сам, поэтому `allprojects { }` здесь больше нет: блок настраивает
// проекты снаружи, конфигурационный кэш сквозь него не видит, а build-файл модуля перестаёт
// описывать этот модуль.
//
// Вендоренная compose-charts исключается там, где лежит, — в `:composeApp`: плагин обходит
// исходники по путям, и `ktlint = disabled` из `.editorconfig` до неё уже не доходит.

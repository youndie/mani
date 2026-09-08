package io.github.youndie.mani.feature.auth.data

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.div

/**
 * Файл сессии для прогона — во временном каталоге, а не в профиле того, кто его запускает.
 *
 * Без этого тесты писали токены в настоящий каталог настроек: они мешали бы друг другу между
 * прогонами и оставляли бы после себя чужую сессию на машине разработчика.
 */
internal fun temporarySessionFile(): Path = createTempDirectory("mani-session") / "session"

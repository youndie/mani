package ru.workinprogress.mani.web

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** Один файл фронтенда, каким его отдаёт сервер. */
class WebAsset(
    val path: Path,
    /** Логическое имя без `.gz` — по нему выбирается MIME и политика кэширования. */
    val name: String,
    val size: Long,
    val etag: String,
    val gzipped: Boolean,
)

/**
 * Каталог статики, снятый один раз на старте.
 *
 * `staticResources` из JVM-сборки здесь недоступен: под Kotlin/Native его в Ktor нет, как нет и
 * ресурсов внутри бинаря — файлы лежат в образе рядом. Отсюда и ручной обход.
 *
 * ETag считается по содержимому, а не берётся из метаданных: у `FileMetadata` в kotlinx-io нет
 * времени изменения, так что `Last-Modified` взять неоткуда. Без валидатора браузер качал бы
 * wasm-бандл целиком на каждый заход — `no-cache` означает «перепроверь», а перепроверять было
 * бы нечем.
 */
class WebAssets(
    private val byName: Map<String, WebAsset>,
) {
    val size: Int get() = byName.size

    fun find(
        name: String,
        acceptsGzip: Boolean,
    ): WebAsset? {
        if (acceptsGzip) byName["$name.gz"]?.let { return it }
        return byName[name]
    }

    companion object {
        private const val CHUNK = 64 * 1024

        fun scan(root: String): WebAssets {
            val found = mutableMapOf<String, WebAsset>()
            walk(Path(root), prefix = "") { relative, path, size ->
                val gzipped = relative.endsWith(".gz")
                found[relative] =
                    WebAsset(
                        path = path,
                        name = if (gzipped) relative.removeSuffix(".gz") else relative,
                        size = size,
                        etag = "\"${contentHash(path)}\"",
                        gzipped = gzipped,
                    )
            }
            return WebAssets(found)
        }

        private fun walk(
            dir: Path,
            prefix: String,
            found: (relative: String, path: Path, size: Long) -> Unit,
        ) {
            for (entry in SystemFileSystem.list(dir)) {
                val metadata = SystemFileSystem.metadataOrNull(entry) ?: continue
                val relative = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
                if (metadata.isDirectory) {
                    walk(entry, relative, found)
                } else {
                    found(relative, entry, metadata.size)
                }
            }
        }

        /**
         * Дешёвый хеш содержимого: FNV-1a. Криптостойкость здесь не нужна — валидатор кэша
         * должен меняться вместе с файлом, а не сопротивляться подбору.
         */
        private fun contentHash(path: Path): String {
            var hash = -0x340d631b_b1c5b3a1L
            val buffer = ByteArray(CHUNK)
            SystemFileSystem.source(path).buffered().use { source ->
                while (true) {
                    val read = source.readAtMostTo(buffer, 0, buffer.size)
                    if (read <= 0) break
                    for (i in 0 until read) {
                        hash = (hash xor (buffer[i].toLong() and 0xFF)) * 0x100000001b3L
                    }
                }
            }
            return hash.toULong().toString(16)
        }
    }
}

package io.github.youndie.mani.web

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** Один файл фронтенда, каким его отдаёт сервер. */
class WebAsset(
    val path: Path,
    /** Логическое имя без `.gz`/`.br` — по нему выбирается MIME и политика кэширования. */
    val name: String,
    val size: Long,
    val etag: String,
    /** Значение `Content-Encoding`; `null` — файл как есть. */
    val encoding: String?,
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
class WebAssets(private val byName: Map<String, WebAsset>) {
    val size: Int get() = byName.size

    /**
     * Лучшее из того, что есть на диске и что клиент принимает: brotli, затем gzip, затем сам файл.
     * Сжатой копии может не быть и у сжимаемого файла — сборка образа оставляет её, только
     * если она заметно меньше оригинала.
     */
    fun find(name: String, accepted: Set<String>): WebAsset? {
        for ((suffix, encoding) in ENCODINGS) {
            if (encoding in accepted) byName["$name.$suffix"]?.let { return it }
        }
        return byName[name]
    }

    companion object {
        private const val CHUNK = 64 * 1024

        /** Суффикс файла на диске → `Content-Encoding`, в порядке предпочтения. */
        private val ENCODINGS = listOf("br" to "br", "gz" to "gzip")

        fun scan(root: String): WebAssets {
            val found = mutableMapOf<String, WebAsset>()
            walk(Path(root), prefix = "") { relative, path, size ->
                val encoded = ENCODINGS.firstOrNull { (suffix, _) -> relative.endsWith(".$suffix") }
                found[relative] =
                    WebAsset(
                        path = path,
                        name = encoded?.let { (suffix, _) -> relative.removeSuffix(".$suffix") } ?: relative,
                        size = size,
                        // Хеш своего файла, а не оригинала: у каждого представления свой ETag,
                        // иначе кэш мог бы подставить gzip-тело клиенту, спросившему brotli.
                        etag = "\"${contentHash(path)}\"",
                        encoding = encoded?.second,
                    )
            }
            return WebAssets(found)
        }

        private fun walk(dir: Path, prefix: String, found: (relative: String, path: Path, size: Long) -> Unit) {
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

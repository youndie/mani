# Образ стенда: нативный бинарь, без JVM и без рантайма.
#
# Бинарь собирается **снаружи** (`./gradlew :server-native:linkReleaseExecutableLinuxX64`) и сюда
# только копируется: линковка Kotlin/Native внутри docker шла бы без кеша Gradle и занимала
# минуты на каждую сборку образа.
#
# **Версия базового образа привязана к сборочной машине, и это не формальность.** Бинарь
# слинкован с той `libmongoc`, что стояла при линковке: у Ubuntu 24.04 это 1.26. Soname
# (`libmongoc-1.0.so.0`) у веток общий, поэтому подмена не ловится ни сборкой, ни стартом — она
# проявится отсутствующим символом на первом обращении к Mongo. Значит база образа и раннер CI
# обязаны быть одной версии дистрибутива; меняете одно — меняйте оба.

FROM --platform=linux/amd64 ubuntu:24.04 AS web

RUN apt-get update \
    && apt-get install -y --no-install-recommends gzip \
    && rm -rf /var/lib/apt/lists/*

COPY composeApp/build/dist/wasmJs/productionExecutable/ /web/

# Сжимаем один раз здесь, а не на каждый запрос: `ktor-server-compression` публикуется только
# под JVM, поэтому нативный сервер отдаёт готовый `.gz`, лежащий рядом с файлом.
RUN find /web -type f \( -name '*.js' -o -name '*.mjs' -o -name '*.wasm' -o -name '*.html' \
      -o -name '*.css' -o -name '*.json' -o -name '*.svg' \) -exec gzip -k9 {} +

FROM --platform=linux/amd64 ubuntu:24.04

# `libmongoc-1.0-0t64` тянет за собой bson, sasl2, snappy, utf8proc, zstd и ssl — весь хвост,
# который показывает `ldd` по бинарю. Перечислять их поштучно не нужно и вредно: список
# разъедется с реальными зависимостями драйвера.
#
# `ca-certificates` — отдельной строкой, потому что по `ldd` его не найти: это не библиотека,
# а набор корневых сертификатов, и в `ubuntu:24.04` их нет вовсе. Сегодня наружу по https mani
# не ходит, но появление первого такого вызова иначе выглядело бы как тихий отказ, а не как
# отсутствующий пакет.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libmongoc-1.0-0t64 ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY server-native/build/bin/linuxX64/releaseExecutable/mani.kexe /app/mani
COPY --from=web /web /usr/share/mani/web

ENV MANI_WEB_ROOT=/usr/share/mani/web
ENV PORT=8080

EXPOSE 8080

ENTRYPOINT ["/app/mani"]

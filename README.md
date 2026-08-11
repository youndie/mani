# mani

![Static Badge](https://img.shields.io/badge/Android-green)
![Static Badge](https://img.shields.io/badge/iOS-black)
![Static Badge](https://img.shields.io/badge/Desktop-blue)
![Static Badge](https://img.shields.io/badge/Browser(WASM)-orange)
![Static Badge](https://img.shields.io/badge/Server(JVM)-red)
![Static Badge](https://img.shields.io/badge/Server(Kotlin%2FNative)-blueviolet)

A modern Kotlin multiplatform budget planner application built with [Ktor](https://ktor.io/) for the backend
and [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) for clients.

![Screenshot](/Screenshot.png?raw=true "screenshot")

### Demo

Check out live demo here: [mani.kotlin.website](https://mani.kotlin.website)  

### Running Locally

1. Configure server settings in `ru.workinprogress.mani.Constants.kt`:

   ```kotlin
   val currentServerConfig: ServerConfig = ServerConfig(
       name = "Local",
       scheme = "http",
       host = "<your-ip>",
       development = true,
       port = "8080"
   )
   ```

2. Build the server:

   ```bash
   ./gradlew publishImageToLocalRegistry
   ```

3. Start the server using Docker:

   ```bash
   docker-compose up -d
   ```

4. Access the web application at [http://localhost:8080/](http://localhost:8080/).

---

### Server builds

The server code lives in `:server-common` and is compiled twice. `:server` is the JVM build —
it uses the official MongoDB driver and is the one to run on macOS, where the native target
cannot be linked at all. `:server-native` is the Kotlin/Native build that ships to the demo
stand: it talks to MongoDB through [mongkn](https://github.com/youndie/mongkn), a binding over
the C driver, and serves the wasm frontend from a directory instead of the classpath.

Routing, dependency injection, configuration, token issuing and password hashing are shared —
both builds sign and verify JWTs with the same code, so a token issued by one is accepted by
the other.

The native build is `linuxX64` only, because mongkn is published for that target alone. It needs
the C driver headers on the build machine:

```bash
sudo apt-get install -y libmongoc-dev libbson-dev
```

Build the binary and the image:

```bash
./gradlew :server-native:linkReleaseExecutableLinuxX64 :composeApp:wasmJsBrowserDistribution
```

```bash
docker build -f server-native/Dockerfile -t mani-native .
```

Tests for the native storage run against a real `mongod` — the failures they look for (an `_id`
written as a string, an amount written as text) do not raise errors, they silently match nothing:

```bash
docker run -d --name mani-mongo -p 27017:27017 mongo:8
```

```bash
./gradlew :server-native:linuxX64Test
```

Configuration comes from the environment in both builds: `PORT`, `MONGO_HOST`, `MONGO_DATABASE`,
`JWT_SECRET`, `JWT_AUDIENCE`, `JWT_ISSUER`, `JWT_EXPIRATION_SECONDS`, `MANI_WEB_ROOT`,
`MANI_DEVELOPMENT`.

---

#### Running on Different Platforms:

- **Android:**  
  Build and install with:
  ```bash
  ./gradlew installDebug
  ```

- **Desktop:**  
  Run the app on desktop with:
  ```bash
  ./gradlew desktopRun
  ```

- **iOS:**  
  Open `iosApp/iosApp.xcodeproj` in Xcode and run the application, or
  use [Fleet](https://www.jetbrains.com/help/kotlin-multiplatform-dev/fleet.html) for development.

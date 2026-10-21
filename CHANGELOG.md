KnHttp Change Log
==========

Version 5.0.0 *(2026-10-21)*
----------------------------
**Breaking changes:** read the [migration guide](MIGRATION.md).

### Breaking changes

* **OkHttp responses:** `getAsOkHttpResponse()` and `executeForOkHttpResponse()` now give a response with a readable body that **the caller must close**. In 4.x its body was closed before the listener ran (bug), so it couldn't be read.
  * On the main thread, the body is read in memory before the callback, so reading it there is safe.
  * With a custom executor or in synchronous calls, it is streamed.
* **OkHttp client access:** `InternalNetworking.gi().getClient()` is replaced by `KnHttp.getOkHttpClient()`.
* **Internal classes removed:** `InternalNetworking`, `KnRequestQueue`, `Core`, `ExecutorSupplier`, `DefaultExecutorSupplier`, `KnExecutor`, `SynchronousCall`, `SourceCloseUtil`, `DownloadProgressHandler`, `UploadProgressHandler`, `InternalSSLSocketFactory` and `cache.LruCache` are replaced by a single internal engine behind the `KnHttp` static facade.
* **Priorities:** the default priority is now `MEDIUM`, and all the requests share one dispatcher (see Performance). `IMMEDIATE` requests ignore the concurrency limit.
* **Types:**
  * `Method` and `RequestType` are enums.
  * `KnRequest` and its builders are not generic anymore.
  * The `executeForXxx()`, `getAsObject()` and `getAsObjectList()` methods are typed.
  * The internal methods of `KnRequest` are not public anymore.
* **Path parameters** are percent-encoded. `addEncodedPathParameter()` inserts a raw value.
* **Error delivery:** errors are delivered on the custom executor when one is set, and a cancellation from a background thread is delivered on the delivery thread.
* **Async requests** can only be started once.
* **TLS key exchange:** X25519MLKEM768 (post-quantum) is no longer offered by default. Conscrypt 2.6+ offered it first, KnHttp now offers X25519, P-256 and P-384. Opt in with `KnSettings.Builder.setEnablePostQuantumKeyExchange(true)`.

### New

* Per-request timeouts: `setConnectTimeout()`, `setReadTimeout()`, `setWriteTimeout()` and `setCallTimeout()` override the global ones. Requests without custom timeouts pay nothing for this feature.
* `KnHttp.getOkHttpClient()` to share the client with other libraries (Socket.IO, Retrofit...).
* `KnHttp.setMaxConcurrentRequests()` (default 64, can be changed at any time).
* `KnHttp.setBitmapCacheMaxSize()`. The image cache is also halved or emptied when the system needs memory.
* `sample` module: a test app (Android 5.0+) with one button per feature, also startable from adb.
* Android Studio run configurations (`.run/`): unit tests, instrumented tests, benchmarks.
* `KnSettings.Builder.setAllowCleartextTraffic()` for plain HTTP requests.
* `KnSettings.Builder.setEnablePostQuantumKeyExchange()` to offer the X25519MLKEM768 hybrid key exchange.
* `KnSettings.Builder.setMaxIdleConnections()`, see Performance.
* `addObjectBody(Object)` serializes any object as the JSON body, directly to UTF-8 bytes.
* **Typed errors:** `KnError.getType()` tells what went wrong (`NETWORK`, `TIMEOUT`, `TLS`, `HTTP`, `PARSE`, `STORAGE`, `INVALID_REQUEST`, `CANCELLED`, `UNKNOWN`), to show the right message to the user.
  * `KnError.isRetryable()`: network errors, timeouts and HTTP 408, 425, 429, 500, 502-504.
  * `KnError.getErrorBodyAs(Class)` parses the JSON error body of an API (its message...).
  * HTTP errors have a message (`HTTP 404 Not Found`), and `toString()` shows the type.
* `addEncodedPathParameter()`, `KnError.isCancellation()`, `KnConstants.INVALID_REQUEST_ERROR`.
* Documentation:
  * plain HTTP requests (network security config);
  * local network requests on Android 17+ (`ACCESS_LOCAL_NETWORK`);
  * R8 rules for the JSON models;
  * migration guide.

### Stability

* **Exactly one callback per request:** an atomic state replaces the non-volatile flags, so a cancellation can no longer cause two callbacks or none. Unexpected exceptions and rejected requests (after `shutDown()`) now always reach the listener.
* **Uploads:** the request body is written correctly again when OkHttp retries or follows a 307/308 redirect (the sink of the first attempt was reused).
* **Downloads:**
  * the file is written to a temporary file then atomically moved;
  * an HTTP error no longer writes the error page into the destination file, and the error body stays available;
  * the cancelling threshold now works for downloads;
  * the file name may contain sub-directories, created if needed.
* **TLS 1.0/1.1:** `setAllowObsoleteTls(true)` works again. Since Conscrypt 2.6 they must be enabled on the Conscrypt provider.
* **No more OkHttp internal APIs** (`setHostnameVerifier$okhttp`, `okhttp3.internal.*` in the logging interceptor). They could break on any OkHttp update.
* **Conscrypt load failure:** if the native library is missing (e.g. unsupported ABI), KnHttp now falls back to the platform TLS stack instead of crashing.
* **Threads:** `shutDown()` now stops the threads (they used to leak on every re-init). Idle threads stop after 60 s and are named `KnHttp-*`.
* **Background init:** an error while building the client in background is logged, it can't crash the app.
* **Disk cache:** a new `init()` reuses the open cache instead of opening a second one on the same directory (it could corrupt it), and `shutDown()` closes it.
* **Logging (`BODY` level):**
  * bodies larger than 1 MiB are no longer held in memory to be logged;
  * multipart uploads no longer report a fake 100% progress before being sent;
  * requests with a per-request client are logged too.
* **Progress throttling** uses a monotonic clock (`System.nanoTime()`).
* **Logging:** `enableLogging()` no longer stacks interceptors and survives a later `init()`.
* **Invalid requests:** an invalid URL or header gives a clear `INVALID_REQUEST_ERROR`. Previously it was reported as a connection error, or the headers were silently dropped.
* **Errors while reading the body:** a connection lost or a timeout while reading the response was reported as a parse error. It is now a connection error (type `NETWORK` or `TIMEOUT`).
* **Smaller fixes:**
  * bitmap decoding could loop forever on degenerate sizes;
  * `setBitmapConfig()` was ignored when resizing;
  * a custom per-request client lost its own cache;
  * error bodies are now read up to 1 MiB.

### Performance

* **New dispatcher:** one pool of threads created on demand (released after 60 s idle), with a concurrency limit (64 by default) and a priority queue.
  * Below the limit, a request starts at once whatever its priority: a request alone never waits.
  * When a request ends, the next queued one runs on the same thread (no hand-off).
  * In 4.x, the default `IMMEDIATE` priority had only 2 threads, so parallel requests were serialized 2 by 2.
* **Faster startup:**
  * `init()` returns immediately, and the OkHttp client (Conscrypt native library, TLS context) is built in background;
  * a full throwaway `OkHttpClient` is no longer built at init.
* **Progress callbacks:** they are throttled (at most every 100 ms, plus the final one) and coalesced. Before, there was one main thread message and one allocation per 8 KiB segment.
* **No per-request `OkHttpClient`:** downloads and custom timeouts use one shared client with the needed interceptors, and per-request custom clients are derived once.
* **Bitmaps:**
  * `KnImageLoader` and its memory cache are only created when the loader is used;
  * the global bitmap decoding lock is replaced by a small semaphore, so several bitmaps can be decoded in parallel;
  * the `KnImageLoader` memory cache really uses 1/8 of the heap (it was 1024 times smaller: unit bug);
  * images are no longer delayed by 100 ms.
* **Less work per request:**
  * the URL is parsed once (instead of twice);
  * maps are allocated only when needed;
  * internal errors have no stack trace;
  * JSON bodies are serialized directly to UTF-8 bytes.
* **Worker threads** run slightly above the background priority, so they are not confined to the little cores while parsing JSON and decoding bitmaps.
* **Connection pool:** 16 idle connections are kept for reuse (OkHttp: 5). An HTTP/1.1 server needs one connection per parallel request, and every connection above the pool size had to be opened again (TCP + TLS) on the next burst. Benchmark, 32 parallel requests: -48% with a large enough pool.
* **Main thread callbacks** are asynchronous messages (API 22+, like Kotlin's `Dispatchers.Main`): a result received while a frame is being prepared is shown in this frame instead of the next one.
* **Benchmarks:** the loopback server disables Nagle's algorithm like real servers. A ~40 ms stall per request hid the real cost (~120 µs per request on a Pixel 9a). An OkHttp baseline shows the KnHttp dispatcher costs the same as OkHttp's.

### Build & Environment

* **Gradle Update:** Upgraded Gradle wrapper from 9.6.1 to 9.8.2.
* **AGP Update:** Upgraded Android Gradle Plugin from 9.3.1 to 9.4.1.
* **Version catalog:** Dependencies moved to `gradle/libs.versions.toml`. The library version is defined once, the sources jar is published, and `lintOptions` is replaced by `lint`.
* **Tests:**
  * unit tests (Robolectric + MockWebServer, TLS checked with the JVM build of Conscrypt);
  * device tests: native Conscrypt, TLS 1.3, X25519 by default and post-quantum when enabled, HTTP/2, and the Android cleartext policy;
  * a `benchmark` module and a `sample` app.
* **androidx.annotation:** now an `api` dependency. Its annotations are part of the public API, and apps without AppCompat got "unknown enum constant" warnings.
* **JitPack (`jitpack.yml`):**
  * only the library module is built;
  * JDK 21 is used even if `gradle/gradle-daemon-jvm.properties` (written by Android Studio) asks for another one;
  * the published version is the requested tag, commit or branch.

### Dependencies

* **Kotlin BOM:** Upgraded from `2.3.21` to `2.4.21`
* **OkHttp:** Upgraded from `5.4.0` to `5.5.0` (includes `okhttp` and `okhttp-brotli`)
* **Okio:** Upgraded from `3.18.1` to `3.18.2`
* **Conscrypt Android:** Upgraded from `2.6.1` to `2.7.0` (ECH support; its default X25519MLKEM768 key exchange is now opt-in, see Breaking changes)
* **AndroidX Annotation:** Upgraded from `1.10.0` to `1.11.0`
* **FASTJSON2:** Upgraded from `2.0.61.android5` to `2.0.66.android5`

Version 4.0.2 *(2026-08-03)*
----------------------------
### Build & Environment

* **SDK Bump:** Upgraded `compileSdk` and `targetSdk` to 37.
* **Gradle Update:** Upgraded Gradle wrapper from 8.14.4 to 9.6.1 and added `distributionSha256Sum` for binary verification.
* **AGP Update:** Upgraded Android Gradle Plugin (`com.android.library`) from 8.13.2 to 9.3.1.

### Dependencies

* **Kotlin BOM:** Upgraded from `2.3.20` to `2.3.21`
* **OkHttp:** Upgraded from `5.3.2` to `5.4.0` (includes `okhttp` and `okhttp-brotli`)
* **Okio:** Upgraded from `3.16.4` to `3.18.1`
* **Conscrypt Android:** Upgraded from `2.5.3` to `2.6.1`
* **AndroidX Annotation:** Upgraded from `1.9.1` to `1.10.0`

Version 4.0.1 *(2026-03-19)*
----------------------------
* Added an option to accept invalid TLS certificates

Version 4.0.0 *(2026-03-19)*
----------------------------
* Various fixes and improvements
* Bumped org.jetbrains.kotlin:kotlin-bom to 2.3.20
* **BREAKING CHANGES**
  * Replaced fastjson by fastjson2 for better security and performance
     * If you use fastjson in your code, you only need to replace your imports of com.alibaba.fastjson.* with com.alibaba.fastjson2.*

Version 3.1.6 *(2026-02-16)*
----------------------------
* Bumped com.squareup.okhttp3:okhttp to 5.3.2
* Bumped com.squareup.okhttp3:okhttp-brotli to 5.3.2
* Bumped com.squareup.okio:okio to 3.16.4
* Bumped org.jetbrains.kotlin:kotlin-bom to 2.3.10
* Bumped com.android.library to 8.13.2
* Bumped gradle to 8.14.4

Version 3.1.5 *(2025-09-15)*
----------------------------
* Added charset autodetection from HTTP Headers for the string response type

Version 3.1.4 *(2025-09-08)*
----------------------------
* Bumped com.squareup.okhttp3:okhttp to 5.1.0
* Bumped com.squareup.okhttp3:okhttp-brotli to 5.1.0
* Bumped com.squareup.okio:okio to 3.16.0
* Bumped org.jetbrains.kotlin:kotlin-bom to 2.2.10
* Bumped com.android.library to 8.13.0
* Bumped gradle to 8.14.3
* Bumped target and compile SDK to 36
* Bumped JDK to 21

Version 3.1.3 *(2025-07-29)*
----------------------------
* Bumped org.conscrypt:conscrypt-android to 2.5.3
* Bumped androidx.annotation:annotation to 1.9.1
* Bumped org.jetbrains.kotlin:kotlin-bom to 2.2.0

Version 3.1.2 *(2023-10-20)*
----------------------------
* Bump com.squareup.okhttp3:okhttp to 4.12.0
* Bump com.squareup.okhttp3:okhttp-brotli to 4.12.0
* Bump org.jetbrains.kotlin:kotlin-bom to 1.9.10
* Bump androidx.annotation:annotation to 1.7.0
* Bump com.alibaba:fastjson to 1.1.76.android
* Bump com.squareup.okio:okio to 3.6.0
* Bump com.android.library to 8.1.2
* Bump gradle to 8.0
* Bump jitpack openjdk to 17

Version 3.1.0 *(2023-07-06)*
----------------------------
* Bump compile and target Android SDK version to 33
* Bump com.squareup.okhttp3:okhttp to 4.11.0
* Bump com.squareup.okhttp3:okhttp-brotli to 4.11.0
* Bump gradle plugin to 7.4.2
* Bump gradle to 7.5
* Add org.jetbrains.kotlin:kotlin-bom:1.8.22 to improve compatibility of Kotlin versions
* Add proguard rules for compatibility of latest versions of Android Studio
* Replace androidx.appcompat:appcompat:1.5.0 by androidx.annotation:annotation:1.6.0
* Remove KnImageView class
* Various improvements

Version 3.0.5 *(2022-08-24)*
----------------------------

* Bump com.squareup.okio:okio to 3.2.0
* Bump androidx.appcompat:appcompat to 1.5.0

Version 3.0.4 *(2022-06-17)*
----------------------------

* Updated TLS 1.3 implementation

Version 3.0.3 *(2022-06-14)*
----------------------------

* Bump androidx.appcompat:appcompat to 1.4.2
* Bump com.squareup.okio:okio to 3.1.0
* Bump com.squareup.okhttp3:okhttp to 4.10.0
* Bump com.squareup.okhttp3:okhttp-brotli to 4.10.0

Version 3.0.2 *(2022-03-20)*
----------------------------

* Bump fastjson 1.1.73

Version 3.0.1 *(2022-03-20)*
----------------------------

* Use ConnectionSpec.COMPATIBLE_TLS for option AllowObsoleteTls == true

Version 3.0.0 *(2022-03-20)*
----------------------------

* KnHttp.gi() calls replaced by static calls because instance is not needed on the KnHttp class
* OkHttpResponse added to the onDownloadComplete callback
* FollowRedirect option added, false by default
* Use Priority.IMMEDIATE by default
* Default Content-Type updated for stringBody, fileBody and byteBody
* Class names updated (breaking changes)
* Various improvements

Version 2.1.1 *(2022-03-19)*
----------------------------

* Brotli support
* getInstance() replaced by gi()
* Bump compile and target Android SDK version to 32
* Bump OkHttp to 4.9.3
* Bump Okio to 3.0.0
* Bump androidx.appcompat to 1.4.1
* Bump gradle plugin to 7.1.2
* Bump gradle to 7.2
* Various improvements

Version 2.1.0 *(2021-10-05)*
----------------------------

* Bump minSdkVersion to 21
* Bump OkHttp to 4.9.1
* Bump Okio to 2.10.0

Version 2.0.8 *(2021-10-05)*
----------------------------

* Bump compile and target Android SDK version to 30
* Bump Java version to 11
* Bump org.conscrypt to 2.5.2
* Bump androidx.appcompat to 1.3.1
* Bump gradle plugin to 7.0.2
* Bump gradle to 7.0.2
* Deprecated jcenter() replaced by mavenCentral()

Version 2.0.7 *(2021-02-12)*
----------------------------

* Bump OkHttp to 3.12.13
* Bump Gradle plugin to 4.1.2

Version 2.0.6 *(2020-11-20)*
----------------------------

* Updated TLS 1.3 implementation
* Bump Conscrypt to 2.5.1
* Bump fastjson 1.1.72
* Bump androidx.appcompat to 1.2.0
* Bump Gradle plugin to 4.1.1
* Bump Gradle to 6.5
* Various improvements

Version 2.0.5 *(2020-06-20)*
----------------------------

* Bump OkHttp to 3.12.12
* Bump gradle plugin to 4.0.0
* Bump gradle to 6.1.1

Version 2.0.4 *(2020-03-27)*
----------------------------

 * Bump Conscrypt Version to 2.4.0

Version 2.0.3 *(2020-03-02)*
----------------------------

 * Bump OkHttp Version to 3.12.10
 * Bump gradle plugin version to 3.6.1
 * Bump gradle to 5.6.4

Version 2.0.2 *(2020-02-07)*
----------------------------

 * Bump OkHttp Version to 3.12.8

Version 2.0.1 *(2019-12-15)*
----------------------------

 * Bump compile and target SDK version to 29
 * Bump Okio version to 1.17.5
 * Bump gradle plugin version to 3.5.3

Version 2.0.0 *(2019-10-26)*
----------------------------

 * New library name: KnHttp
 * New package name: ovh.karewan.knhttp
 * TLS 1.3 support on all Android versions (with help of Conscrypt)
 * TLS 1.0 AND 1.1 now disabled by default (too weak)
 * Migrate code to Java 1.8
 * Singleton instead of static class
 * A new settings class can be use with the init method
 * Change default timeout (Connect 15s, Read 30s, Write 30s)
 * Merge "classic" response interfaces with okhttpresponse interfaces
 * Remove prefetch method
 * Remove ConnectionClassManager, AnalyticsListener, GzipRequestInterceptor
 * Remove useless res folder
 * Bump OkHttp Version to 3.12.6
 * Bump Okio Version to 1.17.4
 * Add [Conscrypt](https://github.com/google/conscrypt) dependency too support TLS 1.3 and ECC certificates on all Android versions
 * Add [FastJson](https://github.com/alibaba/fastjson) dependency for faster JSON parsing instead of GSON
 * Remove GSON dependency
 * Remove RxJava, RxJava2, Jackson support
 * Remove sample app
 * Migrate support library to androidx
 * Compile SDK 28, Target SDK 28, Min SDK 19
 * Bump gradle plugin version to 3.5.1
 * Unused imports removed

Fast-Android-Networking Change Log
==========

Version 1.0.2 *(2018-07-10)*
----------------------------

 * New: Add support for multiple file upload with same key
 * New: Add support for multi contentType in multipart
 * Bump OkHttp Version to 3.10.0
 * Bump other dependencies


Version 1.0.1 *(2017-12-20)*
----------------------------

 * New: Add support for `Single`, `Completable`, `Flowable`, `Maybe` Observable
 * New: Add support for OPTIONS request
 * Bump OkHttp Version to 3.9.1
 * Bump other dependencies
 * New: Add support for specifying request method dynamically
 * New: Add API to check isRequestRunning
 * Fix: Add more than one values for one key in header and query
 * Merge pull requests


Version 1.0.0 *(2017-03-19)*
----------------------------

 * Fix: Progress bug for large files download
 * Merge pull requests
 * New: Add new API
 * Bump OkHttp Version to 3.6.0
 * New: Add config options for BitmapDecode
 * New: Add Consumer Proguard


Version 0.4.0 *(2017-02-01)*
----------------------------

 * New: RxJava2 Support [link](https://amitshekhariitbhu.github.io/Fast-Android-Networking/rxjava2_support.html)
 * New: Add Java Object directly in any request [link](https://amitshekhariitbhu.github.io/Fast-Android-Networking/post_request.html)
 * New: Java Object is supported for query parameter, headers also
 * Update OkHttp to 3.5.0
 * Fix: Allow all Map implementations
 * New: Add better logging of request
 * New: Get parsed error body [link](https://amitshekhariitbhu.github.io/Fast-Android-Networking/error_code_handling.html)
 * Merged pull requests


Version 0.3.0 *(2016-11-07)*
----------------------------

 * Fix: Few minor bug fixes
 * Remove unwanted tags from manifest file


Version 0.2.0 *(2016-09-16)*
----------------------------

* New: Jackson Parser Support
* New: Making Synchronous Request - [Check Here](https://amitshekhariitbhu.github.io/Fast-Android-Networking/synchronous_request.html)
* New: setContentType("application/json; charset=utf-8") in POST and Multipart request.
* New: Getting OkHttpResponse in Response to access headers - [Check Here](https://amitshekhariitbhu.github.io/Fast-Android-Networking/getting_okhttpresponse.html)
* Bug fixes : As always we are squashing bugs.
* New: Few other features which are request by the fans of Fast Android Networking.


Version 0.1.0 *(2016-07-31)*
----------------------------

 * New: RxJava Support For Fast-Android-Networking
 * New: Now RxJava can be used with Fast-Android-Networking
 * New: Operators like `flatMap`, `filter`, `map`, `zip`, etc can be used easily with Fast-Android-Networking.
 * New: Chaining of Requests can be done.
 * New: Requests can be bind with Activity-Lifecycle.
 * New: Java Object Parsing Support


Version 0.0.1 *(2016-06-03)*
----------------------------

Initial release.

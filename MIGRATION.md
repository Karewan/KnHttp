# Migrating from KnHttp 4.x to 5.0

KnHttp 5.0 keeps the same API for the common use cases (`KnHttp.get(...).build().getAsXxx(...)`), most apps only need the steps 1 to 3.
The other changes only concern apps using advanced or internal APIs, or relying on a v4 bug.

## 1. Update the dependency

```groovy
implementation 'com.github.Karewan:KnHttp:5.0.0'
```

Java 21 is still required (`sourceCompatibility` / `targetCompatibility`), minSdk is still 21.

## 2. Close the OkHttp responses you receive (BREAKING)

`getAsOkHttpResponse()` and `executeForOkHttpResponse()` now give you a response with a **readable body**, and **you must close it**.

In v4 the body was closed by KnHttp before your listener ran (a bug): `body().string()` threw `IllegalStateException: closed`, only the status and the headers were usable.

| API | v5 behavior |
|---|---|
| `getAsOkHttpResponse()` without custom executor (main thread) | The body is read in memory before `onResponse()`: reading it on the main thread is safe and the connection is already released. Closing it is still recommended |
| `getAsOkHttpResponse()` with `setExecutor(...)` | The body is streamed from the network: you **must** close the response, on that executor. Otherwise the connection leaks (OkHttp logs "A connection to ... was leaked") |
| `executeForOkHttpResponse()` | Same as above: streamed, you **must** close it |

Consequence: without custom executor, the whole body is now downloaded before `onResponse()` (v4 discarded it). If you only need the status and the headers, use `KnHttp.head(url)`.

```java
// v4
.getAsOkHttpResponse(new OkHttpResponseListener() {
	@Override
	public void onResponse(Response okHttpRes) {
		Log.d(TAG, "Code: " + okHttpRes.code());
	}
	...
});

// v5
.getAsOkHttpResponse(new OkHttpResponseListener() {
	@Override
	public void onResponse(Response okHttpRes) {
		try (Response res = okHttpRes) { // Always close it, even if you don't read the body
			Log.d(TAG, "Code: " + res.code());
			String body = res.body().string(); // Now possible
		} catch (IOException e) {
			...
		}
	}
	...
});
```

```java
// v5, synchronous
KnResponse<Response> res = request.executeForOkHttpResponse();
if (res.isSuccess()) {
	try (Response response = res.getResult()) {
		...
	}
}
```

The responses received by the other listeners (`onResponse(String str, Response okHttpRes)`...) are still already closed: only use their status and headers.

## 3. Get the OkHttpClient with `KnHttp.getOkHttpClient()` (BREAKING)

`InternalNetworking` doesn't exist anymore (see 5.).

```java
// v4
OkHttpClient okHttpClient = InternalNetworking.gi().getClient();

// v5
OkHttpClient okHttpClient = KnHttp.getOkHttpClient();
IO.setDefaultOkHttpCallFactory(okHttpClient);
IO.setDefaultOkHttpWebSocketFactory(okHttpClient);
```

`init()` and `enableLogging()` create a new client: call `getOkHttpClient()` again after them. The client is built in background after `init()` and `getOkHttpClient()` waits for it if needed, avoid calling it on the main thread right after `init()`.

## 4. Source changes for advanced uses (BREAKING)

| v4 | v5 |
|---|---|
| `KnHttp.request(url, Method.GET)` with `int` constants | `Method` is an enum: `Method.GET` still works, raw `int` values don't |
| `int KnRequest.getMethod()` | `Method KnRequest.getMethod()` |
| `int KnRequest.getRequestType()`, `RequestType.SIMPLE` (int) | `RequestType KnRequest.getRequestType()`, `RequestType` is an enum |
| `KnRequest<T extends KnRequest>` | `KnRequest` (not generic, final) |
| `KnRequest.GetRequestBuilder<T>`, `PostRequestBuilder<T>`, `DownloadBuilder<T>`, `MultiPartBuilder<T>` | Not generic anymore, they extend `KnRequest.Builder<B>`. Remove the type arguments if you wrote some |
| `KnResponse executeForString()` (raw) | `KnResponse<String> executeForString()`, all the `executeForXxx()` are typed (`executeForObject(Class<T>)` returns `KnResponse<T>`, `executeForObjectList(Class<T>)` returns `KnResponse<List<T>>`...) |
| `getAsObject(Class, ParsedRequestListener)` (raw) | `<T> getAsObject(Class<T>, ParsedRequestListener<T>)`, `<T> getAsObjectList(Class<T>, ParsedRequestListener<List<T>>)` |
| `ovh.karewan.knhttp.cache.LruCache` | Removed, `LruBitmapCache` now extends `android.util.LruCache` (same methods) |
| `ExecutorSupplier`, `DefaultExecutorSupplier`, `KnExecutor` (two thread pools) | Removed, replaced by an internal dispatcher (single pool + concurrency limit), see `KnHttp.setMaxConcurrentRequests()` |
| `PriorityThreadFactory(int priority)` | `PriorityThreadFactory(int priority, String namePrefix)` |
| `KnImageLoader.getImageCache()` | Creates the cache on first call. `KnImageLoader.peekImageCache()` doesn't create it |
| `RequestBuilder` interface | New methods: `addEncodedPathParameter()`, `setConnectTimeout()`, `setReadTimeout()`, `setWriteTimeout()`, `setCallTimeout()`, `build()` |
| `KnConstants.UPDATE` | Removed (internal) |
| `KnError` final | Not final anymore |

Raw usages (`KnRequest request`, `KnResponse res`, raw listeners) still compile, with unchecked warnings.

## 5. Removed internal classes and methods (BREAKING)

These classes were public but internal, they are replaced by a single internal engine behind the `KnHttp` facade:

| Removed | Replacement |
|---|---|
| `InternalNetworking` | `KnHttp.getOkHttpClient()`, `KnHttp.init()`, `KnHttp.enableLogging()`, `KnHttp.setUserAgent()` |
| `KnRequestQueue` | `KnHttp.cancel()`, `KnHttp.cancelAll()`, `KnHttp.isRequestRunning()`... |
| `Core`, `ExecutorSupplier`, `DefaultExecutorSupplier`, `KnExecutor` | None (internal) |
| `SynchronousCall`, `SourceCloseUtil`, `DownloadProgressHandler`, `UploadProgressHandler` | None (internal) |
| `InternalSSLSocketFactory` | None: OkHttp's `ConnectionSpec` and the Conscrypt provider configuration do the job (TLS 1.3 is enabled by default by the bundled Conscrypt on every Android version) |

The plumbing methods of `KnRequest` are not public anymore, or are marked `@RestrictTo(LIBRARY)`: `setUserAgent()`, `setResponseAs()`, `setItemClass()`, `setProgress()`, `getDownloadProgressListener()`, `getUploadProgressListener()`, `updateDownloadCompletion()`, `destroy()`, `finish()`, `parseResponse()`, `parseNetworkError()`, `deliverError()`, `deliverResponse()`, `deliverOkHttpResponse()`, `getRequestBody()`, `getMultiPartRequestBody()`, `getHeaders()`, `getCall()`, `setCall()`, `getFuture()`, `setFuture()`, `setRunning()`, `setSequenceNumber()`.
Use the builders (`setUserAgent()`, `addHeaders()`...) and `cancel()` / `isCanceled()` / `isRunning()` instead.

## 6. Behavior changes

Check these points if your code relies on them.

### Requests
* **Path parameters are percent-encoded.** `addPathParameter("id", "a/b c")` now produces `a%2Fb%20c` (v4 inserted the raw value, which broke the URL with `/`, `?`, `#` or spaces). If you relied on the raw insertion (pre-encoded values, or several path segments on purpose), use `addEncodedPathParameter()`.
* **Invalid requests** (invalid URL, invalid header value) fail with `KnConstants.INVALID_REQUEST_ERROR`. In v4 an invalid URL failed with `CONNECTION_ERROR` and an invalid header silently dropped the headers.
* **An asynchronous request can only be started once**: calling `getAsXxx()` / `startDownload()` twice on the same `KnRequest` throws `IllegalStateException`. Synchronous requests (`executeForXxx()`) can still be executed several times.
* The order of the headers, query parameters, form parameters and multipart parts is now the insertion order (v4: `HashMap` order).
* Plain HTTP is still refused by default: it is now possible with `KnSettings.Builder.setAllowCleartextTraffic(true)` (see the README).

### Callbacks
* **Exactly one callback per request** (success, error or cancellation). In v4, a cancellation racing with the response could lead to two callbacks or none.
* **Errors are delivered on the custom executor** set with `setExecutor()`, like the successes (v4: always on the main thread).
* **Cancellation:** `onError()` with `REQUEST_CANCELLED_ERROR` is delivered on the delivery thread. When `cancel()` is called from the main thread on a request without custom executor (the common case), `onError()` is still called before `cancel()` returns, like in v4. From another thread it is now posted to the delivery thread (v4: called on the calling thread). New helper: `KnError.isCancellation()`.
* **Progress listeners** are called at most every 100 ms plus a final call (v4: on every 8 KiB segment), on the request executor when one is set (v4: always the main thread).
* `setPercentageThresholdForCancelling()` now also works for downloads (v4 only tracked the upload progress).

### Downloads
* The file is written to a temporary file (`.<name>.<n>.kntmp`, in the same directory) then moved to its destination: the destination is never partially written.
* An HTTP error (>= 400) doesn't create or overwrite the destination file anymore (v4 wrote the error page in it), and `getErrorBody()` contains the error body.
* The file name may contain sub-directories (`"videos/a.mp4"`), they are created if needed.

### Errors
* **New `KnError.getType()`** (`NETWORK`, `TIMEOUT`, `TLS`, `HTTP`, `PARSE`, `STORAGE`, `INVALID_REQUEST`, `CANCELLED`, `UNKNOWN`), plus `isRetryable()` and `getErrorBodyAs(Class)`: prefer them to `getErrorDetail()` and to inspecting `getCause()` (see the README). `getErrorDetail()` keeps its v4 values, except:
  * a connection lost or a timeout while reading the response body gives `CONNECTION_ERROR` (v4: `PARSE_ERROR`);
  * a refused cleartext request gives `INVALID_REQUEST_ERROR` (v4: `CONNECTION_ERROR`).
* HTTP errors have a message (`HTTP 404 Not Found`, v4: null) and `toString()` shows the type. `getResponse()` is closed: use `getErrorBody()`.
* `getErrorBody()` contains at most the first MiB of the body (`KnConstants.MAX_ERROR_BODY_SIZE`).
* The errors created by KnHttp have no stack trace (it was the worker thread one, meaningless and costly): use `getCause()` for connection errors.

### Priorities and threads
* **The default priority is now `MEDIUM`** (v4: `IMMEDIATE`). In v4, `IMMEDIATE` requests ran on a dedicated pool of only 2 threads: parallel requests were serialized 2 by 2.
* v5 runs all the requests on a single pool of threads created on demand (released after 60 s without work, named `KnHttp-N`). While less than 64 requests are running (`KnHttp.setMaxConcurrentRequests()`), a request starts at once whatever its priority: a request alone never waits. Above the limit, the requests wait and start by priority (`HIGH` > `MEDIUM` > `LOW`) then in order of arrival.
* `IMMEDIATE` requests always start at once, ignore the limit and are not counted in it.
* Keep `IMMEDIATE` only for the requests which must never wait, even when many requests are running.

### Bitmaps and KnImageLoader
* `setBitmapConfig()` is now applied when a max width / height is set (v4 ignored it).
* The `BitmapFactory.Options` you provide are copied for each decode (they were mutated and shared between threads). Their `inBitmap` and `inTempStorage` fields are ignored.
* Several bitmaps can be decoded in parallel (v4: one at a time for the whole app).
* The `KnImageLoader` memory cache really uses 1/8 of the heap. In v4 it was 1024 times smaller because of a unit mismatch (KB vs bytes), so it barely cached anything: expect a higher memory use, as intended.
* `KnImageLoader` delivers the batched responses without delay (v4: 100 ms). Use `KnImageLoader.gi().setBatchedResponseDelay(100)` to restore it.
* `KnImageLoader` and its cache are only created when the loader is used (`KnHttp.setBitmapDecodeOptions()`, `evictBitmap()` and `evictAllBitmap()` don't create them anymore). New `KnHttp.setBitmapCacheMaxSize()`. The cache is halved / emptied when the system needs memory.

### Networking and TLS
* `KnHttp.init()` returns immediately: the client is built in background.
* The connection pool keeps 16 idle connections (v4: OkHttp's 5), configurable with `KnSettings.Builder.setMaxIdleConnections()`.
* The main thread callbacks are posted as asynchronous messages (API 22+): they may run before your own `Handler.post()` runnables that are waiting for the end of a frame.
* **Post-quantum key exchange is now opt-in.**
  * v4 (Conscrypt 2.6.1) offered X25519MLKEM768 first. v5 offers X25519, P-256 and P-384, so the TLS ClientHello is ~1.2 KB smaller and some middleboxes and old servers no longer fail on it.
  * To keep the v4 behavior, use `KnSettings.Builder.setEnablePostQuantumKeyExchange(true)`.
* `setAllowObsoleteTls(true)` works again: since Conscrypt 2.6, TLS 1.0 / 1.1 have to be enabled on the Conscrypt provider, KnHttp 4.0.2 silently didn't support them anymore. Note that this Conscrypt setting is process-wide.
* If Conscrypt can't be loaded (e.g. no native library for the device ABI), KnHttp logs an error and uses the platform TLS stack instead of crashing.
* A custom client set with `setOkHttpClient()` on a request keeps its own cache (v4 replaced it by the KnHttp cache, even when the KnHttp cache was disabled).
* The default logger writes to Logcat with the `KnHttp` tag (v4: OkHttp's internal platform logger). `enableLogging()` can be called before or after `init()`, and calling it several times doesn't add several interceptors anymore.
* `KnHttp.shutDown()` also stops the threads and closes the idle connections. `KnHttp.cancel*()` and `isRequestRunning()` do nothing before `init()` or the first request.

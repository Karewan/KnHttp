# KnHttp

[![](https://jitpack.io/v/Karewan/KnHttp.svg)](https://jitpack.io/#Karewan/KnHttp)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Android networking library built on [OkHttp](https://square.github.io/okhttp/): JSON mapping, downloads, uploads, images, priorities and cancellation behind a single fluent API.

> Upgrading from 4.x? Read the [migration guide](MIGRATION.md).

- **Same modern TLS on every device (Android 5.0+)**: TLS 1.3, X25519 and ECC certificates through the bundled [Conscrypt](https://github.com/google/conscrypt), whatever the system TLS stack.
- **HTTP/2, Brotli and Gzip**, connection pooling, optional disk cache.
- **Fast JSON** parsing and mapping with [fastjson2](https://github.com/alibaba/fastjson2).
- **Typed errors** (`NETWORK`, `TIMEOUT`, `TLS`, `HTTP`...) to show the right message, and to know when a retry makes sense.
- **Parallel by design**: a request started alone never waits, priorities only order the queue above a configurable limit.
- **Exactly one callback per request** (success, error or cancellation), on the main thread or on your executor.
- **Per-request timeouts**, atomic downloads, throttled progress, synchronous API.

## Installation

```groovy
// settings.gradle
dependencyResolutionManagement {
	repositories {
		google()
		mavenCentral()
		maven { url 'https://jitpack.io' }
	}
}
```

```groovy
// app/build.gradle
android {
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
	}
}

dependencies {
	implementation 'com.github.Karewan:KnHttp:5.0.0'
}
```

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

```java
// Application.onCreate(): returns immediately, the client is built in background
KnHttp.init(this);
```

## Configuration

```java
KnHttp.init(this, new KnSettings.Builder()
		.setConnectTimeout(15_000)
		.setFollowRedirect(true)
		.build());
```

| Setting | Default | Effect |
|---|---|---|
| `setConnectTimeout(ms)` | 15 s | TCP and TLS connection |
| `setReadTimeout(ms)` / `setWriteTimeout(ms)` | 30 s | Between two reads / writes |
| `setCallTimeout(ms)` | 0 (none) | Whole call, redirects included |
| `setFollowRedirect(bool)` | `false` | Follow HTTP redirects |
| `setEnableCache(bool)` | `false` | 10 MB HTTP disk cache |
| `setMaxIdleConnections(n)` | 16 | Connections kept for reuse. HTTP/1.1 needs one per parallel request |
| `setEnableBrotli(bool)` | `true` | Brotli in addition to Gzip |
| `setAllowCleartextTraffic(bool)` | `false` | Allow `http://` ([details](#cleartext-http)) |
| `setAllowObsoleteTls(bool)` | `false` | Allow TLS 1.0 and 1.1 |
| `setAllowInvalidTlsCertificates(bool)` | `false` | Trust any certificate and hostname. Development only |
| `setEnablePostQuantumKeyExchange(bool)` | `false` | Offer X25519MLKEM768 ([details](#tls)) |

To use your own client instead: `KnHttp.init(okHttpClient)`.

## Requests

```java
KnHttp.get("https://api.example.com/users/{id}")
		.addPathParameter("id", "42")
		.addQueryParameter("fields", "name,email")
		.addHeaders("Authorization", "Bearer " + token)
		.setTag(this)
		.build()
		.getAsObject(User.class, new ParsedRequestListener<User>() {
			@Override
			public void onResponse(@NonNull User user, @NonNull Response okHttpRes) {
				// Main thread
			}

			@Override
			public void onError(@NonNull KnError err) {
				// Main thread
			}
		});
```

**Methods**: `get`, `head`, `options`, `post`, `put`, `delete`, `patch`, `request(url, Method)`, `download(url, dir, fileName)`, `upload(url)` (multipart).

**Results**

| Asynchronous | Listener | Synchronous |
|---|---|---|
| `getAsString` | `StringRequestListener` | `executeForString()` |
| `getAsJSONObject` / `getAsJSONArray` | `JSONObjectRequestListener` / `JSONArrayRequestListener` | `executeForJSONObject()` / `executeForJSONArray()` |
| `getAsObject(Class)` / `getAsObjectList(Class)` | `ParsedRequestListener<T>` / `ParsedRequestListener<List<T>>` | `executeForObject(Class)` / `executeForObjectList(Class)` |
| `getAsBitmap` | `BitmapRequestListener` | `executeForBitmap()` |
| `getAsOkHttpResponse` | `OkHttpResponseListener` | `executeForOkHttpResponse()` |
| `startDownload` | `DownloadListener` | `executeForDownload()` |

An HTTP status ≥ 400 is an error, except for `getAsOkHttpResponse`, which returns any response.

**Request bodies** (`post`, `put`, `delete`, `patch`, `request`)

```java
.addObjectBody(user)                  // Any object (POJO, Map, List) as JSON
.addJSONObjectBody(json)              // Also addJSONArrayBody
.addBodyParameter("key", "value")     // Form, encoded (addUrlEncodeFormBodyParameter: already encoded)
.addStringBody(text)                  // Also addByteBody, addFileBody
.setContentType("application/merge-patch+json")
```

JSON bodies are serialized to UTF-8 when added: later changes to the object are not sent.

**Path parameters**: `addPathParameter` percent-encodes the value (`/`, `?`, spaces, Unicode...). `addEncodedPathParameter` inserts it as is.

**Other options**: `setPriority`, `setExecutor` (callbacks on your executor), `setOkHttpClient` (per-request client), `setUserAgent`, `doNotCacheResponse`, `getResponseOnlyIfCached`, `getResponseOnlyFromNetwork`, `setMaxAgeCacheControl`, `setMaxStaleCacheControl`.

### OkHttp response

```java
public void onResponse(@NonNull Response okHttpRes) {
	try (Response res = okHttpRes) {
		String body = res.body().string();
	} catch (IOException e) {
		// ...
	}
}
```

- **Default (main thread)**: the body is already in memory, so reading it is safe.
- **With `setExecutor(...)` or `executeForOkHttpResponse()`**: the body is streamed and **must** be closed.
- For headers only, use `KnHttp.head(url)`.

### Synchronous requests

```java
KnResponse<User> res = KnHttp.get(url).build().executeForObject(User.class); // Never on the main thread

if (res.isSuccess()) {
	User user = res.getResult();
} else {
	KnError err = res.getError();
}
```

## Downloads and uploads

```java
KnHttp.download(url, getCacheDir().getPath(), "video.mp4")
		.build()
		.setDownloadProgressListener((downloaded, total) -> { })
		.startDownload(new DownloadListener() { ... });

KnHttp.upload(url)
		.addMultipartParameter("name", "avatar")
		.addMultipartFile("file", file, "image/jpeg")
		.build()
		.setUploadProgressListener((uploaded, total) -> { })
		.getAsJSONObject(new JSONObjectRequestListener() { ... });
```

- **Atomic downloads**: data goes to a temporary file, renamed at the end. The destination is never partially written, and is left untouched on HTTP errors.
- **Progress** is reported at most every 100 ms, plus a final event, on the delivery thread.
- **`setPercentageThresholdForCancelling(percent)`**: `cancel()` is ignored past this percentage, `forceCancel()` still applies.

## Timeouts

Each request can override the global timeouts, shorter or longer (0 = none). Requests without overrides pay nothing.

```java
KnHttp.get(url)
		.setConnectTimeout(3, TimeUnit.SECONDS) // No effect on a reused connection
		.setReadTimeout(2, TimeUnit.MINUTES)
		.setWriteTimeout(30, TimeUnit.SECONDS)
		.setCallTimeout(3, TimeUnit.MINUTES)
		...
```

## Errors

`err.getType()` tells what went wrong, to show the right message:

| `KnError.Type` | Cause | `isRetryable()` |
|---|---|---|
| `NETWORK` | No Internet, DNS failure, connection refused or lost | yes |
| `TIMEOUT` | Connect, read, write or call timeout | yes |
| `TLS` | Certificate refused (expired, untrusted, wrong host): often a wrong device date or a captive portal | no |
| `HTTP` | Status ≥ 400: `getErrorCode()`, `getErrorBody()` (first MiB), `getErrorBodyAs(Class)` | 408, 425, 429, 500, 502-504 |
| `PARSE` | Body not matching the requested type | no |
| `STORAGE` | Download file not writable (storage full, permission...) | no |
| `INVALID_REQUEST` | Invalid URL, header or body, cleartext not permitted: a bug | no |
| `CANCELLED` | Cancelled by the app (`isCancellation()`) | no |
| `UNKNOWN` | Unexpected error | no |

```java
@Override
public void onError(@NonNull KnError err) {
	if (err.isCancellation()) return;

	switch (err.getType()) {
		case NETWORK: showError(R.string.error_offline); break;
		case TIMEOUT: showError(R.string.error_timeout); break;
		case HTTP:
			ApiError body = err.getErrorBodyAs(ApiError.class); // e.g. {"message": "Invalid email"} sent by your API
			if (body != null && body.message != null) showError(body.message);
			else showError(err.getErrorCode() >= 500 ? R.string.error_server : R.string.error_refused);
			break;
		default: showError(R.string.error_generic);
	}
	retryButton.setVisibility(err.isRetryable() ? View.VISIBLE : View.GONE);
}
```

`getCause()` holds the underlying exception, for the logs. `getErrorDetail()` (`KnConstants.*_ERROR`) is kept for compatibility.

## Cancellation

```java
KnHttp.cancel(tag);       // Also forceCancel(tag), cancelAll(), forceCancelAll()
request.cancel(false);
KnHttp.isRequestRunning(tag);
```

A cancelled request receives a single `onError()` with `REQUEST_CANCELLED_ERROR`. When cancelled from the main thread (no custom executor), `onError()` runs before `cancel()` returns.

## Priorities and concurrency

All asynchronous requests run on one pool of threads, created on demand and released after 60 s idle.

- **Below the limit (64 by default)**: every request starts at once, whatever its priority.
- **Above the limit**: requests wait and start by priority (`HIGH`, `MEDIUM` (default), `LOW`), then in arrival order.
- **`IMMEDIATE`**: always starts at once and is not counted in the limit.

```java
KnHttp.setMaxConcurrentRequests(16); // At any time
```

## TLS

The bundled Conscrypt is used on every Android version: TLS 1.2 and 1.3, X25519, ECC certificates. If it can't be loaded, KnHttp logs an error and falls back to the platform TLS stack.

**Post-quantum key exchange** (X25519MLKEM768, a hybrid of X25519 and ML-KEM-768) protects today's traffic against future quantum computers ("harvest now, decrypt later").
- **Off by default**: it adds ~1.2 KB to every handshake, and some middleboxes and old servers still fail on it. KnHttp offers X25519, P-256 and P-384.
- **To enable it**: `setEnablePostQuantumKeyExchange(true)`. It is then preferred with servers that support it (Cloudflare, Google...), with X25519 as fallback.

## Cleartext HTTP

`http://` needs two opt-ins.

1. In KnHttp:
   ```java
   new KnSettings.Builder().setAllowCleartextTraffic(true)
   ```
2. In the app's [network security config](https://developer.android.com/privacy-and-security/security-config) (Android blocks cleartext since targetSdk 28):

```xml
<!-- res/xml/network_security_config.xml, referenced by android:networkSecurityConfig in the manifest -->
<network-security-config>
	<domain-config cleartextTrafficPermitted="true">
		<domain includeSubdomains="true">legacy.example.com</domain>
		<domain>192.168.1.10</domain>
	</domain-config>
</network-security-config>
```

IP ranges are not supported: for dynamic LAN addresses, `<base-config cleartextTrafficPermitted="true" />` allows every host.

If cleartext is refused, the request fails with the type `INVALID_REQUEST` and an `UnknownServiceException` cause.

## Local network on Android 17+

Apps targeting SDK 37+ need the runtime permission `ACCESS_LOCAL_NETWORK` (group "Nearby devices") to reach LAN devices (IoT, NAS, printers, `.local` names).
- **Scope**: this applies to every networking library. Internet and mobile requests are not affected.
- **Details**: see the [documentation](https://developer.android.com/privacy-and-security/local-network-permission).

```xml
<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />
```

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
		&& checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
	requestPermissions(new String[] { Manifest.permission.ACCESS_LOCAL_NETWORK }, REQUEST_CODE);
} else {
	KnHttp.get("http://192.168.1.10/status").setConnectTimeout(3, TimeUnit.SECONDS).build().getAsJSONObject(...);
}
```

- **Without the permission**: connections are blocked and the request fails with the type `NETWORK` or `TIMEOUT`. The user can revoke the permission at any time.
- **Apps targeting SDK < 37**: they keep access through `INTERNET`. Don't request the permission before targeting 37.
- **Testing on Android 16**: run `adb shell am compat enable RESTRICT_LOCAL_NETWORK <package>`, then reboot.
- **Self-signed device certificates**: use `setOkHttpClient(...)` with a client trusting that certificate, rather than `setAllowInvalidTlsCertificates`.

## Sharing the OkHttp client

Socket.IO, Retrofit... can share KnHttp's client, with its connection pool and TLS setup:

```java
OkHttpClient client = KnHttp.getOkHttpClient();
IO.setDefaultOkHttpCallFactory(client);
IO.setDefaultOkHttpWebSocketFactory(client);
```

- **Main thread**: the call builds the client if needed, so avoid it right after `init()`.
- **New client**: `init()` and `enableLogging()` create one. Call `getOkHttpClient()` again after them.

## Images

```java
KnHttp.get(imageUrl)
		.setBitmapMaxWidth(width)
		.setBitmapMaxHeight(height)
		.setBitmapConfig(Bitmap.Config.RGB_565)
		.build()
		.getAsBitmap(new BitmapRequestListener() { ... });

// ImageView loading with a memory cache
KnImageLoader.gi().get(url, KnImageLoader.getImageListener(imageView, R.drawable.placeholder, R.drawable.error), width, height);
```

The `KnImageLoader` memory cache costs nothing until first used.
- **Max size**: 1/8 of the heap by default, set with `KnHttp.setBitmapCacheMaxSize(bytes)`.
- **Memory pressure**: halved when the app goes to background, emptied when memory is low.
- **Manual eviction**: `KnHttp.evictBitmap(key)` and `KnHttp.evictAllBitmap()`.

## Miscellaneous

```java
KnHttp.enableLogging(Level.HEADERS);  // Logcat, tag "KnHttp" (default level: BASIC)
KnHttp.setUserAgent("MyApp/1.0");     // Default "KnHttp/5.0.0". Precedence: User-Agent header > setUserAgent() per request > global
KnHttp.shutDown();                    // Cancels everything, releases the threads and idle connections
```

**R8**: fastjson2 maps your models by reflection, keep them:

```
-keep class com.example.model.** { *; }
```

## Development

| Android Studio run configuration | Gradle task | Content |
|---|---|---|
| Unit tests | `:knhttp:testDebugUnitTest` | Robolectric, MockWebServer, JVM Conscrypt |
| Instrumented tests (device) | `:knhttp:connectedDebugAndroidTest` | Real device (Internet needed): bundled Conscrypt, TLS 1.3, X25519 (post-quantum when enabled), HTTP/2, Android cleartext policy |
| Benchmarks (device) | `:benchmark:connectedReleaseAndroidTest` | Microbenchmarks. Results in `benchmark/build/outputs` |

The `sample` app (Android 5.0+) has one button per feature: TLS check, JSON, parallel requests, priorities, timeouts, cancellation, transfers, images, cleartext, LAN, synchronous calls, shared client.
- **Install**: `./gradlew :sample:installDebug`.
- **Run a feature from adb** (by part of its label): `adb shell am start --activity-single-top -n ovh.karewan.knhttp.sample/.MainActivity --es run upload`.
- **Results**: written in the app and in Logcat (tags `KnHttpSample` and `KnHttp`).

## Credits

- [Amit Shekhar](https://github.com/amitshekhariitbhu) and the contributors of [Fast-Android-Networking](https://github.com/amitshekhariitbhu/Fast-Android-Networking), the origin of this library.
- [Square](https://github.com/square) for [OkHttp](https://github.com/square/okhttp) and [Okio](https://github.com/square/okio).
- [Google](https://github.com/google) for [Conscrypt](https://github.com/google/conscrypt) and the ImageLoader of [Volley](https://github.com/google/volley).
- [Alibaba](https://github.com/alibaba) for [fastjson2](https://github.com/alibaba/fastjson2).

## License

```
   Copyright (c) 2019-2026 Florent VIALATTE
   Copyright (c) 2016-2019 Amit Shekhar

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```

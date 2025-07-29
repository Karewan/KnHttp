# KnHttp

[![](https://jitpack.io/v/Karewan/KnHttp.svg)](https://jitpack.io/#Karewan/KnHttp)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

### Why use KnHttp ?
* TLS 1.3 and ECC (certificates + curves) support on all Android versions (5.0+) with help of [Conscrypt](https://github.com/google/conscrypt)
* Brotli + Gzip support
* It uses [OkHttp](http://square.github.io/okhttp/), more importantly it supports HTTP/2.
* As it uses [Okio](https://github.com/square/okio), no more GC overhead in android applications. [Okio](https://github.com/square/okio) is made to handle GC overhead while allocating memory. [Okio](https://github.com/square/okio) does some clever things to save CPU and memory.
* No other single library does each and everything like making request, downloading any type of file, uploading file, etc. There are some libraries but they are outdated.
* No other library provides simple interface for doing all types of things in networking like setting priority, cancelling, etc.
* Recent removal of HttpClient in Android Marshmallow(Android M) made other networking libraries obsolete.

## Installation

```groovy
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}
```

```groovy
android {
	compileOptions {
		sourceCompatibility JavaVersion.VERSION_11
		targetCompatibility JavaVersion.VERSION_11
	}
}

dependencies {
	implementation 'com.github.Karewan:KnHttp:3.1.3'
}
```

Do not forget to add internet permission in manifest if already not present
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Then initialize :
```java
KnHttp.init(getApplicationContext());
```
## Customization

#### Use custom settings
```java
KnSettings settings = new KnSettings.Builder()
		.setCallTimeout(0) // Call timeout ms (Default: 0)
		.setConnectTimeout(15000) // Connect timeout ms (Default: 15s)
		.setReadTimeout(30000) // Read timeout ms (Default: 30s)
		.setWriteTimeout(30000) // Write timeout ms (Default: 30s)
		.setAllowObsoleteTls(false) // Obsolete TLS 1.0 and 1.1 (Default: false)
		.setEnableCache(false) // Request caching (Default: false)
		.setEnableBrotli(true) // Brotli (+ Gzip) (Default: true), if false gzip stay enabled (OkHttp default behavior)
		.setFollowRedirect(false) // Follow redirect (Default: false)
		.build();

KnHttp.init(getApplicationContext(), settings);
```

#### Use a custom OkHttpClient

```java
OkHttpClient okHttpClient = new OkHttpClient.Builder()
		.followRedirects(false)
		.build();

KnHttp.init(okHttpClient);
```

## Asynchronous request

#### GET: response as String
```java
KnHttp.get("https://jsonplaceholder.typicode.com/posts")
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String str, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### GET: response as JSON Object
```java
KnHttp.get("https://jsonplaceholder.typicode.com/posts/{postID}")
		.addPathParameter("postID", "1")
		.build()
		.getAsJSONObject(new JSONObjectRequestListener() {
			@Override
			public void onResponse(JSONObject obj, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### GET: response as parsed Object
```java
public class PostItem {
	public int userId;
	public int id;
	public String title;
	public String body;
}

KnHttp.get("https://jsonplaceholder.typicode.com/posts/{postID}")
		.addPathParameter("postID", "1")
		.build()
		.getAsObject(PostItem.class, new ParsedRequestListener<PostItem>() {
			@Override
			public void onResponse(PostItem post, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### GET: response as JSON Array
```java
KnHttp.get("https://jsonplaceholder.typicode.com/posts")
		.build()
		.getAsJSONArray(new JSONArrayRequestListener() {
			@Override
			public void onResponse(JSONArray arr, Response okHttpRes) {
				// do anything with response
			}
			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### GET: response as parsed Object list
```java
KnHttp.get("https://jsonplaceholder.typicode.com/posts")
		.build()
		.getAsObjectList(PostItem.class, new ParsedRequestListener<List<PostItem>>() {
			@Override
			public void onResponse(List<PostItem> posts, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### GET: response as OkHttpResponse
```java
KnHttp.get("https://jsonplaceholder.typicode.com/posts")
		.build()
		.getAsOkHttpResponse(new OkHttpResponseListener() {
			@Override
			public void onResponse(Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### POST: response as parsed Object
```java
KnHttp.post("https://jsonplaceholder.typicode.com/posts")
		.addBodyParameter("title", "foo")
		.addBodyParameter("body", "bar")
		.addBodyParameter("userId", "1")
		.build()
		.getAsObject(PostItem.class, new ParsedRequestListener<PostItem>() {
			@Override
			public void onResponse(PostItem post, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### POST: send json + get response as parsed Object
```java
// Create JSON manually
JSONObject postItem = new JSONObject();
postItem.put("title", "foo");
postItem.put("body", "bar");
postItem.put("userId", 1);

// OR use class
public class PostItem {
	public int userId;
	public int id;
	public String title;
	public String body;
}
PostItem postItem = new PostItem();
JSONObject json = (JSONObject) JSON.toJSON(postItem);

KnHttp.post("https://jsonplaceholder.typicode.com/posts")
		.addJSONObjectBody(json) // Content-Type header is set automatically
		.build()
		.getAsObject(PostItem.class, new ParsedRequestListener<PostItem>() {
			@Override
			public void onResponse(PostItem post, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### PUT: send json + get response as parsed Object
```java
// Create JSON manually
JSONObject postItem = new JSONObject();
postItem.put("title", "foo");
postItem.put("body", "bar");
postItem.put("userId", 1);

// OR use class
public class PostItem {
	public int userId;
	public int id;
	public String title;
	public String body;
}
PostItem postItem = new PostItem();
JSONObject json = (JSONObject) JSON.toJSON(postItem);

KnHttp.put("https://jsonplaceholder.typicode.com/posts/{postID}")
		.addPathParameter("postId", "1")
		.addJSONObjectBody(json) // Content-Type header is set automatically
		.build()
		.getAsObject(PostItem.class, new ParsedRequestListener<PostItem>() {
			@Override
			public void onResponse(PostItem post, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### HEAD and OPTIONS request are based on the GET request constructor
```java
KnHttp.head(url)
		...

KnHttp.options(url)
		...
```

#### PUT, DELETE, PATCH request are based on the POST request constructor
```java
KnHttp.put(url)
		...

KnHttp.delete(url)
		...

KnHttp.patch(url)
		...
```

#### Download a file
```java
KnHttp.download("https://jsonplaceholder.typicode.com/posts", absoluteDirPath, "posts.json")
		.build()
		.setDownloadProgressListener(new DownloadProgressListener() {
			@Override
			public void onProgress(long bytesDownloaded, long totalBytes) {
				// on download progress
			}
		})
		.startDownload(new DownloadListener() {
			@Override
			public void onDownloadComplete(Response okHttpRes) {
				// download completed
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### Upload a file
```java
KnHttp.upload(url)
		.addMultipartFile("avatar", avatarFile)
		.build()
		.setUploadProgressListener(new UploadProgressListener() {
			@Override
			public void onProgress(long bytesUploaded, long totalBytes) {
				// on upload progress
			}
		})
		.getAsJSONObject(new JSONObjectRequestListener() {
			@Override
			public void onResponse(JSONObject obj, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### Download image as bitmap
```java
KnHttp.get(imageUrl)
		.setBitmapMaxHeight(100)
		.setBitmapMaxWidth(100)
		.setBitmapConfig(Bitmap.Config.ARGB_8888)
		.build()
		.getAsBitmap(new BitmapRequestListener() {
			@Override
			public void onResponse(Bitmap bitmap, Response okHttpRes) {
				// do anything with bitmap
			}
			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

## Synchronous requests (Do not work on the main thread)

#### GET
```java
KnRequest request = KnHttp.get("https://jsonplaceholder.typicode.com/posts").build();

KnResponse<String> res = request.executeForString();

if (res.isSuccess()) {
	String str = res.getResult();
	Response okHttpRes = res.getOkHttpResponse();
	// do anything with response
} else {
	KnError err = res.getError();
	// Handle Error
}
```

#### POST
```java
KnRequest request = KnHttp.post("https://jsonplaceholder.typicode.com/posts")
		.addBodyParameter("title", "foo")
		.addBodyParameter("body", "bar")
		.addBodyParameter("userId", "1")
		.build();

KnResponse<PostItem> res = request.executeForObject(PostItem.class);

if (res.isSuccess()) {
	PostItem post = res.getResult();
	Response okHttpRes = res.getOkHttpResponse();
	// do anything with response
} else {
	KnError err = res.getError();
	// Handle Error
}
```

#### Download
```java
KnRequest request = KnHttp.download("https://jsonplaceholder.typicode.com/posts", absoluteDirPath, "posts.json")
		.build()
		.setDownloadProgressListener(new DownloadProgressListener() {
			@Override
			public void onProgress(long bytesDownloaded, long totalBytes) {
				// on download progress
			}
		});

KnResponse res = request.executeForDownload();

if (res.isSuccess()) {
	// download complete
} else {
	KnError err = res.getError();
	// Handle Error
}
```

#### Upload

```java
KnRequest request = KnHttp.upload(url)
		.addMultipartFile("avatar", avatarFile)
		.build()
		.setUploadProgressListener(new UploadProgressListener() {
			@Override
			public void onProgress(long bytesUploaded, long totalBytes) {
				// on upload progress
			}
		});

KnResponse<PostItem> res = request.executeForObject(PostItem.class);

if (res.isSuccess()) {
	PostItem post = res.getResult();
	Response okHttpRes = res.getOkHttpResponse();
	// do anything with response
} else {
	KnError err = res.getError();
	// Handle Error
}
```

## Caching (If enabled)

#### How it's works ?

* First of all the server must send cache-control in header so that is starts working.
* Response will be cached on the basis of cache-control max-age, max-stale.
* If the internet is connected and the age is NOT expired, it will return from cache.
* If the internet is connected and the age is expired and if server returns 304(NOT MODIFIED), it will return from cache.
* If the internet is NOT connected and you are using getResponseOnlyIfCached() - it will return from cache even it the date is expired.
* If the internet is NOT connected, if you are NOT using getResponseOnlyIfCached() - it will NOT return anything.
* If you are using getResponseOnlyFromNetwork(), it will only return response after validating from the server.
* If cache-control is set, it will work according to the max-age and the max-stale returned from server.
* If the internet is NOT connected, only way to get cached response is by using getResponseOnlyIfCached().

#### Do not cache response
```java
KnHttp.get("https://jsonplaceholder.typicode.com/posts")
		.doNotCacheResponse()
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String str, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### Get response only if is cached
```java
KnHttp.get("https://jsonplaceholder.typicode.com/posts")
		.getResponseOnlyIfCached()
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String str, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### Get response only from network(internet)
```java
KnHttp.get("https://jsonplaceholder.typicode.com/posts")
		.getResponseOnlyFromNetwork()
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String str, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### Set Max Age Cache Control
```java
KnHttp.get("https://jsonplaceholder.typicode.com/posts")
		.setMaxAgeCacheControl(0, TimeUnit.SECONDS)
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String str, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### Set Max Stale Cache Control
```java
KnHttp.get("https://jsonplaceholder.typicode.com/posts")
		.setMaxStaleCacheControl(365, TimeUnit.SECONDS)
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String str, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

## Others

#### Error Code Handling
```java
public void onError(KnError err) {
	if (err.getErrorCode() != 0) {
		// received error from server
		// err.getErrorCode() - the error code from server
		// err.getErrorBody() - the error body from server
		// err.getErrorDetail() - just an error detail
		Log.d(TAG, "onError errorCode : " + err.getErrorCode());
		Log.d(TAG, "onError errorBody : " + err.getErrorBody());
		Log.d(TAG, "onError errorDetail : " + err.getErrorDetail());

		// get parsed error object (If ApiError is your class)
		ApiError apiError = err.getErrorAsObject(ApiError.class);
	} else {
		// err.getErrorDetail() :
		// KnConstants.connectionError
		// KnConstants.parseError
		// KnConstants.requestCancelledError
		Log.d(TAG, "onError errorDetail : " + err.getErrorDetail());
	}
}
```

#### Cancelling a request
```java
KnHttp.cancel("tag"); // All the requests with the given tag will be cancelled.
KnHttp.forceCancel("tag");  // All the requests with the given tag will be cancelled , even if any percent threshold is set , it will be cancelled forcefully.
KnHttp.cancelAll(); // All the requests will be cancelled.
KnHttp.forceCancelAll(); // All the requests will be cancelled , even if any percent threshold is set , it will be cancelled forcefully.
```

#### Request priority
```java
KnHttp.get("https://jsonplaceholder.typicode.com/posts")
		.setPriority(Priority.LOW)
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String str, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### Accessing Headers in Response
```java
@Override
public void onResponse(String str, Response okHttpRes) {
	Log.d(TAG, "Headers :" + okHttpRes.headers());
}
```

#### Clear Bitmap Cache
```java
KnHttp.evictBitmap(key); // remove a bitmap with key from LruCache
KnHttp.evictAllBitmap(); // clear LruCache
```

#### Logging
```java
KnHttp.enableLogging(); // simply enable logging
KnHttp.enableLogging(LEVEL.HEADERS); // enabling logging with level
```

#### Custom Executor
```java
KnHttp.get("https://jsonplaceholder.typicode.com/posts")
		.setExecutor(Executors.newSingleThreadExecutor()) // setting an executor to get response or completion on that executor thread
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String str, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### Setting custom Content-Type
```java
KnHttp.post("https://jsonplaceholder.typicode.com/posts")
		.setContentType("application/json+lama; charset=utf-8") // Custom Content-Type
		.addJSONObjectBody(json)
		.build()
		.getAsJSONObject(new JSONObjectRequestListener() {
			@Override
			public void onResponse(JSONObject obj, Response okHttpRes) {
				// do anything with response
			}
			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

#### Set global user-agent
```java
KnHttp.setUserAgent("MyApp/" + BuildConfig.VERSION_NAME);
```

#### Set per request user-agent
```java
KnHttp.get("https://jsonplaceholder.typicode.com/posts")
		.setUserAgent("MyApp/" + BuildConfig.VERSION_NAME)
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String str, Response okHttpRes) {
				// do anything with response
			}

			@Override
			public void onError(KnError err) {
				// handle error
			}
		});
```

### CREDITS
* [Amit Shekhar](https://github.com/amitshekhariitbhu) Many thanks for all his work on [Fast-Android-Networking](https://github.com/amitshekhariitbhu/Fast-Android-Networking).
* Thanks to all contributors of [Fast-Android-Networking](https://github.com/amitshekhariitbhu/Fast-Android-Networking).
* [Square](https://github.com/square) for [OkHttp](https://github.com/square/okhttp) and [Okio](https://github.com/square/okio)
* [Google](https://github.com/google) for the ImageLoader class which is part of [Volley](https://github.com/google/volley).
* [Google](https://github.com/google) for [Conscrypt](https://github.com/google/conscrypt)
* [Alibaba](https://github.com/alibaba) for [fastjson](https://github.com/alibaba/fastjson)

### License
```
   Copyright (c) 2019-2025 Florent VIALATTE
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

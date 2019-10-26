# KnHttp

[![](https://jitpack.io/v/Karewan/KnHttp.svg)](https://jitpack.io/#Karewan/KnHttp)
[![API](https://img.shields.io/badge/API-19%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=19)
[![Open Source Love](https://badges.frapsoft.com/os/v1/open-source.png?v=103)](https://opensource.org/licenses/Apache-2.0)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://github.com/Karewan/KnHttp/blob/master/LICENSE)

### About KnHttp (Fast-Android-Networking)

* https://github.com/amitshekhariitbhu/Fast-Android-Networking
* http://amitshekhariitbhu.github.io/Fast-Android-Networking

### Why use KnHttp ?
* TLS 1.3 support on all Android versions
* It uses [OkHttp](http://square.github.io/okhttp/), more importantly it supports HTTP/2.
* As it uses [Okio](https://github.com/square/okio), no more GC overhead in android applications. [Okio](https://github.com/square/okio) is made to handle GC overhead while allocating memory. [Okio](https://github.com/square/okio) does some clever things to save CPU and memory.
* No other single library does each and everything like making request, downloading any type of file, uploading file, loading image from network in ImageView, etc. There are some libraries but they are outdated.
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
dependencies {
	implementation 'com.github.Karewan:KnHttp:2.0.0'
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

##### Use custom settings
```java
ANSettings settings = new ANSettings.Builder()
		.setCallTimeout(0) // Call timeout ms (Default: 0)
		.setConnectTimeout(15000) // Connect timeout ms (Default: 15s)
		.setReadTimeout(30000) // Read timeout ms (Default: 30s)
		.setWriteTimeout(30000) // Write timeout ms (Default: 30s)
		.setAllowObsoleteTls(false) // Obsolete TLS 1.0 and 1.1 (Default: false)
		.setEnableCache(false) // Request caching (Default: false)
		.build();

KnHttp.init(getApplicationContext(), settings);
```

##### Use a custom OkHttpClient

```java
OkHttpClient okHttpClient = new OkHttpClient.Builder()
		.followRedirects(false)
		.build();

KnHttp.init(okHttpClient);
```

## Asynchronous request

##### GET: response as String
```java
KnHttp.getInstance()
		.get("https://jsonplaceholder.typicode.com/posts")
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String response, Response okHttpResponse) {
				// do anything with response
			}

			@Override
			public void onError(ANError anError) {
				// handle error
			}
	});
```

##### GET: response as JSON Object
```java
KnHttp.getInstance()
		.get("https://jsonplaceholder.typicode.com/posts/{postID}")
		.addPathParameter("postID", "1")
		.build()
		.getAsJSONObject(new JSONObjectRequestListener() {
			@Override
			public void onResponse(JSONObject response, Response okHttpResponse) {
				// do anything with response
			}

			@Override
			public void onError(ANError anError) {
				// handle error
			}
		});
```

##### GET: response as parsed Object
```java
public class PostItem {
	public int userId;
	public int id;
	public String title;
	public String body;
}

KnHttp.getInstance()
		.get("https://jsonplaceholder.typicode.com/posts/{postID}")
		.addPathParameter("postID", "1")
		.build()
		.getAsObject(PostItem.class, new ParsedRequestListener<PostItem>() {
			@Override
			public void onResponse(PostItem post, Response okHttpResponse) {
				// do anything with response
			}

			@Override
			public void onError(ANError anError) {
				// handle error
			}
		});
```

##### GET: response as JSON Array
```java
KnHttp.getInstance()
		.get("https://jsonplaceholder.typicode.com/posts")
		.build()
		.getAsJSONArray(new JSONArrayRequestListener() {
			@Override
			public void onResponse(JSONArray response, Response okHttpResponse) {
				// do anything with response
			}
			@Override
			public void onError(ANError error) {
				// handle error
			}
		});
```
##### GET: response as parsed Object list
```java
KnHttp.getInstance()
		.get("https://jsonplaceholder.typicode.com/posts")
		.build()
		.getAsObjectList(PostItem.class, new ParsedRequestListener<List<PostItem>>() {
			@Override
			public void onResponse(List<PostItem> posts, Response okHttpResponse) {
				// do anything with response
			}

			@Override
			public void onError(ANError anError) {
				// handle error
			}
		});
```

##### POST: response as parsed Object list
```java
KnHttp.getInstance()
		.post("https://jsonplaceholder.typicode.com/posts")
		.addBodyParameter("title", "foo")
		.addBodyParameter("body", "bar")
		.addBodyParameter("userId", "1")
		.build()
		.getAsObject(PostItem.class, new ParsedRequestListener<PostItem>() {
			@Override
			public void onResponse(PostItem post, Response okHttpResponse) {
				// do anything with response
			}

			@Override
			public void onError(ANError anError) {
				// handle error
			}
		});
```

##### HEAD and OPTIONS request are based on the GET request constructor
```java
KnHttp.getInstance()
		.head(url)
		...

KnHttp.getInstance()
		.options(url)
		...
```

##### PUT, DELETE, PATCH request are based on the POST request constructor
```java
KnHttp.getInstance()
		.put(url)
		...

KnHttp.getInstance()
		.delete(url)
		...

KnHttp.getInstance()
		.patch(url)
		...
```

##### Download a file
```java
KnHttp.getInstance()
		.download("https://jsonplaceholder.typicode.com/posts", absoluteDirPath, "posts.json")
		.build()
		.setDownloadProgressListener(new DownloadProgressListener() {
			@Override
			public void onProgress(long bytesDownloaded, long totalBytes) {
				// on download progress
			}
		})
		.startDownload(new DownloadListener() {
			@Override
			public void onDownloadComplete() {
				// download completed
			}

			@Override
			public void onError(ANError anError) {
				// handle error
			}
		});
```

##### Upload a file
```java
KnHttp.getInstance()
		.upload(url)
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
			public void onResponse(JSONObject response, Response okHttpResponse) {
				// do anything with response
			}

			@Override
			public void onError(ANError anError) {
				// handle error
			}
		});
```

##### Download image as bitmap
```java
KnHttp.getInstance()
		.get(imageUrl)
		.setBitmapMaxHeight(100)
		.setBitmapMaxWidth(100)
		.setBitmapConfig(Bitmap.Config.ARGB_8888)
		.build()
		.getAsBitmap(new BitmapRequestListener() {
			@Override
			public void onResponse(Bitmap bitmap, Response okHttpResponse) {
				// do anything with bitmap
			}
			@Override
			public void onError(ANError error) {
				// handle error
			}
		});
```

##### Download image directly into an ImageView
```java
<com.androidnetworking.widget.ANImageView
		android:id="@+id/imageView"
		android:layout_width="100dp"
		android:layout_height="100dp"
		android:layout_gravity="center" />

imageView.setDefaultImageResId(R.drawable.default);
imageView.setErrorImageResId(R.drawable.error);
imageView.setImageUrl(imageUrl);
```

## Synchronous requests

##### GET
```java
ANRequest request = KnHttp.getInstance()
		.get("https://jsonplaceholder.typicode.com/posts")
		.build();

ANResponse<String> response = request.executeForString();

if (response.isSuccess()) {
	String body = response.getResult();
	Response okHttpResponse = response.getOkHttpResponse();
	// do anything with response
} else {
	ANError error = response.getError();
	// Handle Error
}
```

##### POST
```java
ANRequest request = KnHttp.getInstance()
		.post("https://jsonplaceholder.typicode.com/posts")
		.addBodyParameter("title", "foo")
		.addBodyParameter("body", "bar")
		.addBodyParameter("userId", "1")
		.build();

ANResponse<PostItem> response = request.executeForObject(PostItem.class);

if (response.isSuccess()) {
	PostItem post = response.getResult();
	Response okHttpResponse = response.getOkHttpResponse();
	// do anything with response
} else {
	ANError error = response.getError();
	// Handle Error
}
```

##### Download
```java
ANRequest request = KnHttp.getInstance()
		.download("https://jsonplaceholder.typicode.com/posts", absoluteDirPath, "posts.json")
		.build()
		.setDownloadProgressListener(new DownloadProgressListener() {
			@Override
			public void onProgress(long bytesDownloaded, long totalBytes) {
				// on download progress
			}
		});

ANResponse response = request.executeForDownload();

if (response.isSuccess()) {
	// download complete
} else {
ANError error = response.getError();
	// Handle Error
}
```

##### Upload

```java
ANRequest request = KnHttp.getInstance()
		.upload(url)
		.addMultipartFile("avatar", avatarFile)
		.build()
		.setUploadProgressListener(new UploadProgressListener() {
			@Override
			public void onProgress(long bytesUploaded, long totalBytes) {
				// on upload progress
			}
		});

ANResponse<PostItem> response = request.executeForObject(PostItem.class);

if (response.isSuccess()) {
	PostItem post = response.getResult();
	Response okHttpResponse = response.getOkHttpResponse();
	// do anything with response
} else {
	ANError error = response.getError();
	// Handle Error
}
```

## Caching (If enabled)

##### How it's works ?

* First of all the server must send cache-control in header so that is starts working.
* Response will be cached on the basis of cache-control max-age, max-stale.
* If the internet is connected and the age is NOT expired, it will return from cache.
* If the internet is connected and the age is expired and if server returns 304(NOT MODIFIED), it will return from cache.
* If the internet is NOT connected and you are using getResponseOnlyIfCached() - it will return from cache even it the date is expired.
* If the internet is NOT connected, if you are NOT using getResponseOnlyIfCached() - it will NOT return anything.
* If you are using getResponseOnlyFromNetwork(), it will only return response after validating from the server.
* If cache-control is set, it will work according to the max-age and the max-stale returned from server.
* If the internet is NOT connected, only way to get cached response is by using getResponseOnlyIfCached().

##### Do not cache response
```java
KnHttp.getInstance()
		.get("https://jsonplaceholder.typicode.com/posts")
		.doNotCacheResponse()
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String response, Response okHttpResponse) {
				// do anything with response
			}

			@Override
			public void onError(ANError anError) {
				// handle error
			}
		});
```

##### Get response only if is cached
```java
KnHttp.getInstance()
		.get("https://jsonplaceholder.typicode.com/posts")
		.getResponseOnlyIfCached()
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String response, Response okHttpResponse) {
				// do anything with response
			}

			@Override
			public void onError(ANError anError) {
				// handle error
			}
		});
```

##### Get response only from network(internet)
```java
KnHttp.getInstance()
		.get("https://jsonplaceholder.typicode.com/posts")
		.getResponseOnlyFromNetwork()
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String response, Response okHttpResponse) {
				// do anything with response
			}

			@Override
			public void onError(ANError anError) {
				// handle error
			}
		});
```

##### Set Max Age Cache Control
```java
KnHttp.getInstance()
		.get("https://jsonplaceholder.typicode.com/posts")
		.setMaxAgeCacheControl(0, TimeUnit.SECONDS)
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String response, Response okHttpResponse) {
				// do anything with response
			}

			@Override
			public void onError(ANError anError) {
				// handle error
			}
		});
```

##### Set Max Stale Cache Control
```java
KnHttp.getInstance()
		.get("https://jsonplaceholder.typicode.com/posts")
		.setMaxStaleCacheControl(365, TimeUnit.SECONDS)
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String response, Response okHttpResponse) {
				// do anything with response
			}

			@Override
			public void onError(ANError anError) {
				// handle error
			}
		});
```

## Others

##### Error Code Handling
```java
public void onError(ANError error) {
	if (error.getErrorCode() != 0) {
		// received error from server
		// error.getErrorCode() - the error code from server
		// error.getErrorBody() - the error body from server
		// error.getErrorDetail() - just an error detail
		Log.d(TAG, "onError errorCode : " + error.getErrorCode());
		Log.d(TAG, "onError errorBody : " + error.getErrorBody());
		Log.d(TAG, "onError errorDetail : " + error.getErrorDetail());
		// get parsed error object (If ApiError is your class)
		ApiError apiError = error.getErrorAsObject(ApiError.class);
	} else {
		// error.getErrorDetail() :
		// ANConstants.connectionError
		// ANConstants.parseError
		// ANConstants.requestCancelledError
		Log.d(TAG, "onError errorDetail : " + error.getErrorDetail());
	}
}
```

##### Cancelling a request
```java
AndroidNetworking.cancel("tag"); // All the requests with the given tag will be cancelled.
AndroidNetworking.forceCancel("tag");  // All the requests with the given tag will be cancelled , even if any percent threshold is set , it will be cancelled forcefully. 
AndroidNetworking.cancelAll(); // All the requests will be cancelled.  
AndroidNetworking.forceCancelAll(); // All the requests will be cancelled , even if any percent threshold is set , it will be cancelled forcefully.
```

##### Accessing Headers in Response
```java
@Override
public void onResponse(String response, Response okHttpResponse) {
	Log.d(TAG, "Headers :" + okHttpResponse.headers());
}
```

##### Clear Bitmap Cache
```java
KnHttp.getInstance().evictBitmap(key); // remove a bitmap with key from LruCache
KnHttp.getInstance().evictAllBitmap(); // clear LruCache
```

##### Logging
```java
KnHttp.getInstance().enableLogging(); // simply enable logging
KnHttp.getInstance().enableLogging(LEVEL.HEADERS); // enabling logging with level
```

##### Custom Executor
```java
KnHttp.getInstance()
		.get("https://jsonplaceholder.typicode.com/posts")
		.setExecutor(Executors.newSingleThreadExecutor()) // setting an executor to get response or completion on that executor thread
		.build()
		.getAsString(new StringRequestListener() {
			@Override
			public void onResponse(String response, Response okHttpResponse) {
				// do anything with response
			}

			@Override
			public void onError(ANError anError) {
				// handle error
			}
		});
```

##### Setting Custom ContentType
```java
KnHttp.getInstance()
		.post("https://jsonplaceholder.typicode.com/posts")
		.addBodyParameter("title", "foo")
		.addBodyParameter("body", "bar")
		.addBodyParameter("userId", "1")
		.setContentType("application/json; charset=utf-8") // custom ContentType
		.build()
		.getAsJSONObject(new JSONObjectRequestListener() {
			@Override
			public void onResponse(JSONObject response, Response okHttpResponse) {
				// do anything with response
			}
			@Override
			public void onError(ANError error) {
				// handle error
			}
		});
```
### CREDITS
* [Amit Shekhar](https://github.com/amitshekhariitbhu) - Many thanks for all his work on [Fast-Android-Networking](https://github.com/amitshekhariitbhu/Fast-Android-Networking).
* Thanks to all contributors of [Fast-Android-Networking](https://github.com/amitshekhariitbhu/Fast-Android-Networking).
* [Square](https://square.github.io/) - As both [OkHttp](http://square.github.io/okhttp/) and [Okio](https://github.com/square/okio) used by KnHttp is developed by [Square](https://square.github.io/).
* [Volley](https://android.googlesource.com/platform/frameworks/volley/) - As KnHttp uses ImageLoader that is developed by [Volley](https://android.googlesource.com/platform/frameworks/volley/).

### License
```
   Copyright (c) 2019 Florent VIALATTE
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
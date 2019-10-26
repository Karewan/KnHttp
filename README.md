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
...

## Making requests

#### GET Request
...

#### POST Request
...

#### DOWNLOAD Request
...

#### UPLOAD Request
...

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

KnHttp Change Log
==========

Version 3.1.1 *(2023-10-20)*
----------------------------
* Bump com.squareup.okhttp3:okhttp to 4.12.0
* Bump com.squareup.okhttp3:okhttp-brotli to 4.12.0
* Bump org.jetbrains.kotlin:kotlin-bom to 1.9.10
* Bump androidx.annotation:annotation to 1.7.0
* Bump com.alibaba:fastjson to 1.1.76.android
* Bump com.squareup.okio:okio to 3.6.0
* Bump com.android.library to 8.1.2
* Bump gradle to 8.0

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

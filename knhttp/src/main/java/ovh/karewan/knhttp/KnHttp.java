package ovh.karewan.knhttp;

import android.content.Context;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnSettings;
import ovh.karewan.knhttp.common.Method;
import ovh.karewan.knhttp.interceptors.HttpLoggingInterceptor.Level;
import ovh.karewan.knhttp.internal.KnEngine;
import ovh.karewan.knhttp.internal.KnImageLoader;

import okhttp3.OkHttpClient;

@SuppressWarnings("unused")
public final class KnHttp {
	private KnHttp() {}

	/**
	 * Initializes KnHttp with the default config.
	 * The OkHttp client is built in background, this call returns immediately.
	 * @param context The context
	 */
	public static void init(@NonNull Context context) {
		KnEngine.get().init(context, null);
	}

	/**
	 * Initializes KnHttp with a custom config.
	 * The OkHttp client is built in background, this call returns immediately.
	 * @param context The context
	 * @param settings The settings
	 */
	public static void init(@NonNull Context context, @NonNull KnSettings settings) {
		KnEngine.get().init(context, settings);
	}

	/**
	 * Initializes KnHttp with the specified OkHttpClient.
	 * @param okHttpClient The okHttpClient
	 */
	public static void init(@NonNull OkHttpClient okHttpClient) {
		KnEngine.get().init(okHttpClient);
	}

	/**
	 * Returns the OkHttpClient used by KnHttp, to share it with other libraries (Socket.IO, Retrofit...)
	 * and benefit from the same connection pool and TLS configuration.
	 * The client is built on the first call if needed: avoid calling it on the main thread right after init().
	 * A new client is created by init() and enableLogging(), call this method again after them.
	 * @return The OkHttpClient
	 */
	@NonNull
	public static OkHttpClient getOkHttpClient() {
		return KnEngine.get().getClient();
	}

	/**
	 * Sets the max number of asynchronous requests running at the same time (64 by default).
	 * Below this limit a new request starts at once, whatever its priority. Above, the requests wait
	 * and are started by priority. {@link ovh.karewan.knhttp.common.Priority#IMMEDIATE} requests ignore the limit.
	 * Can be changed at any time.
	 * @param maxConcurrentRequests The limit, at least 1
	 */
	public static void setMaxConcurrentRequests(int maxConcurrentRequests) {
		KnEngine.get().setMaxConcurrentRequests(maxConcurrentRequests);
	}

	/**
	 * Sets the max size of the memory cache of {@link KnImageLoader} (1/8 of the heap by default).
	 * The cache is only created when KnImageLoader is used, and only holds the loaded images.
	 * @param maxSizeBytes The max size in bytes
	 */
	public static void setBitmapCacheMaxSize(int maxSizeBytes) {
		KnImageLoader.setMaxCacheSize(maxSizeBytes);
	}

	/**
	 * Method to set decodeOptions
	 * @param decodeOptions The decode config for Bitmaps
	 */
	public static void setBitmapDecodeOptions(@NonNull BitmapFactory.Options decodeOptions) {
		KnImageLoader.setDefaultBitmapDecodeOptions(decodeOptions);
	}

	/**
	 * Method to make GET request
	 * @param url The url on which request is to be made
	 * @return The GetRequestBuilder
	 */
	public static KnRequest.GetRequestBuilder get(@NonNull String url) {
		return new KnRequest.GetRequestBuilder(url);
	}

	/**
	 * Method to make HEAD request
	 * @param url The url on which request is to be made
	 * @return The HeadRequestBuilder
	 */
	public static KnRequest.HeadRequestBuilder head(@NonNull String url) {
		return new KnRequest.HeadRequestBuilder(url);
	}

	/**
	 * Method to make OPTIONS request
	 * @param url The url on which request is to be made
	 * @return The OptionsRequestBuilder
	 */
	public static KnRequest.OptionsRequestBuilder options(@NonNull String url) {
		return new KnRequest.OptionsRequestBuilder(url);
	}

	/**
	 * Method to make POST request
	 * @param url The url on which request is to be made
	 * @return The PostRequestBuilder
	 */
	public static KnRequest.PostRequestBuilder post(@NonNull String url) {
		return new KnRequest.PostRequestBuilder(url);
	}

	/**
	 * Method to make PUT request
	 * @param url The url on which request is to be made
	 * @return The PutRequestBuilder
	 */
	public static KnRequest.PutRequestBuilder put(@NonNull String url) {
		return new KnRequest.PutRequestBuilder(url);
	}

	/**
	 * Method to make DELETE request
	 * @param url The url on which request is to be made
	 * @return The DeleteRequestBuilder
	 */
	public static KnRequest.DeleteRequestBuilder delete(@NonNull String url) {
		return new KnRequest.DeleteRequestBuilder(url);
	}

	/**
	 * Method to make PATCH request
	 * @param url The url on which request is to be made
	 * @return The PatchRequestBuilder
	 */
	public static KnRequest.PatchRequestBuilder patch(@NonNull String url) {
		return new KnRequest.PatchRequestBuilder(url);
	}

	/**
	 * Method to make download request
	 * @param url      The url on which request is to be made
	 * @param dirPath  The directory path on which file is to be saved
	 * @param fileName The file name with which file is to be saved
	 * @return The DownloadBuilder
	 */
	public static KnRequest.DownloadBuilder download(@NonNull String url, @NonNull String dirPath, @NonNull String fileName) {
		return new KnRequest.DownloadBuilder(url, dirPath, fileName);
	}

	/**
	 * Method to make upload request
	 * @param url The url on which request is to be made
	 * @return The MultiPartBuilder
	 */
	public static KnRequest.MultiPartBuilder upload(@NonNull String url) {
		return new KnRequest.MultiPartBuilder(url);
	}

	/**
	 * Method to make Dynamic request
	 * @param url    The url on which request is to be made
	 * @param method The HTTP METHOD for the request
	 * @return The DynamicRequestBuilder
	 */
	public static KnRequest.DynamicRequestBuilder request(@NonNull String url, @NonNull Method method) {
		return new KnRequest.DynamicRequestBuilder(url, method);
	}

	/**
	 * Method to cancel requests with the given tag
	 *
	 * @param tag The tag with which requests are to be cancelled
	 */
	public static void cancel(@NonNull Object tag) {
		final KnEngine engine = KnEngine.peek();
		if (engine != null) engine.cancelRequestWithGivenTag(tag, false);
	}

	/**
	 * Method to force cancel requests with the given tag
	 *
	 * @param tag The tag with which requests are to be cancelled
	 */
	public static void forceCancel(@NonNull Object tag) {
		final KnEngine engine = KnEngine.peek();
		if (engine != null) engine.cancelRequestWithGivenTag(tag, true);
	}

	/**
	 * Method to cancel all given request
	 */
	public static void cancelAll() {
		final KnEngine engine = KnEngine.peek();
		if (engine != null) engine.cancelAll(false);
	}

	/**
	 * Method to force cancel all given request
	 */
	public static void forceCancelAll() {
		final KnEngine engine = KnEngine.peek();
		if (engine != null) engine.cancelAll(true);
	}

	/**
	 * Method to enable logging
	 */
	public static void enableLogging() {
		enableLogging(Level.BASIC);
	}

	/**
	 * Method to enable logging with tag, can be called before or after init()
	 * @param level The level for logging
	 */
	public static void enableLogging(@NonNull Level level) {
		KnEngine.get().enableLogging(level);
	}

	/**
	 * Method to evict a bitmap with given key from LruCache
	 * @param key The key of the bitmap
	 */
	public static void evictBitmap(@NonNull String key) {
		final KnImageLoader.ImageCache imageCache = KnImageLoader.peekImageCache();
		if (imageCache != null) imageCache.evictBitmap(key);
	}

	/**
	 * Method to clear LruCache
	 */
	public static void evictAllBitmap() {
		final KnImageLoader.ImageCache imageCache = KnImageLoader.peekImageCache();
		if (imageCache != null) imageCache.evictAllBitmap();
	}

	/**
	 * Method to set userAgent globally
	 * @param userAgent The userAgent
	 */
	public static void setUserAgent(@Nullable String userAgent) {
		KnEngine.get().setUserAgent(userAgent);
	}

	/**
	 * Method to find if the request is running or not
	 * @param tag The tag with which request running status is to be checked
	 * @return The request is running or not
	 */
	public static boolean isRequestRunning(@NonNull Object tag) {
		final KnEngine engine = KnEngine.peek();
		return engine != null && engine.isRequestRunning(tag);
	}

	/**
	 * Shuts KnHttp down: cancels all the requests, releases the threads and the idle connections.
	 * KnHttp can be used again after, init() must be called again to restore a custom config.
	 */
	public static void shutDown() {
		KnImageLoader.shutDown();
		KnEngine.shutDown();
	}
}

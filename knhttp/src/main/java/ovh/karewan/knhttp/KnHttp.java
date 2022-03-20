package ovh.karewan.knhttp;

import android.content.Context;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnSettings;
import ovh.karewan.knhttp.core.Core;
import ovh.karewan.knhttp.interceptors.HttpLoggingInterceptor.Level;
import ovh.karewan.knhttp.internal.KnImageLoader;
import ovh.karewan.knhttp.internal.KnRequestQueue;
import ovh.karewan.knhttp.internal.InternalNetworking;

import okhttp3.OkHttpClient;

@SuppressWarnings({"unused", "rawtypes"})
public final class KnHttp {
	/**
	 * Initializes KnHttp with the default config.
	 */
	public static void init(@NonNull Context context) {
		InternalNetworking.gi().setSettings(null);
		InternalNetworking.gi().initOkHttpClient(context);
		KnRequestQueue.gi();
	}

	/**
	 * Initializes KnHttp with a custom config.
	 * @param context The context
	 */
	public static void init(@NonNull Context context, @NonNull KnSettings settings) {
		InternalNetworking.gi().setSettings(settings);
		InternalNetworking.gi().initOkHttpClient(context.getApplicationContext());
		KnRequestQueue.gi();
	}


	/**
	 * Initializes KnHttp with the specified OkHttpClient.
	 * @param okHttpClient The okHttpClient
	 */
	public static void init(@NonNull OkHttpClient okHttpClient) {
		InternalNetworking.gi().setSettings(null);
		InternalNetworking.gi().setClient(okHttpClient);
		KnRequestQueue.gi();
	}

	/**
	 * Method to set decodeOptions
	 * @param decodeOptions The decode config for Bitmaps
	 */
	public static void setBitmapDecodeOptions(@NonNull BitmapFactory.Options decodeOptions) {
		KnImageLoader.gi().setBitmapDecodeOptions(decodeOptions);
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
	public static KnRequest.DynamicRequestBuilder request(@NonNull String url, int method) {
		return new KnRequest.DynamicRequestBuilder(url, method);
	}

	/**
	 * Method to cancel requests with the given tag
	 *
	 * @param tag The tag with which requests are to be cancelled
	 */
	public static void cancel(@NonNull Object tag) {
		KnRequestQueue.gi().cancelRequestWithGivenTag(tag, false);
	}

	/**
	 * Method to force cancel requests with the given tag
	 *
	 * @param tag The tag with which requests are to be cancelled
	 */
	public static void forceCancel(@NonNull Object tag) {
		KnRequestQueue.gi().cancelRequestWithGivenTag(tag, true);
	}

	/**
	 * Method to cancel all given request
	 */
	public static void cancelAll() {
		KnRequestQueue.gi().cancelAll(false);
	}

	/**
	 * Method to force cancel all given request
	 */
	public static void forceCancelAll() {
		KnRequestQueue.gi().cancelAll(true);
	}

	/**
	 * Method to enable logging
	 */
	public static void enableLogging() {
		enableLogging(Level.BASIC);
	}

	/**
	 * Method to enable logging with tag
	 * @param level The level for logging
	 */
	public static void enableLogging(@NonNull Level level) {
		InternalNetworking.gi().enableLogging(level);
	}

	/**
	 * Method to evict a bitmap with given key from LruCache
	 * @param key The key of the bitmap
	 */
	public static void evictBitmap(@NonNull String key) {
		final KnImageLoader.ImageCache imageCache = KnImageLoader.gi().getImageCache();
		if (imageCache != null) imageCache.evictBitmap(key);
	}

	/**
	 * Method to clear LruCache
	 */
	public static void evictAllBitmap() {
		final KnImageLoader.ImageCache imageCache = KnImageLoader.gi().getImageCache();
		if (imageCache != null) imageCache.evictAllBitmap();
	}

	/**
	 * Method to set userAgent globally
	 * @param userAgent The userAgent
	 */
	public static void setUserAgent(@Nullable String userAgent) {
		InternalNetworking.gi().setUserAgent(userAgent);
	}

	/**
	 * Method to find if the request is running or not
	 * @param tag The tag with which request running status is to be checked
	 * @return The request is running or not
	 */
	public static boolean isRequestRunning(@NonNull Object tag) {
		return KnRequestQueue.gi().isRequestRunning(tag);
	}

	/**
	 * Shuts KnHttp down
	 */
	public static void shutDown() {
		KnImageLoader.shutDown();
		KnRequestQueue.shutDown();
		InternalNetworking.shutDown();
		Core.shutDown();
	}
}

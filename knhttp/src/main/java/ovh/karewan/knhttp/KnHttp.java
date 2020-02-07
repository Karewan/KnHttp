/*
    KnHttp

    Copyright (c) 2019-2020 Florent VIALATTE
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
 */
package ovh.karewan.knhttp;

import android.content.Context;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ovh.karewan.knhttp.common.ANRequest;
import ovh.karewan.knhttp.common.ANSettings;
import ovh.karewan.knhttp.core.Core;
import ovh.karewan.knhttp.interceptors.HttpLoggingInterceptor.Level;
import ovh.karewan.knhttp.internal.ANImageLoader;
import ovh.karewan.knhttp.internal.ANRequestQueue;
import ovh.karewan.knhttp.internal.InternalNetworking;

import okhttp3.OkHttpClient;

@SuppressWarnings("unused")
public final class KnHttp {
    private static volatile KnHttp sInstance;

    /**
     * Private constructor to prevent instantiation of this class
     */
    private KnHttp() {}

    /**
     * Get instance
     * @return KnHttp
     */
    @NonNull
    public static KnHttp getInstance() {
        if(sInstance == null) {
            synchronized (KnHttp.class) {
                if(sInstance == null) sInstance = new KnHttp();
            }
        }

        return sInstance;
    }

    /**
     * Initializes KnHttp with the default config.
     */
    public static void init(@NonNull Context context) {
        getInstance();
        InternalNetworking.getInstance().setSettings(null);
        InternalNetworking.getInstance().initOkHttpClient(context);
        ANRequestQueue.init();
    }

    /**
     * Initializes KnHttp with a custom config.
     * @param context The context
     */
    public static void init(@NonNull Context context, @NonNull ANSettings settings) {
        getInstance();
        InternalNetworking.getInstance().setSettings(settings);
        InternalNetworking.getInstance().initOkHttpClient(context.getApplicationContext());
        ANRequestQueue.init();
    }


    /**
     * Initializes KnHttp with the specified OkHttpClient.
     * @param okHttpClient The okHttpClient
     */
    public static void init(@NonNull OkHttpClient okHttpClient) {
        getInstance();
        InternalNetworking.getInstance().setSettings(null);
        InternalNetworking.getInstance().setClient(okHttpClient);
        ANRequestQueue.init();
    }

    /**
     * Method to set decodeOptions
     *
     * @param decodeOptions The decode config for Bitmaps
     */
    public void setBitmapDecodeOptions(@NonNull BitmapFactory.Options decodeOptions) {
        ANImageLoader.getInstance().setBitmapDecodeOptions(decodeOptions);
    }

    /**
     * Method to make GET request
     *
     * @param url The url on which request is to be made
     * @return The GetRequestBuilder
     */
    public ANRequest.GetRequestBuilder get(@NonNull String url) {
        return new ANRequest.GetRequestBuilder(url);
    }

    /**
     * Method to make HEAD request
     *
     * @param url The url on which request is to be made
     * @return The HeadRequestBuilder
     */
    public ANRequest.HeadRequestBuilder head(@NonNull String url) {
        return new ANRequest.HeadRequestBuilder(url);
    }

    /**
     * Method to make OPTIONS request
     *
     * @param url The url on which request is to be made
     * @return The OptionsRequestBuilder
     */
    public ANRequest.OptionsRequestBuilder options(@NonNull String url) {
        return new ANRequest.OptionsRequestBuilder(url);
    }

    /**
     * Method to make POST request
     *
     * @param url The url on which request is to be made
     * @return The PostRequestBuilder
     */
    public ANRequest.PostRequestBuilder post(@NonNull String url) {
        return new ANRequest.PostRequestBuilder(url);
    }

    /**
     * Method to make PUT request
     *
     * @param url The url on which request is to be made
     * @return The PutRequestBuilder
     */
    public ANRequest.PutRequestBuilder put(@NonNull String url) {
        return new ANRequest.PutRequestBuilder(url);
    }

    /**
     * Method to make DELETE request
     *
     * @param url The url on which request is to be made
     * @return The DeleteRequestBuilder
     */
    public ANRequest.DeleteRequestBuilder delete(@NonNull String url) {
        return new ANRequest.DeleteRequestBuilder(url);
    }

    /**
     * Method to make PATCH request
     *
     * @param url The url on which request is to be made
     * @return The PatchRequestBuilder
     */
    public ANRequest.PatchRequestBuilder patch(@NonNull String url) {
        return new ANRequest.PatchRequestBuilder(url);
    }

    /**
     * Method to make download request
     *
     * @param url      The url on which request is to be made
     * @param dirPath  The directory path on which file is to be saved
     * @param fileName The file name with which file is to be saved
     * @return The DownloadBuilder
     */
    public ANRequest.DownloadBuilder download(@NonNull String url, @NonNull String dirPath, @NonNull String fileName) {
        return new ANRequest.DownloadBuilder(url, dirPath, fileName);
    }

    /**
     * Method to make upload request
     *
     * @param url The url on which request is to be made
     * @return The MultiPartBuilder
     */
    public ANRequest.MultiPartBuilder upload(@NonNull String url) {
        return new ANRequest.MultiPartBuilder(url);
    }

    /**
     * Method to make Dynamic request
     *
     * @param url    The url on which request is to be made
     * @param method The HTTP METHOD for the request
     * @return The DynamicRequestBuilder
     */
    public ANRequest.DynamicRequestBuilder request(@NonNull String url, int method) {
        return new ANRequest.DynamicRequestBuilder(url, method);
    }

    /**
     * Method to cancel requests with the given tag
     *
     * @param tag The tag with which requests are to be cancelled
     */
    public void cancel(@NonNull Object tag) {
        ANRequestQueue.getInstance().cancelRequestWithGivenTag(tag, false);
    }

    /**
     * Method to force cancel requests with the given tag
     *
     * @param tag The tag with which requests are to be cancelled
     */
    public void forceCancel(@NonNull Object tag) {
        ANRequestQueue.getInstance().cancelRequestWithGivenTag(tag, true);
    }

    /**
     * Method to cancel all given request
     */
    public void cancelAll() {
        ANRequestQueue.getInstance().cancelAll(false);
    }

    /**
     * Method to force cancel all given request
     */
    public void forceCancelAll() {
        ANRequestQueue.getInstance().cancelAll(true);
    }

    /**
     * Method to enable logging
     */
    public void enableLogging() {
        enableLogging(Level.BASIC);
    }

    /**
     * Method to enable logging with tag
     *
     * @param level The level for logging
     */
    public void enableLogging(@NonNull Level level) {
        InternalNetworking.getInstance().enableLogging(level);
    }

    /**
     * Method to evict a bitmap with given key from LruCache
     *
     * @param key The key of the bitmap
     */
    public void evictBitmap(@NonNull String key) {
        final ANImageLoader.ImageCache imageCache = ANImageLoader.getInstance().getImageCache();
        if (imageCache != null) imageCache.evictBitmap(key);
    }

    /**
     * Method to clear LruCache
     */
    public void evictAllBitmap() {
        final ANImageLoader.ImageCache imageCache = ANImageLoader.getInstance().getImageCache();
        if (imageCache != null) imageCache.evictAllBitmap();
    }

    /**
     * Method to set userAgent globally
     *
     * @param userAgent The userAgent
     */
    public void setUserAgent(@Nullable String userAgent) {
        InternalNetworking.getInstance().setUserAgent(userAgent);
    }

    /**
     * Method to find if the request is running or not
     *
     * @param tag The tag with which request running status is to be checked
     * @return The request is running or not
     */
    public boolean isRequestRunning(@NonNull Object tag) {
        return ANRequestQueue.getInstance().isRequestRunning(tag);
    }

    /**
     * Shuts KnHttp down
     */
    public static void shutDown() {
        if(sInstance != null) {
            Core.shutDown();
            getInstance().evictAllBitmap();
            ANImageLoader.shutDown();
            InternalNetworking.shutDown();
            ANRequestQueue.shutDown();
            synchronized (KnHttp.class) {
                sInstance = null;
            }
        }
    }
}

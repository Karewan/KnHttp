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
package ovh.karewan.knhttp.internal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.conscrypt.Conscrypt;

import ovh.karewan.knhttp.common.ANConstants;
import ovh.karewan.knhttp.common.ANRequest;
import ovh.karewan.knhttp.common.ANSettings;
import ovh.karewan.knhttp.error.ANError;
import ovh.karewan.knhttp.interceptors.HttpLoggingInterceptor;
import ovh.karewan.knhttp.interceptors.HttpLoggingInterceptor.Level;
import ovh.karewan.knhttp.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.security.Security;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionSpec;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static ovh.karewan.knhttp.common.Method.DELETE;
import static ovh.karewan.knhttp.common.Method.GET;
import static ovh.karewan.knhttp.common.Method.HEAD;
import static ovh.karewan.knhttp.common.Method.OPTIONS;
import static ovh.karewan.knhttp.common.Method.PATCH;
import static ovh.karewan.knhttp.common.Method.POST;
import static ovh.karewan.knhttp.common.Method.PUT;

public final class InternalNetworking {
    private static volatile InternalNetworking sInstance;
    private ANSettings mSettings = null;
    private OkHttpClient mHttpClient;
    private String mUserAgent = null;

    private InternalNetworking() {}

    /**
     * Get instance
     * @return InternalNetworking
     */
    @NonNull
    public static InternalNetworking getInstance() {
        if(sInstance == null) {
            synchronized (InternalNetworking.class) {
                if(sInstance == null) sInstance = new InternalNetworking();
            }
        }

        return sInstance;
    }

    public static void shutDown() {
        if(sInstance != null) {
            synchronized (InternalNetworking.class) {
                sInstance = null;
            }
        }
    }

    public void setSettings(@Nullable ANSettings settings) {
        this.mSettings = settings;
    }

    public Response performSimpleRequest(ANRequest request) throws ANError {
        Request okHttpRequest;
        Response okHttpResponse;

        try {
            Request.Builder builder = new Request.Builder().url(request.getUrl());
            addHeadersToRequestBuilder(builder, request);
            RequestBody requestBody;

            switch (request.getMethod()) {
                case GET: {
                    builder = builder.get();
                    break;
                }
                case POST: {
                    requestBody = request.getRequestBody();
                    builder = builder.post(requestBody);
                    break;
                }
                case PUT: {
                    requestBody = request.getRequestBody();
                    builder = builder.put(requestBody);
                    break;
                }
                case DELETE: {
                    requestBody = request.getRequestBody();
                    builder = builder.delete(requestBody);
                    break;
                }
                case HEAD: {
                    builder = builder.head();
                    break;
                }
                case OPTIONS: {
                    builder = builder.method(ANConstants.OPTIONS, null);
                    break;
                }
                case PATCH: {
                    requestBody = request.getRequestBody();
                    builder = builder.patch(requestBody);
                    break;
                }
            }

            if (request.getCacheControl() != null && getClient().cache() != null) builder.cacheControl(request.getCacheControl());
            okHttpRequest = builder.build();

            if (request.getOkHttpClient() != null) request.setCall(request.getOkHttpClient().newBuilder().cache(getClient().cache()).build().newCall(okHttpRequest));
            else request.setCall(getClient().newCall(okHttpRequest));

            okHttpResponse = request.getCall().execute();
        } catch (IOException ioe) {
            throw new ANError(ioe);
        }

        return okHttpResponse;
    }

    public Response performDownloadRequest(final ANRequest request) throws ANError {
        Request okHttpRequest;
        Response okHttpResponse;

        try {
            Request.Builder builder = new Request.Builder().url(request.getUrl());
            addHeadersToRequestBuilder(builder, request);
            builder = builder.get();
            if (request.getCacheControl() != null && getClient().cache() != null) builder.cacheControl(request.getCacheControl());
            okHttpRequest = builder.build();

            OkHttpClient okHttpClient;

            if (request.getOkHttpClient() != null) {
                okHttpClient = request.getOkHttpClient().newBuilder().cache(getClient().cache())
                        .addNetworkInterceptor(chain -> {
                            Response originalResponse = chain.proceed(chain.request());
                            return originalResponse.newBuilder().body(new ResponseProgressBody(originalResponse.body(), request.getDownloadProgressListener())).build();
                        }).build();
            } else {
                okHttpClient = getClient().newBuilder()
                        .addNetworkInterceptor(chain -> {
                            Response originalResponse = chain.proceed(chain.request());
                            return originalResponse.newBuilder().body(new ResponseProgressBody(originalResponse.body(), request.getDownloadProgressListener())).build();
                        }).build();
            }

            request.setCall(okHttpClient.newCall(okHttpRequest));
            okHttpResponse = request.getCall().execute();
            Utils.saveFile(okHttpResponse, request.getDirPath(), request.getFileName());
        } catch (IOException ioe) {
            try {
                File destinationFile = new File(request.getDirPath() + File.separator + request.getFileName());
                if (destinationFile.exists()) destinationFile.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }

            throw new ANError(ioe);
        }

        return okHttpResponse;
    }


    public Response performUploadRequest(ANRequest request) throws ANError {
        Request okHttpRequest;
        Response okHttpResponse;

        try {
            Request.Builder builder = new Request.Builder().url(request.getUrl());
            addHeadersToRequestBuilder(builder, request);
            final RequestBody requestBody = request.getMultiPartRequestBody();
            builder = builder.post(new RequestProgressBody(requestBody, request.getUploadProgressListener()));
            if (request.getCacheControl() != null && getClient().cache() != null) builder.cacheControl(request.getCacheControl());
            okHttpRequest = builder.build();

            if (request.getOkHttpClient() != null)  request.setCall(request.getOkHttpClient().newBuilder().cache(getClient().cache()).build().newCall(okHttpRequest));
            else request.setCall(getClient().newCall(okHttpRequest));

            okHttpResponse = request.getCall().execute();
        } catch (IOException ioe) {
            throw new ANError(ioe);
        }

        return okHttpResponse;
    }

    public OkHttpClient getClient() {
        if (mHttpClient == null) initOkHttpClient(null);
        return mHttpClient;
    }

    public void addHeadersToRequestBuilder(Request.Builder builder, ANRequest request) {
        if (request.getUserAgent() != null) builder.addHeader(ANConstants.USER_AGENT, request.getUserAgent());
        else if (mUserAgent != null) {
            request.setUserAgent(mUserAgent);
            builder.addHeader(ANConstants.USER_AGENT, mUserAgent);
        }

        Headers requestHeaders = request.getHeaders();
        if (requestHeaders != null) {
            builder.headers(requestHeaders);
            if (request.getUserAgent() != null && !requestHeaders.names().contains(ANConstants.USER_AGENT)) builder.addHeader(ANConstants.USER_AGENT, request.getUserAgent());
        }
    }

    public void setUserAgent(@Nullable String userAgent) {
        mUserAgent = userAgent;
    }

    public void enableLogging(Level level) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(level);
        mHttpClient = getClient()
                .newBuilder()
                .addInterceptor(logging)
                .build();
    }

    /**
     * Init the OkHttpClient
     * @param context The context
     */
    public void initOkHttpClient(@Nullable Context context) {
        // Use Conscrypt as the main provider
        Security.insertProviderAt(Conscrypt.newProvider(), 1);

        // If no settings, use default settings
        if(mSettings == null) mSettings = new ANSettings.Builder().build();

        // OkHttpClient Builder
        OkHttpClient.Builder okHttpBuilder = new OkHttpClient().newBuilder()
                .callTimeout(mSettings.getCallTimeout(), TimeUnit.MILLISECONDS)
                .connectTimeout(mSettings.getConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(mSettings.getReadTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(mSettings.getWriteTimeout(), TimeUnit.MILLISECONDS);

        // Cache
        if(!mSettings.isCacheEnabled() || context == null) okHttpBuilder.cache(null);
        else okHttpBuilder.cache(Utils.getCache(context.getApplicationContext(), ANConstants.MAX_CACHE_SIZE, ANConstants.CACHE_DIR_NAME));

        // ConnectionSpec
        if(!mSettings.isAllowObsoleteTls()) okHttpBuilder.connectionSpecs(Collections.singletonList(ConnectionSpec.RESTRICTED_TLS));
        else okHttpBuilder.connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS));

        try {
            // Custom SSL socket factory to add TLS 1.3 support
            okHttpBuilder.sslSocketFactory(new InternalSSLSocketFactory(mSettings.isAllowObsoleteTls()), new InternalX509TrustManager());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Build the client
        mHttpClient = okHttpBuilder.build();
    }

    /**
     * Use a custom OkHttpClient
     * @param okHttpClient The OkHttpClient
     */
    public void setClient(@NonNull OkHttpClient okHttpClient) {
        // Use Conscrypt as the main provider
        Security.insertProviderAt(Conscrypt.newProvider(), 1);

        // Set the client
        mHttpClient = okHttpClient;
    }
}

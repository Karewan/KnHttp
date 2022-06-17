package ovh.karewan.knhttp.internal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.conscrypt.Conscrypt;

import okhttp3.brotli.BrotliInterceptor;
import ovh.karewan.knhttp.common.KnConstants;
import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnSettings;
import ovh.karewan.knhttp.error.KnError;
import ovh.karewan.knhttp.interceptors.HttpLoggingInterceptor;
import ovh.karewan.knhttp.interceptors.HttpLoggingInterceptor.Level;
import ovh.karewan.knhttp.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.security.Provider;
import java.security.Security;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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

@SuppressWarnings("rawtypes")
public final class InternalNetworking {
	private static volatile InternalNetworking sInstance;
	private KnSettings mSettings = null;
	private OkHttpClient mHttpClient;
	private String mUserAgent = "knhttp/2";

	private InternalNetworking() {}

	@NonNull
	public static InternalNetworking gi() {
		if(sInstance == null) {
			synchronized (InternalNetworking.class) {
				if(sInstance == null) sInstance = new InternalNetworking();
			}
		}

		return sInstance;
	}

	public static void shutDown() {
		if(sInstance == null) return;

		synchronized (InternalNetworking.class) {
			sInstance = null;
		}
	}

	public void setSettings(@Nullable KnSettings settings) {
		this.mSettings = settings;
	}

	public Response performSimpleRequest(KnRequest request) throws KnError {
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
					builder = builder.method(KnConstants.OPTIONS, null);
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
			throw new KnError(ioe);
		}

		return okHttpResponse;
	}

	public Response performDownloadRequest(final KnRequest request) throws KnError {
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
				if (destinationFile.exists()) //noinspection ResultOfMethodCallIgnored
					destinationFile.delete();
			} catch (Exception e) {
				e.printStackTrace();
			}

			throw new KnError(ioe);
		}

		return okHttpResponse;
	}


	public Response performUploadRequest(KnRequest request) throws KnError {
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
			throw new KnError(ioe);
		}

		return okHttpResponse;
	}

	public OkHttpClient getClient() {
		if (mHttpClient == null) initOkHttpClient(null);
		return mHttpClient;
	}

	public void addHeadersToRequestBuilder(Request.Builder builder, KnRequest request) {
		if (request.getUserAgent() != null) builder.addHeader(KnConstants.USER_AGENT, request.getUserAgent());
		else if (mUserAgent != null) {
			request.setUserAgent(mUserAgent);
			builder.addHeader(KnConstants.USER_AGENT, mUserAgent);
		}

		Headers requestHeaders = request.getHeaders();
		if (requestHeaders != null) {
			builder.headers(requestHeaders);
			if (request.getUserAgent() != null && !requestHeaders.names().contains(KnConstants.USER_AGENT)) builder.addHeader(KnConstants.USER_AGENT, request.getUserAgent());
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
		// Init Conscrypt
		Provider conscrypt = Conscrypt.newProvider();

		// Add as provider
		Security.insertProviderAt(conscrypt, 1);

		// If no settings, use default settings
		if(mSettings == null) mSettings = new KnSettings.Builder().build();

		// OkHttpClient Builder
		OkHttpClient.Builder okHttpBuilder = new OkHttpClient().newBuilder()
				.callTimeout(mSettings.getCallTimeout(), TimeUnit.MILLISECONDS)
				.connectTimeout(mSettings.getConnectTimeout(), TimeUnit.MILLISECONDS)
				.readTimeout(mSettings.getReadTimeout(), TimeUnit.MILLISECONDS)
				.writeTimeout(mSettings.getWriteTimeout(), TimeUnit.MILLISECONDS)
				.followRedirects(mSettings.isFollowRedirect())
				.connectionSpecs(Collections.singletonList(mSettings.isAllowObsoleteTls() ? ConnectionSpec.COMPATIBLE_TLS : ConnectionSpec.RESTRICTED_TLS));

		// Cache
		if(!mSettings.isCacheEnabled() || context == null) okHttpBuilder.cache(null);
		else okHttpBuilder.cache(Utils.getCache(context.getApplicationContext(), KnConstants.MAX_CACHE_SIZE, KnConstants.CACHE_DIR_NAME));

		// Brotli
		if(mSettings.isBrotliEnabled()) okHttpBuilder.addInterceptor(BrotliInterceptor.INSTANCE);

		try {
			// Custom SSL socket factory to add TLS 1.3 support on all devices
			X509TrustManager tm = Conscrypt.getDefaultX509TrustManager();
			SSLContext sslContext = SSLContext.getInstance("TLS", conscrypt);
			sslContext.init(null, new TrustManager[] { tm }, null);
			okHttpBuilder.sslSocketFactory(new InternalSSLSocketFactory(sslContext.getSocketFactory(), mSettings.isAllowObsoleteTls()), tm);
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
		mHttpClient = okHttpClient;
	}
}

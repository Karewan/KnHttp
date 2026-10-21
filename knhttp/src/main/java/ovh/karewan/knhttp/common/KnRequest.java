package ovh.karewan.knhttp.common;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Looper;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import ovh.karewan.knhttp.error.KnError;
import ovh.karewan.knhttp.interfaces.BitmapRequestListener;
import ovh.karewan.knhttp.interfaces.DownloadListener;
import ovh.karewan.knhttp.interfaces.DownloadProgressListener;
import ovh.karewan.knhttp.interfaces.JSONArrayRequestListener;
import ovh.karewan.knhttp.interfaces.JSONObjectRequestListener;
import ovh.karewan.knhttp.interfaces.OkHttpResponseListener;
import ovh.karewan.knhttp.interfaces.ParsedRequestListener;
import ovh.karewan.knhttp.interfaces.StringRequestListener;
import ovh.karewan.knhttp.interfaces.UploadProgressListener;
import ovh.karewan.knhttp.internal.KnEngine;
import ovh.karewan.knhttp.internal.ProgressReporter;
import ovh.karewan.knhttp.internal.RequestExecutor;
import ovh.karewan.knhttp.model.MultipartFileBody;
import ovh.karewan.knhttp.model.MultipartStringBody;
import ovh.karewan.knhttp.utils.Utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@SuppressWarnings("unused")
public final class KnRequest {
	private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
	private static final MediaType MEDIA_TYPE_TEXT = MediaType.parse("text/plain; charset=utf-8");
	private static final MediaType MEDIA_TYPE_OCTET_STREAM = MediaType.parse("application/octet-stream");
	private static final long NO_TIMEOUT_OVERRIDE = -1;

	// Configuration (immutable)
	private final RequestType mRequestType;
	private final Method mMethod;
	private final Priority mPriority;
	private final String mUrl;
	private final Object mTag;
	private final Map<String, List<String>> mHeadersMap;
	private final Map<String, List<String>> mQueryParameterMap;
	private final Map<String, String> mPathParameterMap;
	private final CacheControl mCacheControl;
	private final Executor mExecutor;
	private final OkHttpClient mOkHttpClient;
	private final String mUserAgent;
	private final long mConnectTimeoutMs;
	private final long mReadTimeoutMs;
	private final long mWriteTimeoutMs;
	private final long mCallTimeoutMs;

	// Body (POST, PUT, DELETE, PATCH)
	private final byte[] mJsonBody;
	private final String mStringBody;
	private final byte[] mByteBody;
	private final File mFileBody;
	private final Map<String, String> mBodyParameterMap;
	private final Map<String, String> mUrlEncodedFormBodyParameterMap;
	private final MediaType mCustomMediaType;

	// Multipart
	private final Map<String, MultipartStringBody> mMultiPartParameterMap;
	private final Map<String, List<MultipartFileBody>> mMultiPartFileMap;

	// Download
	private final String mDirPath;
	private final String mFileName;
	private final int mPercentageThresholdForCancelling;

	// Bitmap
	private final Bitmap.Config mDecodeConfig;
	private final BitmapFactory.Options mBitmapOptions;
	private final int mMaxWidth;
	private final int mMaxHeight;
	private final ImageView.ScaleType mScaleType;

	// Response
	private ResponseType mResponseType;
	private Type mItemClass;
	private Object mListener;
	private DownloadProgressListener mDownloadProgressListener;
	private UploadProgressListener mUploadProgressListener;
	private ProgressReporter mDownloadProgressReporter;
	private ProgressReporter mUploadProgressReporter;

	// State
	private int mSequenceNumber;
	private KnEngine mEngine;
	private boolean mAsync;
	private final AtomicBoolean mStarted = new AtomicBoolean();
	private final AtomicBoolean mDelivered = new AtomicBoolean();
	private volatile boolean mCancelled;
	private volatile boolean mRunning;
	private volatile int mProgress;
	private volatile Call mCall;

	private KnRequest(@NonNull Builder<?> builder, @NonNull RequestType requestType) {
		this.mRequestType = requestType;
		this.mMethod = builder.mMethod;
		this.mPriority = builder.mPriority != null ? builder.mPriority : Priority.MEDIUM;
		this.mUrl = builder.mUrl;
		this.mTag = builder.mTag;
		this.mHeadersMap = builder.mHeadersMap;
		this.mQueryParameterMap = builder.mQueryParameterMap;
		this.mPathParameterMap = builder.mPathParameterMap;
		this.mCacheControl = builder.mCacheControl;
		this.mExecutor = builder.mExecutor;
		this.mOkHttpClient = builder.mOkHttpClient;
		this.mUserAgent = builder.mUserAgent;
		this.mConnectTimeoutMs = builder.mConnectTimeoutMs;
		this.mReadTimeoutMs = builder.mReadTimeoutMs;
		this.mWriteTimeoutMs = builder.mWriteTimeoutMs;
		this.mCallTimeoutMs = builder.mCallTimeoutMs;

		final PostRequestBuilder post = builder instanceof PostRequestBuilder ? (PostRequestBuilder) builder : null;
		this.mJsonBody = post != null ? post.mJsonBody : null;
		this.mStringBody = post != null ? post.mStringBody : null;
		this.mByteBody = post != null ? post.mByteBody : null;
		this.mFileBody = post != null ? post.mFileBody : null;
		this.mBodyParameterMap = post != null ? post.mBodyParameterMap : null;
		this.mUrlEncodedFormBodyParameterMap = post != null ? post.mUrlEncodedFormBodyParameterMap : null;

		final MultiPartBuilder multipart = builder instanceof MultiPartBuilder ? (MultiPartBuilder) builder : null;
		this.mMultiPartParameterMap = multipart != null ? multipart.mMultiPartParameterMap : null;
		this.mMultiPartFileMap = multipart != null ? multipart.mMultiPartFileMap : null;

		final String customContentType = post != null ? post.mCustomContentType : (multipart != null ? multipart.mCustomContentType : null);
		this.mCustomMediaType = customContentType != null ? MediaType.parse(customContentType) : null;

		final DownloadBuilder download = builder instanceof DownloadBuilder ? (DownloadBuilder) builder : null;
		this.mDirPath = download != null ? download.mDirPath : null;
		this.mFileName = download != null ? download.mFileName : null;
		this.mPercentageThresholdForCancelling = download != null ? download.mPercentageThresholdForCancelling
				: (multipart != null ? multipart.mPercentageThresholdForCancelling : 0);

		final GetRequestBuilder get = builder instanceof GetRequestBuilder ? (GetRequestBuilder) builder : null;
		this.mDecodeConfig = get != null ? get.mDecodeConfig : null;
		this.mBitmapOptions = get != null ? get.mBitmapOptions : null;
		this.mMaxWidth = get != null ? get.mMaxWidth : 0;
		this.mMaxHeight = get != null ? get.mMaxHeight : 0;
		this.mScaleType = get != null ? get.mScaleType : null;
	}

	// region Asynchronous API

	public void getAsJSONObject(JSONObjectRequestListener requestListener) {
		start(ResponseType.JSON_OBJECT, null, requestListener);
	}

	public void getAsJSONArray(JSONArrayRequestListener requestListener) {
		start(ResponseType.JSON_ARRAY, null, requestListener);
	}

	public void getAsString(StringRequestListener requestListener) {
		start(ResponseType.STRING, null, requestListener);
	}

	/**
	 * The listener receives the {@link Response} with a readable body, whatever its HTTP status. Close it (try-with-resources).
	 * <ul>
	 * <li>Main thread delivery (default): the body is read in memory before the callback, reading it is safe on the main thread.</li>
	 * <li>Custom executor ({@link Builder#setExecutor(Executor)}): the body is streamed from the network,
	 * the listener MUST close the response, on that executor.</li>
	 * </ul>
	 */
	public void getAsOkHttpResponse(OkHttpResponseListener requestListener) {
		start(ResponseType.OK_HTTP_RESPONSE, null, requestListener);
	}

	public void getAsBitmap(BitmapRequestListener requestListener) {
		start(ResponseType.BITMAP, null, requestListener);
	}

	public <T> void getAsObject(Class<T> objectClass, ParsedRequestListener<T> parsedRequestListener) {
		start(ResponseType.OBJECT, objectClass, parsedRequestListener);
	}

	public <T> void getAsObjectList(Class<T> objectClass, ParsedRequestListener<List<T>> parsedRequestListener) {
		start(ResponseType.OBJECT_LIST, objectClass, parsedRequestListener);
	}

	public void startDownload(DownloadListener downloadListener) {
		start(null, null, downloadListener);
	}

	private void start(@Nullable ResponseType responseType, @Nullable Type itemClass, Object listener) {
		if (!mStarted.compareAndSet(false, true)) throw new IllegalStateException("This request has already been started: " + this);

		mResponseType = responseType;
		mItemClass = itemClass;
		mListener = listener;
		mAsync = true;
		mEngine = KnEngine.get();

		// Cancelled before being started
		if (mCancelled) {
			deliverRejection(Utils.getErrorForCancellation());
			return;
		}

		mEngine.submit(this);
	}

	// endregion

	// region Synchronous API (never on the main thread)

	public KnResponse<JSONObject> executeForJSONObject() {
		return execute(ResponseType.JSON_OBJECT, null);
	}

	public KnResponse<JSONArray> executeForJSONArray() {
		return execute(ResponseType.JSON_ARRAY, null);
	}

	public KnResponse<String> executeForString() {
		return execute(ResponseType.STRING, null);
	}

	/**
	 * On success, the caller MUST close the returned {@link Response}.
	 */
	public KnResponse<Response> executeForOkHttpResponse() {
		return execute(ResponseType.OK_HTTP_RESPONSE, null);
	}

	public KnResponse<Bitmap> executeForBitmap() {
		return execute(ResponseType.BITMAP, null);
	}

	public <T> KnResponse<T> executeForObject(Class<T> objectClass) {
		return execute(ResponseType.OBJECT, objectClass);
	}

	public <T> KnResponse<List<T>> executeForObjectList(Class<T> objectClass) {
		return execute(ResponseType.OBJECT_LIST, objectClass);
	}

	public KnResponse<String> executeForDownload() {
		return execute(null, null);
	}

	@SuppressWarnings("unchecked")
	private <T> KnResponse<T> execute(@Nullable ResponseType responseType, @Nullable Type itemClass) {
		mResponseType = responseType;
		mItemClass = itemClass;
		mEngine = KnEngine.get();

		mRunning = true;
		try {
			return (KnResponse<T>) RequestExecutor.execute(this, mEngine);
		} finally {
			mRunning = false;
			mCall = null;
		}
	}

	// endregion

	public KnRequest setDownloadProgressListener(DownloadProgressListener downloadProgressListener) {
		this.mDownloadProgressListener = downloadProgressListener;
		return this;
	}

	public KnRequest setUploadProgressListener(UploadProgressListener uploadProgressListener) {
		this.mUploadProgressListener = uploadProgressListener;
		return this;
	}

	public Method getMethod() {
		return mMethod;
	}

	public Priority getPriority() {
		return mPriority;
	}

	/**
	 * The final URL (path and query parameters applied), or the raw URL if it is invalid
	 */
	public String getUrl() {
		try {
			return buildHttpUrl().toString();
		} catch (IllegalArgumentException e) {
			return mUrl;
		}
	}

	public int getSequenceNumber() {
		return mSequenceNumber;
	}

	public ResponseType getResponseAs() {
		return mResponseType;
	}

	public Object getTag() {
		return mTag;
	}

	public RequestType getRequestType() {
		return mRequestType;
	}

	public OkHttpClient getOkHttpClient() {
		return mOkHttpClient;
	}

	public String getUserAgent() {
		return mUserAgent;
	}

	public Type getItemClass() {
		return mItemClass;
	}

	public String getDirPath() {
		return mDirPath;
	}

	public String getFileName() {
		return mFileName;
	}

	public CacheControl getCacheControl() {
		return mCacheControl;
	}

	public ImageView.ScaleType getScaleType() {
		return mScaleType;
	}

	/**
	 * @return The per-request connect timeout in ms, -1 if the global one is used
	 */
	public long getConnectTimeoutMs() {
		return mConnectTimeoutMs;
	}

	/**
	 * @return The per-request read timeout in ms, -1 if the global one is used
	 */
	public long getReadTimeoutMs() {
		return mReadTimeoutMs;
	}

	/**
	 * @return The per-request write timeout in ms, -1 if the global one is used
	 */
	public long getWriteTimeoutMs() {
		return mWriteTimeoutMs;
	}

	/**
	 * @return The per-request call timeout in ms, -1 if the global one is used
	 */
	public long getCallTimeoutMs() {
		return mCallTimeoutMs;
	}

	/**
	 * Cancels the request. The listener receives a single onError() with
	 * {@link KnConstants#REQUEST_CANCELLED_ERROR} on its delivery thread (immediately when called from it).
	 * @param forceCancel Cancel even if the percentage threshold for cancelling is reached
	 */
	public void cancel(boolean forceCancel) {
		if (!forceCancel && mPercentageThresholdForCancelling > 0 && mProgress >= mPercentageThresholdForCancelling) return;

		mCancelled = true;
		mRunning = false;

		// A queued request is skipped by the dispatcher, a running one is aborted
		final Call call = mCall;
		if (call != null) call.cancel();

		if (mStarted.get() && mDelivered.compareAndSet(false, true)) {
			final KnError error = Utils.getErrorForCancellation();
			if (mExecutor == null && Looper.myLooper() == Looper.getMainLooper()) {
				dispatchError(error);
				finish();
			} else {
				runOnDeliveryThread(() -> {
					dispatchError(error);
					finish();
				});
			}
		}
	}

	public boolean isCanceled() {
		return mCancelled;
	}

	public boolean isRunning() {
		return mRunning;
	}

	// region Internal API

	@RestrictTo(RestrictTo.Scope.LIBRARY)
	public KnEngine getEngine() {
		return mEngine;
	}

	@RestrictTo(RestrictTo.Scope.LIBRARY)
	public void setSequenceNumber(int sequenceNumber) {
		this.mSequenceNumber = sequenceNumber;
	}

	@RestrictTo(RestrictTo.Scope.LIBRARY)
	public void setRunning(boolean running) {
		mRunning = running;
	}

	@RestrictTo(RestrictTo.Scope.LIBRARY)
	public void setCall(Call call) {
		this.mCall = call;
	}

	/**
	 * Whether the result is delivered on the main thread (asynchronous request without custom executor)
	 */
	@RestrictTo(RestrictTo.Scope.LIBRARY)
	public boolean deliversOnMainThread() {
		return mAsync && mExecutor == null;
	}

	@RestrictTo(RestrictTo.Scope.LIBRARY)
	public boolean hasCustomTimeouts() {
		return mConnectTimeoutMs != NO_TIMEOUT_OVERRIDE || mReadTimeoutMs != NO_TIMEOUT_OVERRIDE || mWriteTimeoutMs != NO_TIMEOUT_OVERRIDE;
	}

	/**
	 * Whether this request needs the client with the per-request interceptors
	 */
	@RestrictTo(RestrictTo.Scope.LIBRARY)
	public boolean needsExtendedClient() {
		return hasCustomTimeouts() || getDownloadProgressReporter() != null;
	}

	@RestrictTo(RestrictTo.Scope.LIBRARY)
	@Nullable
	public ProgressReporter getDownloadProgressReporter() {
		if (mRequestType != RequestType.DOWNLOAD) return null;
		if (mDownloadProgressReporter == null && (mDownloadProgressListener != null || mPercentageThresholdForCancelling > 0)) {
			mDownloadProgressReporter = new ProgressReporter(this, false);
		}
		return mDownloadProgressReporter;
	}

	@RestrictTo(RestrictTo.Scope.LIBRARY)
	@Nullable
	public ProgressReporter getUploadProgressReporter() {
		if (mRequestType != RequestType.MULTIPART) return null;
		if (mUploadProgressReporter == null && (mUploadProgressListener != null || mPercentageThresholdForCancelling > 0)) {
			mUploadProgressReporter = new ProgressReporter(this, true);
		}
		return mUploadProgressReporter;
	}

	/**
	 * Called from the IO thread, used by the percentage threshold for cancelling
	 */
	@RestrictTo(RestrictTo.Scope.LIBRARY)
	public void updateProgress(long currentBytes, long totalBytes) {
		if (totalBytes > 0) mProgress = (int) ((currentBytes * 100) / totalBytes);
	}

	@RestrictTo(RestrictTo.Scope.LIBRARY)
	public boolean hasProgressListener(boolean upload) {
		return upload ? mUploadProgressListener != null : mDownloadProgressListener != null;
	}

	@RestrictTo(RestrictTo.Scope.LIBRARY)
	public void dispatchProgress(boolean upload, long currentBytes, long totalBytes) {
		if (mCancelled) return;

		if (upload) {
			final UploadProgressListener listener = mUploadProgressListener;
			if (listener != null) listener.onProgress(currentBytes, totalBytes);
		} else {
			final DownloadProgressListener listener = mDownloadProgressListener;
			if (listener != null) listener.onProgress(currentBytes, totalBytes);
		}
	}

	@RestrictTo(RestrictTo.Scope.LIBRARY)
	@NonNull
	public HttpUrl buildHttpUrl() {
		String url = mUrl;
		if (url == null) throw new IllegalArgumentException("Invalid URL: null");

		if (mPathParameterMap != null) {
			for (Map.Entry<String, String> entry : mPathParameterMap.entrySet()) {
				url = url.replace("{" + entry.getKey() + "}", entry.getValue());
			}
		}

		final HttpUrl httpUrl = HttpUrl.parse(url);
		if (httpUrl == null) throw new IllegalArgumentException("Invalid URL: " + url);
		if (mQueryParameterMap == null) return httpUrl;

		final HttpUrl.Builder urlBuilder = httpUrl.newBuilder();
		for (Map.Entry<String, List<String>> entry : mQueryParameterMap.entrySet()) {
			for (String value : entry.getValue()) urlBuilder.addQueryParameter(entry.getKey(), value);
		}
		return urlBuilder.build();
	}

	/**
	 * Header precedence: User-Agent header added on the request > setUserAgent() > global User-Agent
	 */
	@RestrictTo(RestrictTo.Scope.LIBRARY)
	public void applyHeaders(@NonNull Request.Builder builder, @Nullable String globalUserAgent) {
		boolean hasUserAgentHeader = false;

		if (mHeadersMap != null) {
			for (Map.Entry<String, List<String>> entry : mHeadersMap.entrySet()) {
				final String name = entry.getKey();
				if (!hasUserAgentHeader && KnConstants.USER_AGENT.equalsIgnoreCase(name)) hasUserAgentHeader = true;
				for (String value : entry.getValue()) builder.addHeader(name, value);
			}
		}

		if (hasUserAgentHeader) return;

		final String userAgent = mUserAgent != null ? mUserAgent : globalUserAgent;
		if (userAgent != null) builder.header(KnConstants.USER_AGENT, userAgent);
	}

	@RestrictTo(RestrictTo.Scope.LIBRARY)
	@NonNull
	public RequestBody createRequestBody() {
		if (mJsonBody != null) {
			if (mCustomMediaType == null) return RequestBody.create(mJsonBody, MEDIA_TYPE_JSON);

			// Same Content-Type as a String body: charset=utf-8 is added when missing
			final Charset charset = mCustomMediaType.charset(null);
			if (charset == null) return RequestBody.create(mJsonBody, MediaType.parse(mCustomMediaType + "; charset=utf-8"));
			if (StandardCharsets.UTF_8.equals(charset)) return RequestBody.create(mJsonBody, mCustomMediaType);
			return RequestBody.create(new String(mJsonBody, StandardCharsets.UTF_8), mCustomMediaType);
		} else if (mStringBody != null) {
			return RequestBody.create(mStringBody, mCustomMediaType != null ? mCustomMediaType : MEDIA_TYPE_TEXT);
		} else if (mFileBody != null) {
			return RequestBody.create(mFileBody, mCustomMediaType != null ? mCustomMediaType : MEDIA_TYPE_OCTET_STREAM);
		} else if (mByteBody != null) {
			return RequestBody.create(mByteBody, mCustomMediaType != null ? mCustomMediaType : MEDIA_TYPE_OCTET_STREAM);
		}

		final FormBody.Builder builder = new FormBody.Builder();
		if (mBodyParameterMap != null) {
			for (Map.Entry<String, String> entry : mBodyParameterMap.entrySet()) builder.add(entry.getKey(), entry.getValue());
		}
		if (mUrlEncodedFormBodyParameterMap != null) {
			for (Map.Entry<String, String> entry : mUrlEncodedFormBodyParameterMap.entrySet()) builder.addEncoded(entry.getKey(), entry.getValue());
		}
		return builder.build();
	}

	@RestrictTo(RestrictTo.Scope.LIBRARY)
	@NonNull
	public RequestBody createMultipartRequestBody() {
		final MultipartBody.Builder builder = new MultipartBody.Builder().setType(mCustomMediaType == null ? MultipartBody.FORM : mCustomMediaType);

		if (mMultiPartParameterMap != null) {
			for (Map.Entry<String, MultipartStringBody> entry : mMultiPartParameterMap.entrySet()) {
				final MultipartStringBody stringBody = entry.getValue();
				final MediaType mediaType = stringBody.contentType != null ? MediaType.parse(stringBody.contentType) : null;
				builder.addPart(MultipartBody.Part.createFormData(entry.getKey(), null, RequestBody.create(stringBody.value, mediaType)));
			}
		}

		if (mMultiPartFileMap != null) {
			for (Map.Entry<String, List<MultipartFileBody>> entry : mMultiPartFileMap.entrySet()) {
				for (MultipartFileBody fileBody : entry.getValue()) {
					final String fileName = fileBody.file.getName();
					final MediaType mediaType = MediaType.parse(fileBody.contentType != null ? fileBody.contentType : Utils.getMimeType(fileName));
					builder.addPart(MultipartBody.Part.createFormData(entry.getKey(), fileName, RequestBody.create(fileBody.file, mediaType)));
				}
			}
		}

		return builder.build();
	}

	/**
	 * @throws IOException The body couldn't be read (connection lost, timeout...): not a parse error
	 */
	@RestrictTo(RestrictTo.Scope.LIBRARY)
	@NonNull
	public KnResponse<?> parseResponse(@NonNull Response response) throws IOException {
		try {
			final ResponseBody body = response.body();

			switch (mResponseType) {
				case STRING: {
					return KnResponse.success(body.string());
				}
				case JSON_ARRAY: {
					final JSONArray array = JSON.parseArray(body.bytes());
					return array != null ? KnResponse.success(array) : KnResponse.failed(Utils.getErrorForParse(null));
				}
				case JSON_OBJECT: {
					final JSONObject object = JSON.parseObject(body.bytes());
					return object != null ? KnResponse.success(object) : KnResponse.failed(Utils.getErrorForParse(null));
				}
				case OBJECT: {
					final Object object = JSON.parseObject(body.bytes(), mItemClass);
					return object != null ? KnResponse.success(object) : KnResponse.failed(Utils.getErrorForParse(null));
				}
				case OBJECT_LIST: {
					final List<Object> list = JSON.parseArray(body.bytes(), mItemClass);
					return list != null ? KnResponse.success(list) : KnResponse.failed(Utils.getErrorForParse(null));
				}
				case BITMAP: {
					final Bitmap bitmap = Utils.decodeBitmap(body.bytes(), mMaxWidth, mMaxHeight, mDecodeConfig, mBitmapOptions, mScaleType);
					return bitmap != null ? KnResponse.success(bitmap) : KnResponse.failed(Utils.getErrorForParse(null));
				}
				default: {
					return KnResponse.failed(Utils.getErrorForParse(null));
				}
			}
		} catch (IOException e) {
			throw e;
		} catch (Throwable t) {
			return KnResponse.failed(Utils.getErrorForParse(t));
		}
	}

	/**
	 * Delivers the result of an execution, exactly once, on the delivery thread
	 */
	@RestrictTo(RestrictTo.Scope.LIBRARY)
	public void deliver(@NonNull KnResponse<?> response) {
		if (!mDelivered.compareAndSet(false, true)) {
			// Already delivered (cancelled): release what the listener will never receive
			closeOwnedResponse(response);
			return;
		}

		if (mCancelled) {
			closeOwnedResponse(response);
			runOnDeliveryThread(() -> {
				dispatchError(Utils.getErrorForCancellation());
				finish();
			});
			return;
		}

		runOnDeliveryThread(() -> {
			try {
				if (response.isSuccess()) dispatchSuccess(response);
				else dispatchError(response.getError());
			} finally {
				finish();
			}
		});
	}

	/**
	 * Delivers an error for a request which could not be executed
	 */
	@RestrictTo(RestrictTo.Scope.LIBRARY)
	public void deliverRejection(@NonNull KnError error) {
		mCancelled = true;
		if (!mDelivered.compareAndSet(false, true)) return;

		runOnDeliveryThread(() -> {
			dispatchError(error);
			finish();
		});
	}

	@RestrictTo(RestrictTo.Scope.LIBRARY)
	public void runOnDeliveryThread(@NonNull Runnable runnable) {
		final Executor executor = mExecutor != null ? mExecutor : (mEngine != null ? mEngine : KnEngine.get()).getMainThreadExecutor();

		try {
			executor.execute(runnable);
		} catch (RejectedExecutionException e) {
			// The custom executor has been shut down: nobody can receive the result anymore
			finish();
		}
	}

	// endregion

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void dispatchSuccess(@NonNull KnResponse<?> response) {
		final Object result = response.getResult();
		final Response okHttpResponse = response.getOkHttpResponse();
		final Object listener = mListener;

		if (mRequestType == RequestType.DOWNLOAD) {
			if (listener instanceof DownloadListener) ((DownloadListener) listener).onDownloadComplete(okHttpResponse);
			return;
		}

		switch (mResponseType) {
			case STRING:
				if (listener instanceof StringRequestListener) ((StringRequestListener) listener).onResponse((String) result, okHttpResponse);
				break;
			case JSON_OBJECT:
				if (listener instanceof JSONObjectRequestListener) ((JSONObjectRequestListener) listener).onResponse((JSONObject) result, okHttpResponse);
				break;
			case JSON_ARRAY:
				if (listener instanceof JSONArrayRequestListener) ((JSONArrayRequestListener) listener).onResponse((JSONArray) result, okHttpResponse);
				break;
			case BITMAP:
				if (listener instanceof BitmapRequestListener) ((BitmapRequestListener) listener).onResponse((Bitmap) result, okHttpResponse);
				break;
			case OBJECT:
			case OBJECT_LIST:
				if (listener instanceof ParsedRequestListener) ((ParsedRequestListener) listener).onResponse(result, okHttpResponse);
				break;
			case OK_HTTP_RESPONSE:
				if (listener instanceof OkHttpResponseListener) ((OkHttpResponseListener) listener).onResponse((Response) result);
				else Utils.closeQuietly((Response) result);
				break;
		}
	}

	private void dispatchError(@NonNull KnError error) {
		final Object listener = mListener;

		if (listener instanceof StringRequestListener) ((StringRequestListener) listener).onError(error);
		else if (listener instanceof JSONObjectRequestListener) ((JSONObjectRequestListener) listener).onError(error);
		else if (listener instanceof JSONArrayRequestListener) ((JSONArrayRequestListener) listener).onError(error);
		else if (listener instanceof BitmapRequestListener) ((BitmapRequestListener) listener).onError(error);
		else if (listener instanceof ParsedRequestListener) ((ParsedRequestListener<?>) listener).onError(error);
		else if (listener instanceof OkHttpResponseListener) ((OkHttpResponseListener) listener).onError(error);
		else if (listener instanceof DownloadListener) ((DownloadListener) listener).onError(error);
	}

	private void closeOwnedResponse(@NonNull KnResponse<?> response) {
		if (response.isSuccess() && response.getResult() instanceof Response) Utils.closeQuietly((Response) response.getResult());
	}

	private void finish() {
		mListener = null;
		mDownloadProgressListener = null;
		mUploadProgressListener = null;
		mCall = null;
		if (mEngine != null) mEngine.finish(this);
	}

	@NonNull
	@Override
	public String toString() {
		return "KnRequest{" +
				"sequenceNumber=" + mSequenceNumber +
				", mMethod=" + mMethod +
				", mPriority=" + mPriority +
				", mRequestType=" + mRequestType +
				", mUrl=" + mUrl +
				'}';
	}

	// region Builders

	/**
	 * Common builder of all the request types
	 * @param <B> The concrete builder type, returned by all the setters
	 */
	@SuppressWarnings("unchecked")
	public abstract static class Builder<B extends Builder<B>> implements RequestBuilder {
		final String mUrl;
		final Method mMethod;
		Priority mPriority = Priority.MEDIUM;
		Object mTag;
		Map<String, List<String>> mHeadersMap;
		Map<String, List<String>> mQueryParameterMap;
		Map<String, String> mPathParameterMap;
		CacheControl mCacheControl;
		Executor mExecutor;
		OkHttpClient mOkHttpClient;
		String mUserAgent;
		long mConnectTimeoutMs = NO_TIMEOUT_OVERRIDE;
		long mReadTimeoutMs = NO_TIMEOUT_OVERRIDE;
		long mWriteTimeoutMs = NO_TIMEOUT_OVERRIDE;
		long mCallTimeoutMs = NO_TIMEOUT_OVERRIDE;

		Builder(String url, Method method) {
			this.mUrl = url;
			this.mMethod = method;
		}

		final B self() {
			return (B) this;
		}

		@Override
		public B setPriority(Priority priority) {
			mPriority = priority;
			return self();
		}

		@Override
		public B setTag(Object tag) {
			mTag = tag;
			return self();
		}

		@Override
		public B addQueryParameter(String key, String value) {
			if (mQueryParameterMap == null) mQueryParameterMap = new LinkedHashMap<>();
			addToMultiMap(mQueryParameterMap, key, value);
			return self();
		}

		@Override
		public B addQueryParameter(Map<String, String> queryParameterMap) {
			if (queryParameterMap != null) {
				for (Map.Entry<String, String> entry : queryParameterMap.entrySet()) addQueryParameter(entry.getKey(), entry.getValue());
			}
			return self();
		}

		/**
		 * The value is percent-encoded, it can contain any character (/, ?, #, spaces...)
		 */
		@Override
		public B addPathParameter(String key, String value) {
			if (mPathParameterMap == null) mPathParameterMap = new LinkedHashMap<>();
			mPathParameterMap.put(key, Utils.encodePathSegment(value));
			return self();
		}

		@Override
		public B addPathParameter(Map<String, String> pathParameterMap) {
			if (pathParameterMap != null) {
				for (Map.Entry<String, String> entry : pathParameterMap.entrySet()) addPathParameter(entry.getKey(), entry.getValue());
			}
			return self();
		}

		/**
		 * The value is inserted as is in the URL (already encoded, or several path segments on purpose)
		 */
		@Override
		public B addEncodedPathParameter(String key, String value) {
			if (mPathParameterMap == null) mPathParameterMap = new LinkedHashMap<>();
			mPathParameterMap.put(key, String.valueOf(value));
			return self();
		}

		@Override
		public B addHeaders(String key, String value) {
			if (mHeadersMap == null) mHeadersMap = new LinkedHashMap<>();
			addToMultiMap(mHeadersMap, key, value);
			return self();
		}

		@Override
		public B addHeaders(Map<String, String> headerMap) {
			if (headerMap != null) {
				for (Map.Entry<String, String> entry : headerMap.entrySet()) addHeaders(entry.getKey(), entry.getValue());
			}
			return self();
		}

		@Override
		public B doNotCacheResponse() {
			mCacheControl = new CacheControl.Builder().noStore().build();
			return self();
		}

		@Override
		public B getResponseOnlyIfCached() {
			mCacheControl = CacheControl.FORCE_CACHE;
			return self();
		}

		@Override
		public B getResponseOnlyFromNetwork() {
			mCacheControl = CacheControl.FORCE_NETWORK;
			return self();
		}

		@Override
		public B setMaxAgeCacheControl(int maxAge, TimeUnit timeUnit) {
			mCacheControl = new CacheControl.Builder().maxAge(maxAge, timeUnit).build();
			return self();
		}

		@Override
		public B setMaxStaleCacheControl(int maxStale, TimeUnit timeUnit) {
			mCacheControl = new CacheControl.Builder().maxStale(maxStale, timeUnit).build();
			return self();
		}

		@Override
		public B setExecutor(Executor executor) {
			mExecutor = executor;
			return self();
		}

		@Override
		public B setOkHttpClient(OkHttpClient okHttpClient) {
			mOkHttpClient = okHttpClient;
			return self();
		}

		@Override
		public B setUserAgent(String userAgent) {
			mUserAgent = userAgent;
			return self();
		}

		/**
		 * Overrides the global connect timeout for this request (0 = no timeout).
		 * No effect when an already established connection is reused.
		 */
		@Override
		public B setConnectTimeout(long timeout, TimeUnit timeUnit) {
			mConnectTimeoutMs = toTimeoutMs(timeout, timeUnit, true);
			return self();
		}

		/**
		 * Overrides the global read timeout for this request (0 = no timeout)
		 */
		@Override
		public B setReadTimeout(long timeout, TimeUnit timeUnit) {
			mReadTimeoutMs = toTimeoutMs(timeout, timeUnit, true);
			return self();
		}

		/**
		 * Overrides the global write timeout for this request (0 = no timeout)
		 */
		@Override
		public B setWriteTimeout(long timeout, TimeUnit timeUnit) {
			mWriteTimeoutMs = toTimeoutMs(timeout, timeUnit, true);
			return self();
		}

		/**
		 * Overrides the global call timeout (whole call: DNS, connect, write, read, redirects) for this request (0 = no timeout)
		 */
		@Override
		public B setCallTimeout(long timeout, TimeUnit timeUnit) {
			mCallTimeoutMs = toTimeoutMs(timeout, timeUnit, false);
			return self();
		}

		private static long toTimeoutMs(long timeout, TimeUnit timeUnit, boolean intRange) {
			if (timeout < 0) throw new IllegalArgumentException("timeout < 0");
			if (timeUnit == null) throw new IllegalArgumentException("timeUnit == null");

			final long ms = timeUnit.toMillis(timeout);
			if (intRange && ms > Integer.MAX_VALUE) throw new IllegalArgumentException("Timeout too large");
			if (ms == 0 && timeout > 0) throw new IllegalArgumentException("Timeout too small");
			return ms;
		}

		private static void addToMultiMap(Map<String, List<String>> map, String key, String value) {
			List<String> list = map.get(key);

			if (list == null) {
				list = new ArrayList<>(1);
				map.put(key, list);
			}

			if (!list.contains(value)) list.add(value);
		}

		@Override
		public abstract KnRequest build();
	}

	public static class GetRequestBuilder extends Builder<GetRequestBuilder> {
		private Bitmap.Config mDecodeConfig;
		private BitmapFactory.Options mBitmapOptions;
		private int mMaxWidth;
		private int mMaxHeight;
		private ImageView.ScaleType mScaleType;

		public GetRequestBuilder(String url) {
			super(url, Method.GET);
		}

		public GetRequestBuilder(String url, Method method) {
			super(url, method);
		}

		public GetRequestBuilder setBitmapConfig(Bitmap.Config bitmapConfig) {
			mDecodeConfig = bitmapConfig;
			return this;
		}

		public GetRequestBuilder setBitmapOptions(BitmapFactory.Options bitmapOptions) {
			mBitmapOptions = bitmapOptions;
			return this;
		}

		public GetRequestBuilder setBitmapMaxHeight(int maxHeight) {
			mMaxHeight = maxHeight;
			return this;
		}

		public GetRequestBuilder setBitmapMaxWidth(int maxWidth) {
			mMaxWidth = maxWidth;
			return this;
		}

		public GetRequestBuilder setImageScaleType(ImageView.ScaleType imageScaleType) {
			mScaleType = imageScaleType;
			return this;
		}

		@Override
		public KnRequest build() {
			return new KnRequest(this, RequestType.SIMPLE);
		}
	}

	public static class HeadRequestBuilder extends GetRequestBuilder {

		public HeadRequestBuilder(String url) {
			super(url, Method.HEAD);
		}
	}

	public static class OptionsRequestBuilder extends GetRequestBuilder {

		public OptionsRequestBuilder(String url) {
			super(url, Method.OPTIONS);
		}
	}

	public static class PostRequestBuilder extends Builder<PostRequestBuilder> {
		private byte[] mJsonBody;
		private String mStringBody;
		private byte[] mByteBody;
		private File mFileBody;
		private Map<String, String> mBodyParameterMap;
		private Map<String, String> mUrlEncodedFormBodyParameterMap;
		private String mCustomContentType;

		public PostRequestBuilder(String url) {
			super(url, Method.POST);
		}

		public PostRequestBuilder(String url, Method method) {
			super(url, method);
		}

		public PostRequestBuilder addBodyParameter(String key, String value) {
			if (mBodyParameterMap == null) mBodyParameterMap = new LinkedHashMap<>();
			mBodyParameterMap.put(key, value);
			return this;
		}

		public PostRequestBuilder addBodyParameter(Map<String, String> bodyParameterMap) {
			if (bodyParameterMap != null) {
				if (mBodyParameterMap == null) mBodyParameterMap = new LinkedHashMap<>();
				mBodyParameterMap.putAll(bodyParameterMap);
			}
			return this;
		}

		public PostRequestBuilder addUrlEncodeFormBodyParameter(String key, String value) {
			if (mUrlEncodedFormBodyParameterMap == null) mUrlEncodedFormBodyParameterMap = new LinkedHashMap<>();
			mUrlEncodedFormBodyParameterMap.put(key, value);
			return this;
		}

		public PostRequestBuilder addUrlEncodeFormBodyParameter(Map<String, String> bodyParameterMap) {
			if (bodyParameterMap != null) {
				if (mUrlEncodedFormBodyParameterMap == null) mUrlEncodedFormBodyParameterMap = new LinkedHashMap<>();
				mUrlEncodedFormBodyParameterMap.putAll(bodyParameterMap);
			}
			return this;
		}

		/**
		 * Serialized immediately (snapshot of the object), directly as UTF-8 bytes
		 */
		public PostRequestBuilder addJSONObjectBody(JSONObject jsonObject) {
			if (jsonObject != null) mJsonBody = JSON.toJSONBytes(jsonObject);
			return this;
		}

		/**
		 * Serialized immediately (snapshot of the array), directly as UTF-8 bytes
		 */
		public PostRequestBuilder addJSONArrayBody(JSONArray jsonArray) {
			if (jsonArray != null) mJsonBody = JSON.toJSONBytes(jsonArray);
			return this;
		}

		/**
		 * Serializes any object (POJO, Map, List...) as the JSON body, immediately and directly as UTF-8 bytes
		 */
		public PostRequestBuilder addObjectBody(Object object) {
			if (object != null) mJsonBody = JSON.toJSONBytes(object);
			return this;
		}

		public PostRequestBuilder addStringBody(String stringBody) {
			mStringBody = stringBody;
			return this;
		}

		public PostRequestBuilder addFileBody(File file) {
			mFileBody = file;
			return this;
		}

		public PostRequestBuilder addByteBody(byte[] bytes) {
			mByteBody = bytes;
			return this;
		}

		public PostRequestBuilder setContentType(String contentType) {
			mCustomContentType = contentType;
			return this;
		}

		@Override
		public KnRequest build() {
			return new KnRequest(this, RequestType.SIMPLE);
		}
	}

	public static class PutRequestBuilder extends PostRequestBuilder {

		public PutRequestBuilder(String url) {
			super(url, Method.PUT);
		}
	}

	public static class DeleteRequestBuilder extends PostRequestBuilder {

		public DeleteRequestBuilder(String url) {
			super(url, Method.DELETE);
		}
	}

	public static class PatchRequestBuilder extends PostRequestBuilder {

		public PatchRequestBuilder(String url) {
			super(url, Method.PATCH);
		}
	}

	public static class DynamicRequestBuilder extends PostRequestBuilder {

		public DynamicRequestBuilder(String url, Method method) {
			super(url, method);
		}
	}

	public static class DownloadBuilder extends Builder<DownloadBuilder> {
		private final String mDirPath;
		private final String mFileName;
		private int mPercentageThresholdForCancelling = 0;

		public DownloadBuilder(String url, String dirPath, String fileName) {
			super(url, Method.GET);
			this.mDirPath = dirPath;
			this.mFileName = fileName;
		}

		public DownloadBuilder setPercentageThresholdForCancelling(int percentageThresholdForCancelling) {
			mPercentageThresholdForCancelling = percentageThresholdForCancelling;
			return this;
		}

		@Override
		public KnRequest build() {
			return new KnRequest(this, RequestType.DOWNLOAD);
		}
	}

	public static class MultiPartBuilder extends Builder<MultiPartBuilder> {
		private Map<String, MultipartStringBody> mMultiPartParameterMap;
		private Map<String, List<MultipartFileBody>> mMultiPartFileMap;
		private int mPercentageThresholdForCancelling = 0;
		private String mCustomContentType;

		public MultiPartBuilder(String url) {
			super(url, Method.POST);
		}

		public MultiPartBuilder addMultipartParameter(String key, String value) {
			return addMultipartParameter(key, value, null);
		}

		public MultiPartBuilder addMultipartParameter(String key, String value, String contentType) {
			if (mMultiPartParameterMap == null) mMultiPartParameterMap = new LinkedHashMap<>();
			mMultiPartParameterMap.put(key, new MultipartStringBody(value, contentType));
			return this;
		}

		public MultiPartBuilder addMultipartParameter(Map<String, String> multiPartParameterMap) {
			return addMultipartParameter(multiPartParameterMap, null);
		}

		public MultiPartBuilder addMultipartParameter(Map<String, String> multiPartParameterMap, String contentType) {
			if (multiPartParameterMap != null) {
				for (Map.Entry<String, String> entry : multiPartParameterMap.entrySet()) addMultipartParameter(entry.getKey(), entry.getValue(), contentType);
			}
			return this;
		}

		public MultiPartBuilder addMultipartFile(String key, File file) {
			return addMultipartFile(key, file, null);
		}

		public MultiPartBuilder addMultipartFile(String key, File file, String contentType) {
			addMultipartFileWithKey(key, new MultipartFileBody(file, contentType));
			return this;
		}

		public MultiPartBuilder addMultipartFile(Map<String, File> multiPartFileMap) {
			return addMultipartFile(multiPartFileMap, null);
		}

		public MultiPartBuilder addMultipartFile(Map<String, File> multiPartFileMap, String contentType) {
			if (multiPartFileMap != null) {
				for (Map.Entry<String, File> entry : multiPartFileMap.entrySet()) addMultipartFileWithKey(entry.getKey(), new MultipartFileBody(entry.getValue(), contentType));
			}
			return this;
		}

		public MultiPartBuilder addMultipartFileList(String key, List<File> files) {
			return addMultipartFileList(key, files, null);
		}

		public MultiPartBuilder addMultipartFileList(String key, List<File> files, String contentType) {
			if (files != null) {
				for (File file : files) addMultipartFileWithKey(key, new MultipartFileBody(file, contentType));
			}
			return this;
		}

		public MultiPartBuilder addMultipartFileList(Map<String, List<File>> multiPartFileMap) {
			return addMultipartFileList(multiPartFileMap, null);
		}

		public MultiPartBuilder addMultipartFileList(Map<String, List<File>> multiPartFileMap, String contentType) {
			if (multiPartFileMap != null) {
				if (mMultiPartFileMap == null) mMultiPartFileMap = new LinkedHashMap<>();

				for (Map.Entry<String, List<File>> entry : multiPartFileMap.entrySet()) {
					final List<MultipartFileBody> fileBodies = new ArrayList<>();
					for (File file : entry.getValue()) fileBodies.add(new MultipartFileBody(file, contentType));
					mMultiPartFileMap.put(entry.getKey(), fileBodies);
				}
			}
			return this;
		}

		public MultiPartBuilder setPercentageThresholdForCancelling(int percentageThresholdForCancelling) {
			this.mPercentageThresholdForCancelling = percentageThresholdForCancelling;
			return this;
		}

		public MultiPartBuilder setContentType(String contentType) {
			mCustomContentType = contentType;
			return this;
		}

		private void addMultipartFileWithKey(String key, MultipartFileBody fileBody) {
			if (mMultiPartFileMap == null) mMultiPartFileMap = new LinkedHashMap<>();

			List<MultipartFileBody> fileBodies = mMultiPartFileMap.get(key);
			if (fileBodies == null) {
				fileBodies = new ArrayList<>();
				mMultiPartFileMap.put(key, fileBodies);
			}
			fileBodies.add(fileBody);
		}

		@Override
		public KnRequest build() {
			return new KnRequest(this, RequestType.MULTIPART);
		}
	}

	// endregion
}

package ovh.karewan.knhttp.common;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import okhttp3.ResponseBody;
import ovh.karewan.knhttp.core.Core;
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
import ovh.karewan.knhttp.internal.KnRequestQueue;
import ovh.karewan.knhttp.internal.SynchronousCall;
import ovh.karewan.knhttp.model.MultipartFileBody;
import ovh.karewan.knhttp.model.MultipartStringBody;
import ovh.karewan.knhttp.utils.Utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Okio;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public class KnRequest<T extends KnRequest> {

	private final static String TAG = KnRequest.class.getSimpleName();

	private final int mMethod;
	private final Priority mPriority;
	private final int mRequestType;
	private final String mUrl;
	private int sequenceNumber;
	private final Object mTag;
	private ResponseType mResponseType;
	private final HashMap<String, List<String>> mHeadersMap;
	private HashMap<String, String> mBodyParameterMap = new HashMap<>();
	private HashMap<String, String> mUrlEncodedFormBodyParameterMap = new HashMap<>();
	private HashMap<String, MultipartStringBody> mMultiPartParameterMap = new HashMap<>();
	private final HashMap<String, List<String>> mQueryParameterMap;
	private final HashMap<String, String> mPathParameterMap;
	private HashMap<String, List<MultipartFileBody>> mMultiPartFileMap = new HashMap<>();
	private String mDirPath;
	private String mFileName;
	private String mApplicationJsonString = null;
	private String mStringBody = null;
	private byte[] mByte = null;
	private File mFile = null;
	private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
	private static final MediaType MEDIA_TYPE_TEXT = MediaType.parse("text/plain; charset=utf-8");
	private static final MediaType MEDIA_TYPE_OCTET_STREAM = MediaType.parse("application/octet-stream");
	private MediaType customMediaType = null;
	private static final Object sDecodeLock = new Object();

	private Future future;
	private Call call;
	private int mProgress;
	private boolean isCancelled;
	private boolean isDelivered;
	private boolean isRunning;
	private int mPercentageThresholdForCancelling = 0;
	private JSONArrayRequestListener mJSONArrayRequestListener;
	private JSONObjectRequestListener mJSONObjectRequestListener;
	private StringRequestListener mStringRequestListener;
	private OkHttpResponseListener mOkHttpResponseListener;
	private BitmapRequestListener mBitmapRequestListener;
	private ParsedRequestListener mParsedRequestListener;
	private DownloadProgressListener mDownloadProgressListener;
	private UploadProgressListener mUploadProgressListener;
	private DownloadListener mDownloadListener;

	private BitmapFactory.Options mBitmapOptions;
	private Bitmap.Config mDecodeConfig;
	private int mMaxWidth;
	private int mMaxHeight;
	private ImageView.ScaleType mScaleType;
	private final CacheControl mCacheControl;
	private final Executor mExecutor;
	private final OkHttpClient mOkHttpClient;
	private String mUserAgent;
	private Class mItemClass = null;

	public KnRequest(GetRequestBuilder builder) {
		this.mRequestType = RequestType.SIMPLE;
		this.mMethod = builder.mMethod;
		this.mPriority = builder.mPriority;
		this.mUrl = builder.mUrl;
		this.mTag = builder.mTag;
		this.mHeadersMap = builder.mHeadersMap;
		this.mBitmapOptions = builder.mBitmapOptions;
		this.mDecodeConfig = builder.mDecodeConfig;
		this.mMaxHeight = builder.mMaxHeight;
		this.mMaxWidth = builder.mMaxWidth;
		this.mScaleType = builder.mScaleType;
		this.mQueryParameterMap = builder.mQueryParameterMap;
		this.mPathParameterMap = builder.mPathParameterMap;
		this.mCacheControl = builder.mCacheControl;
		this.mExecutor = builder.mExecutor;
		this.mOkHttpClient = builder.mOkHttpClient;
		this.mUserAgent = builder.mUserAgent;
	}

	public KnRequest(PostRequestBuilder builder) {
		this.mRequestType = RequestType.SIMPLE;
		this.mMethod = builder.mMethod;
		this.mPriority = builder.mPriority;
		this.mUrl = builder.mUrl;
		this.mTag = builder.mTag;
		this.mHeadersMap = builder.mHeadersMap;
		this.mBodyParameterMap = builder.mBodyParameterMap;
		this.mUrlEncodedFormBodyParameterMap = builder.mUrlEncodedFormBodyParameterMap;
		this.mQueryParameterMap = builder.mQueryParameterMap;
		this.mPathParameterMap = builder.mPathParameterMap;
		this.mApplicationJsonString = builder.mApplicationJsonString;
		this.mStringBody = builder.mStringBody;
		this.mFile = builder.mFile;
		this.mByte = builder.mByte;
		this.mCacheControl = builder.mCacheControl;
		this.mExecutor = builder.mExecutor;
		this.mOkHttpClient = builder.mOkHttpClient;
		this.mUserAgent = builder.mUserAgent;
		if (builder.mCustomContentType != null) {
			this.customMediaType = MediaType.parse(builder.mCustomContentType);
		}
	}

	public KnRequest(DownloadBuilder builder) {
		this.mRequestType = RequestType.DOWNLOAD;
		this.mMethod = Method.GET;
		this.mPriority = builder.mPriority;
		this.mUrl = builder.mUrl;
		this.mTag = builder.mTag;
		this.mDirPath = builder.mDirPath;
		this.mFileName = builder.mFileName;
		this.mHeadersMap = builder.mHeadersMap;
		this.mQueryParameterMap = builder.mQueryParameterMap;
		this.mPathParameterMap = builder.mPathParameterMap;
		this.mCacheControl = builder.mCacheControl;
		this.mPercentageThresholdForCancelling = builder.mPercentageThresholdForCancelling;
		this.mExecutor = builder.mExecutor;
		this.mOkHttpClient = builder.mOkHttpClient;
		this.mUserAgent = builder.mUserAgent;
	}

	public KnRequest(MultiPartBuilder builder) {
		this.mRequestType = RequestType.MULTIPART;
		this.mMethod = Method.POST;
		this.mPriority = builder.mPriority;
		this.mUrl = builder.mUrl;
		this.mTag = builder.mTag;
		this.mHeadersMap = builder.mHeadersMap;
		this.mQueryParameterMap = builder.mQueryParameterMap;
		this.mPathParameterMap = builder.mPathParameterMap;
		this.mMultiPartParameterMap = builder.mMultiPartParameterMap;
		this.mMultiPartFileMap = builder.mMultiPartFileMap;
		this.mCacheControl = builder.mCacheControl;
		this.mPercentageThresholdForCancelling = builder.mPercentageThresholdForCancelling;
		this.mExecutor = builder.mExecutor;
		this.mOkHttpClient = builder.mOkHttpClient;
		this.mUserAgent = builder.mUserAgent;
		if (builder.mCustomContentType != null) {
			this.customMediaType = MediaType.parse(builder.mCustomContentType);
		}
	}

	public void getAsJSONObject(JSONObjectRequestListener requestListener) {
		this.mResponseType = ResponseType.JSON_OBJECT;
		this.mJSONObjectRequestListener = requestListener;
		KnRequestQueue.gi().addRequest(this);
	}

	public void getAsJSONArray(JSONArrayRequestListener requestListener) {
		this.mResponseType = ResponseType.JSON_ARRAY;
		this.mJSONArrayRequestListener = requestListener;
		KnRequestQueue.gi().addRequest(this);
	}

	public void getAsString(StringRequestListener requestListener) {
		this.mResponseType = ResponseType.STRING;
		this.mStringRequestListener = requestListener;
		KnRequestQueue.gi().addRequest(this);
	}

	public void getAsOkHttpResponse(OkHttpResponseListener requestListener) {
		this.mResponseType = ResponseType.OK_HTTP_RESPONSE;
		this.mOkHttpResponseListener = requestListener;
		KnRequestQueue.gi().addRequest(this);
	}

	public void getAsBitmap(BitmapRequestListener requestListener) {
		this.mResponseType = ResponseType.BITMAP;
		this.mBitmapRequestListener = requestListener;
		KnRequestQueue.gi().addRequest(this);
	}

	public void getAsObject(Class objectClass, ParsedRequestListener parsedRequestListener) {
		this.mItemClass = objectClass;
		this.mResponseType = ResponseType.OBJECT;
		this.mParsedRequestListener = parsedRequestListener;
		KnRequestQueue.gi().addRequest(this);
	}

	public void getAsObjectList(Class objectClass, ParsedRequestListener parsedRequestListener) {
		this.mItemClass = objectClass;
		this.mResponseType = ResponseType.OBJECT_LIST;
		this.mParsedRequestListener = parsedRequestListener;
		KnRequestQueue.gi().addRequest(this);
	}

	public void startDownload(DownloadListener downloadListener) {
		this.mDownloadListener = downloadListener;
		KnRequestQueue.gi().addRequest(this);
	}

	public KnResponse executeForJSONObject() {
		this.mResponseType = ResponseType.JSON_OBJECT;
		return SynchronousCall.execute(this);
	}

	public KnResponse executeForJSONArray() {
		this.mResponseType = ResponseType.JSON_ARRAY;
		return SynchronousCall.execute(this);
	}

	public KnResponse executeForString() {
		this.mResponseType = ResponseType.STRING;
		return SynchronousCall.execute(this);
	}

	public KnResponse executeForOkHttpResponse() {
		this.mResponseType = ResponseType.OK_HTTP_RESPONSE;
		return SynchronousCall.execute(this);
	}

	public KnResponse executeForBitmap() {
		this.mResponseType = ResponseType.BITMAP;
		return SynchronousCall.execute(this);
	}

	public KnResponse executeForObject(Class objectClass) {
		this.mItemClass = objectClass;
		this.mResponseType = ResponseType.OBJECT;
		return SynchronousCall.execute(this);
	}

	public KnResponse executeForObjectList(Class objectClass) {
		this.mItemClass = objectClass;
		this.mResponseType = ResponseType.OBJECT_LIST;
		return SynchronousCall.execute(this);
	}

	public KnResponse executeForDownload() {
		return SynchronousCall.execute(this);
	}

	public T setDownloadProgressListener(DownloadProgressListener downloadProgressListener) {
		this.mDownloadProgressListener = downloadProgressListener;
		return (T) this;
	}

	public T setUploadProgressListener(UploadProgressListener uploadProgressListener) {
		this.mUploadProgressListener = uploadProgressListener;
		return (T) this;
	}

	public int getMethod() {
		return mMethod;
	}

	public Priority getPriority() {
		return mPriority;
	}

	public String getUrl() {
		String tempUrl = mUrl;
		for (HashMap.Entry<String, String> entry : mPathParameterMap.entrySet()) {
			tempUrl = tempUrl.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
		}

		//noinspection ConstantConditions
		HttpUrl.Builder urlBuilder = HttpUrl.parse(tempUrl).newBuilder();
		if (mQueryParameterMap != null) {
			Set<Map.Entry<String, List<String>>> entries = mQueryParameterMap.entrySet();
			for (Map.Entry<String, List<String>> entry : entries) {
				String name = entry.getKey();
				List<String> list = entry.getValue();
				if (list != null) {
					for (String value : list) {
						urlBuilder.addQueryParameter(name, value);
					}
				}
			}
		}
		return urlBuilder.build().toString();
	}

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	public void setProgress(int progress) {
		this.mProgress = progress;
	}

	public void setResponseAs(ResponseType responseType) {
		this.mResponseType = responseType;
	}

	public ResponseType getResponseAs() {
		return mResponseType;
	}

	public Object getTag() {
		return mTag;
	}

	public int getRequestType() {
		return mRequestType;
	}

	public OkHttpClient getOkHttpClient() {
		return mOkHttpClient;
	}

	public void setUserAgent(String userAgent) {
		this.mUserAgent = userAgent;
	}

	public String getUserAgent() {
		return mUserAgent;
	}

	public Type getItemClass() {
		return mItemClass;
	}

	public void setItemClass(Class itemClass) {
		this.mItemClass = itemClass;
	}

	public DownloadProgressListener getDownloadProgressListener() {
		return (bytesDownloaded, totalBytes) -> {
			if (mDownloadProgressListener != null && !isCancelled) {
				mDownloadProgressListener.onProgress(bytesDownloaded, totalBytes);
			}
		};
	}

	public void updateDownloadCompletion(Response okHttpResponse) {
		isDelivered = true;
		if (mDownloadListener != null) {
			if (!isCancelled) {
				if (mExecutor != null) {
					mExecutor.execute(() -> {
						if (mDownloadListener != null) {
							mDownloadListener.onDownloadComplete(okHttpResponse);
						}
						finish();
					});
				} else {
					Core.gi().getExecutorSupplier().forMainThreadTasks().execute(() -> {
						if (mDownloadListener != null) {
							mDownloadListener.onDownloadComplete(okHttpResponse);
						}
						finish();
					});
				}
			} else {
				deliverError(new KnError());
				finish();
			}
		} else {
			finish();
		}
	}

	public UploadProgressListener getUploadProgressListener() {
		return (bytesUploaded, totalBytes) -> {
			mProgress = (int) ((bytesUploaded * 100) / totalBytes);
			if (mUploadProgressListener != null && !isCancelled) {
				mUploadProgressListener.onProgress(bytesUploaded, totalBytes);
			}
		};
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

	public void cancel(boolean forceCancel) {
		try {
			if (forceCancel || mPercentageThresholdForCancelling == 0
					|| mProgress < mPercentageThresholdForCancelling) {
				isCancelled = true;
				isRunning = false;
				if (call != null) {
					call.cancel();
				}
				if (future != null) {
					future.cancel(true);
				}
				if (!isDelivered) {
					deliverError(new KnError());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean isCanceled() {
		return isCancelled;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public void setRunning(boolean running) {
		isRunning = running;
	}

	public Call getCall() {
		return call;
	}

	public void setCall(Call call) {
		this.call = call;
	}

	public Future getFuture() {
		return future;
	}

	public void setFuture(Future future) {
		this.future = future;
	}

	public void destroy() {
		mJSONArrayRequestListener = null;
		mJSONObjectRequestListener = null;
		mStringRequestListener = null;
		mBitmapRequestListener = null;
		mParsedRequestListener = null;
		mDownloadProgressListener = null;
		mUploadProgressListener = null;
		mDownloadListener = null;
		mOkHttpResponseListener = null;
	}

	public void finish() {
		destroy();
		KnRequestQueue.gi().finish(this);
	}

	public KnResponse parseResponse(Response response) {
		switch (mResponseType) {
			case JSON_ARRAY:
				try {
					//noinspection ConstantConditions
					return KnResponse.success(JSON.parseArray(Okio.buffer(response.body().source()).readUtf8()));
				} catch (Exception e) {
					return KnResponse.failed(Utils.getErrorForParse(new KnError(e)));
				}
			case JSON_OBJECT:
				try {
					//noinspection ConstantConditions
					return KnResponse.success(JSON.parseObject(Okio.buffer(response.body().source()).readUtf8()));
				} catch (Exception e) {
					return KnResponse.failed(Utils.getErrorForParse(new KnError(e)));
				}
			case STRING:
				try {
					ResponseBody body = response.body();
					MediaType contentType = body.contentType();
					Charset charset = contentType != null && contentType.charset() != null ? contentType.charset(StandardCharsets.UTF_8) : StandardCharsets.UTF_8;
					//noinspection ConstantConditions
					return KnResponse.success(Okio.buffer(body.source()).readString(charset));
				} catch (Exception e) {
					return KnResponse.failed(Utils.getErrorForParse(new KnError(e)));
				}
			case BITMAP:
				synchronized (sDecodeLock) {
					try {
						return Utils.decodeBitmap(response, mMaxWidth, mMaxHeight, mDecodeConfig, mBitmapOptions, mScaleType);
					} catch (Exception e) {
						return KnResponse.failed(Utils.getErrorForParse(new KnError(e)));
					}
				}
			case OBJECT:
				try {
					//noinspection ConstantConditions
					return KnResponse.success(JSON.parseObject(Okio.buffer(response.body().source()).readUtf8(), mItemClass));
				} catch (Exception e) {
					return KnResponse.failed(Utils.getErrorForParse(new KnError(e)));
				}
			case OBJECT_LIST:
				try {
					//noinspection ConstantConditions
					return KnResponse.success(JSON.parseArray(Okio.buffer(response.body().source()).readUtf8(), mItemClass));
				} catch (Exception e) {
					return KnResponse.failed(Utils.getErrorForParse(new KnError(e)));
				}
		}
		return null;
	}

	public KnError parseNetworkError(KnError knError) {
		try {
			//noinspection ConstantConditions
			if (knError.getResponse() != null && knError.getResponse().body() != null && knError.getResponse().body().source() != null) {
				//noinspection ConstantConditions
				knError.setErrorBody(Okio.buffer(knError.getResponse().body().source()).readUtf8());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return knError;
	}

	public synchronized void deliverError(KnError knError) {
		try {
			if (!isDelivered) {
				if (isCancelled) {
					knError.setCancellationMessageInError();
					knError.setErrorCode(0);
				}
				deliverErrorResponse(knError);
			}
			isDelivered = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	public void deliverResponse(final KnResponse response) {
		try {
			isDelivered = true;
			if (!isCancelled) {
				if (mExecutor != null) {
					mExecutor.execute(() -> deliverSuccessResponse(response));
				} else {
					Core.gi().getExecutorSupplier().forMainThreadTasks().execute(() -> deliverSuccessResponse(response));
				}
			} else {
				KnError knError = new KnError();
				knError.setCancellationMessageInError();
				knError.setErrorCode(0);
				deliverErrorResponse(knError);
				finish();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void deliverSuccessResponse(KnResponse response) {
		if (mJSONObjectRequestListener != null) {
			mJSONObjectRequestListener.onResponse((JSONObject) response.getResult(), response.getOkHttpResponse());
		} else if (mJSONArrayRequestListener != null) {
			mJSONArrayRequestListener.onResponse((JSONArray) response.getResult(), response.getOkHttpResponse());
		} else if (mStringRequestListener != null) {
			mStringRequestListener.onResponse((String) response.getResult(), response.getOkHttpResponse());
		} else if (mBitmapRequestListener != null) {
			mBitmapRequestListener.onResponse((Bitmap) response.getResult(), response.getOkHttpResponse());
		} else if (mParsedRequestListener != null) {
			mParsedRequestListener.onResponse(response.getResult(), response.getOkHttpResponse());
		}
		finish();
	}

	private void deliverErrorResponse(KnError knError) {
		if (mJSONObjectRequestListener != null) {
			mJSONObjectRequestListener.onError(knError);
		} else if (mJSONArrayRequestListener != null) {
			mJSONArrayRequestListener.onError(knError);
		} else if (mStringRequestListener != null) {
			mStringRequestListener.onError(knError);
		} else if (mBitmapRequestListener != null) {
			mBitmapRequestListener.onError(knError);
		} else if (mParsedRequestListener != null) {
			mParsedRequestListener.onError(knError);
		} else if (mOkHttpResponseListener != null) {
			mOkHttpResponseListener.onError(knError);
		} else if (mDownloadListener != null) {
			mDownloadListener.onError(knError);
		}
	}

	public void deliverOkHttpResponse(final Response response) {
		try {
			isDelivered = true;
			if (!isCancelled) {
				if (mExecutor != null) {
					mExecutor.execute(() -> {
						if (mOkHttpResponseListener != null) {
							mOkHttpResponseListener.onResponse(response);
						}
						finish();
					});
				} else {
					Core.gi().getExecutorSupplier().forMainThreadTasks().execute(() -> {
						if (mOkHttpResponseListener != null) {
							mOkHttpResponseListener.onResponse(response);
						}
						finish();
					});
				}
			} else {
				KnError knError = new KnError();
				knError.setCancellationMessageInError();
				knError.setErrorCode(0);
				if (mOkHttpResponseListener != null) {
					mOkHttpResponseListener.onError(knError);
				}
				finish();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public RequestBody getRequestBody() {
		if (mApplicationJsonString != null) {
			if (customMediaType != null) {
				return RequestBody.create(mApplicationJsonString, customMediaType);
			}
			return RequestBody.create(mApplicationJsonString, MEDIA_TYPE_JSON);
		} else if (mStringBody != null) {
			if (customMediaType != null) {
				return RequestBody.create(mStringBody, customMediaType);
			}
			return RequestBody.create(mStringBody, MEDIA_TYPE_TEXT);
		} else if (mFile != null) {
			if (customMediaType != null) {
				return RequestBody.create(mFile, customMediaType);
			}
			return RequestBody.create(mFile, MEDIA_TYPE_OCTET_STREAM);
		} else if (mByte != null) {
			if (customMediaType != null) {
				return RequestBody.create(mByte, customMediaType);
			}
			return RequestBody.create(mByte, MEDIA_TYPE_OCTET_STREAM);
		} else {
			FormBody.Builder builder = new FormBody.Builder();
			try {
				for (HashMap.Entry<String, String> entry : mBodyParameterMap.entrySet()) {
					builder.add(entry.getKey(), entry.getValue());
				}
				for (HashMap.Entry<String, String> entry : mUrlEncodedFormBodyParameterMap.entrySet()) {
					builder.addEncoded(entry.getKey(), entry.getValue());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return builder.build();
		}
	}

	public RequestBody getMultiPartRequestBody() {
		MultipartBody.Builder builder = new MultipartBody.Builder()
				.setType((customMediaType == null) ? MultipartBody.FORM : customMediaType);
		try {
			for (HashMap.Entry<String, MultipartStringBody> entry : mMultiPartParameterMap.entrySet()) {
				MultipartStringBody stringBody = entry.getValue();
				MediaType mediaType = null;
				if (stringBody.contentType != null) {
					mediaType = MediaType.parse(stringBody.contentType);
				}
				builder.addPart(Headers.of("Content-Disposition",
						"form-data; name=\"" + entry.getKey() + "\""),
						RequestBody.create(stringBody.value, mediaType));
			}
			for (HashMap.Entry<String, List<MultipartFileBody>> entry : mMultiPartFileMap.entrySet()) {
				List<MultipartFileBody> fileBodies = entry.getValue();
				for (MultipartFileBody fileBody : fileBodies) {
					String fileName = fileBody.file.getName();
					MediaType mediaType;

					//noinspection ReplaceNullCheck
					if (fileBody.contentType != null) {
						mediaType = MediaType.parse(fileBody.contentType);
					} else {
						mediaType = MediaType.parse(Utils.getMimeType(fileName));
					}

					RequestBody requestBody = RequestBody.create(fileBody.file, mediaType);
					builder.addPart(Headers.of("Content-Disposition",
							"form-data; name=\"" + entry.getKey() + "\"; filename=\"" + fileName + "\""),
							requestBody);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return builder.build();
	}

	public Headers getHeaders() {
		Headers.Builder builder = new Headers.Builder();
		try {
			if (mHeadersMap != null) {
				Set<Map.Entry<String, List<String>>> entries = mHeadersMap.entrySet();
				for (Map.Entry<String, List<String>> entry : entries) {
					String name = entry.getKey();
					List<String> list = entry.getValue();
					if (list != null) {
						for (String value : list) {
							builder.add(name, value);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return builder.build();
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

	public static class GetRequestBuilder<T extends GetRequestBuilder> implements RequestBuilder {
		private Priority mPriority = Priority.IMMEDIATE;
		private final int mMethod;
		private final String mUrl;
		private Object mTag;
		private Bitmap.Config mDecodeConfig;
		private BitmapFactory.Options mBitmapOptions = new BitmapFactory.Options();
		private int mMaxWidth;
		private int mMaxHeight;
		private ImageView.ScaleType mScaleType;
		private final HashMap<String, List<String>> mHeadersMap = new HashMap<>();
		private final HashMap<String, List<String>> mQueryParameterMap = new HashMap<>();
		private final HashMap<String, String> mPathParameterMap = new HashMap<>();
		private CacheControl mCacheControl;
		private Executor mExecutor;
		private OkHttpClient mOkHttpClient;
		private String mUserAgent;

		public GetRequestBuilder(String url) {
			this.mUrl = url;
			this.mMethod = Method.GET;
		}

		public GetRequestBuilder(String url, int method) {
			this.mUrl = url;
			this.mMethod = method;
		}

		@Override
		public T setPriority(Priority priority) {
			mPriority = priority;
			return (T) this;
		}

		@Override
		public T setTag(Object tag) {
			mTag = tag;
			return (T) this;
		}

		@Override
		public T addQueryParameter(String key, String value) {
			List<String> list = mQueryParameterMap.get(key);
			if (list == null) {
				list = new ArrayList<>();
				mQueryParameterMap.put(key, list);
			}
			if (!list.contains(value)) {
				list.add(value);
			}
			return (T) this;
		}

		@Override
		public T addQueryParameter(Map<String, String> queryParameterMap) {
			if (queryParameterMap != null) {
				for (HashMap.Entry<String, String> entry : queryParameterMap.entrySet()) {
					addQueryParameter(entry.getKey(), entry.getValue());
				}
			}
			return (T) this;
		}

		@Override
		public T addPathParameter(String key, String value) {
			mPathParameterMap.put(key, value);
			return (T) this;
		}

		@Override
		public T addPathParameter(Map<String, String> pathParameterMap) {
			if (pathParameterMap != null) {
				mPathParameterMap.putAll(pathParameterMap);
			}
			return (T) this;
		}

		@Override
		public T addHeaders(String key, String value) {
			List<String> list = mHeadersMap.get(key);
			if (list == null) {
				list = new ArrayList<>();
				mHeadersMap.put(key, list);
			}
			if (!list.contains(value)) {
				list.add(value);
			}
			return (T) this;
		}

		@Override
		public T addHeaders(Map<String, String> headerMap) {
			if (headerMap != null) {
				for (HashMap.Entry<String, String> entry : headerMap.entrySet()) {
					addHeaders(entry.getKey(), entry.getValue());
				}
			}
			return (T) this;
		}

		@Override
		public T doNotCacheResponse() {
			mCacheControl = new CacheControl.Builder().noStore().build();
			return (T) this;
		}

		@Override
		public T getResponseOnlyIfCached() {
			mCacheControl = CacheControl.FORCE_CACHE;
			return (T) this;
		}

		@Override
		public T getResponseOnlyFromNetwork() {
			mCacheControl = CacheControl.FORCE_NETWORK;
			return (T) this;
		}

		@Override
		public T setMaxAgeCacheControl(int maxAge, TimeUnit timeUnit) {
			mCacheControl = new CacheControl.Builder().maxAge(maxAge, timeUnit).build();
			return (T) this;
		}

		@Override
		public T setMaxStaleCacheControl(int maxStale, TimeUnit timeUnit) {
			mCacheControl = new CacheControl.Builder().maxStale(maxStale, timeUnit).build();
			return (T) this;
		}

		@Override
		public T setExecutor(Executor executor) {
			mExecutor = executor;
			return (T) this;
		}

		@Override
		public T setOkHttpClient(OkHttpClient okHttpClient) {
			mOkHttpClient = okHttpClient;
			return (T) this;
		}

		@Override
		public T setUserAgent(String userAgent) {
			mUserAgent = userAgent;
			return (T) this;
		}

		public T setBitmapConfig(Bitmap.Config bitmapConfig) {
			mDecodeConfig = bitmapConfig;
			return (T) this;
		}

		public T setBitmapOptions(BitmapFactory.Options bitmapOptions) {
			mBitmapOptions = bitmapOptions;
			return (T) this;
		}

		public T setBitmapMaxHeight(int maxHeight) {
			mMaxHeight = maxHeight;
			return (T) this;
		}

		public T setBitmapMaxWidth(int maxWidth) {
			mMaxWidth = maxWidth;
			return (T) this;
		}

		public T setImageScaleType(ImageView.ScaleType imageScaleType) {
			mScaleType = imageScaleType;
			return (T) this;
		}

		public KnRequest build() {
			return new KnRequest(this);
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

		public DynamicRequestBuilder(String url, int method) {
			super(url, method);
		}
	}

	public static class PostRequestBuilder<T extends PostRequestBuilder> implements RequestBuilder {

		private Priority mPriority = Priority.IMMEDIATE;
		private final int mMethod;
		private final String mUrl;
		private Object mTag;
		private String mApplicationJsonString = null;
		private String mStringBody = null;
		private byte[] mByte = null;
		private File mFile = null;
		private final HashMap<String, List<String>> mHeadersMap = new HashMap<>();
		private final HashMap<String, String> mBodyParameterMap = new HashMap<>();
		private final HashMap<String, String> mUrlEncodedFormBodyParameterMap = new HashMap<>();
		private final HashMap<String, List<String>> mQueryParameterMap = new HashMap<>();
		private final HashMap<String, String> mPathParameterMap = new HashMap<>();
		private CacheControl mCacheControl;
		private Executor mExecutor;
		private OkHttpClient mOkHttpClient;
		private String mUserAgent;
		private String mCustomContentType;

		public PostRequestBuilder(String url) {
			this.mUrl = url;
			this.mMethod = Method.POST;
		}

		public PostRequestBuilder(String url, int method) {
			this.mUrl = url;
			this.mMethod = method;
		}

		@Override
		public T setPriority(Priority priority) {
			mPriority = priority;
			return (T) this;
		}

		@Override
		public T setTag(Object tag) {
			mTag = tag;
			return (T) this;
		}

		@Override
		public T addQueryParameter(String key, String value) {
			List<String> list = mQueryParameterMap.get(key);
			if (list == null) {
				list = new ArrayList<>();
				mQueryParameterMap.put(key, list);
			}
			if (!list.contains(value)) {
				list.add(value);
			}
			return (T) this;
		}

		@Override
		public T addQueryParameter(Map<String, String> queryParameterMap) {
			if (queryParameterMap != null) {
				for (HashMap.Entry<String, String> entry : queryParameterMap.entrySet()) {
					addQueryParameter(entry.getKey(), entry.getValue());
				}
			}
			return (T) this;
		}

		@Override
		public T addPathParameter(String key, String value) {
			mPathParameterMap.put(key, value);
			return (T) this;
		}

		@Override
		public T addPathParameter(Map<String, String> pathParameterMap) {
			if (pathParameterMap != null) {
				mPathParameterMap.putAll(pathParameterMap);
			}
			return (T) this;
		}

		@Override
		public T addHeaders(String key, String value) {
			List<String> list = mHeadersMap.get(key);
			if (list == null) {
				list = new ArrayList<>();
				mHeadersMap.put(key, list);
			}
			if (!list.contains(value)) {
				list.add(value);
			}
			return (T) this;
		}

		@Override
		public T addHeaders(Map<String, String> headerMap) {
			if (headerMap != null) {
				for (HashMap.Entry<String, String> entry : headerMap.entrySet()) {
					addHeaders(entry.getKey(), entry.getValue());
				}
			}
			return (T) this;
		}

		@Override
		public T doNotCacheResponse() {
			mCacheControl = new CacheControl.Builder().noStore().build();
			return (T) this;
		}

		@Override
		public T getResponseOnlyIfCached() {
			mCacheControl = CacheControl.FORCE_CACHE;
			return (T) this;
		}

		@Override
		public T getResponseOnlyFromNetwork() {
			mCacheControl = CacheControl.FORCE_NETWORK;
			return (T) this;
		}

		@Override
		public T setMaxAgeCacheControl(int maxAge, TimeUnit timeUnit) {
			mCacheControl = new CacheControl.Builder().maxAge(maxAge, timeUnit).build();
			return (T) this;
		}

		@Override
		public T setMaxStaleCacheControl(int maxStale, TimeUnit timeUnit) {
			mCacheControl = new CacheControl.Builder().maxStale(maxStale, timeUnit).build();
			return (T) this;
		}

		@Override
		public T setExecutor(Executor executor) {
			mExecutor = executor;
			return (T) this;
		}

		@Override
		public T setOkHttpClient(OkHttpClient okHttpClient) {
			mOkHttpClient = okHttpClient;
			return (T) this;
		}

		@Override
		public T setUserAgent(String userAgent) {
			mUserAgent = userAgent;
			return (T) this;
		}

		public T addBodyParameter(String key, String value) {
			mBodyParameterMap.put(key, value);
			return (T) this;
		}

		public T addBodyParameter(Map<String, String> bodyParameterMap) {
			if (bodyParameterMap != null) {
				mBodyParameterMap.putAll(bodyParameterMap);
			}
			return (T) this;
		}

		public T addUrlEncodeFormBodyParameter(String key, String value) {
			mUrlEncodedFormBodyParameterMap.put(key, value);
			return (T) this;
		}

		public T addUrlEncodeFormBodyParameter(Map<String, String> bodyParameterMap) {
			if (bodyParameterMap != null) {
				mUrlEncodedFormBodyParameterMap.putAll(bodyParameterMap);
			}
			return (T) this;
		}

		public T addJSONObjectBody(JSONObject jsonObject) {
			if (jsonObject != null) {
				mApplicationJsonString = jsonObject.toString();
			}
			return (T) this;
		}

		public T addJSONArrayBody(JSONArray jsonArray) {
			if (jsonArray != null) {
				mApplicationJsonString = jsonArray.toString();
			}
			return (T) this;
		}

		public T addStringBody(String stringBody) {
			mStringBody = stringBody;
			return (T) this;
		}

		public T addFileBody(File file) {
			mFile = file;
			return (T) this;
		}

		public T addByteBody(byte[] bytes) {
			mByte = bytes;
			return (T) this;
		}

		public T setContentType(String contentType) {
			mCustomContentType = contentType;
			return (T) this;
		}

		public KnRequest build() {
			return new KnRequest(this);
		}
	}

	public static class DownloadBuilder<T extends DownloadBuilder> implements RequestBuilder {

		private Priority mPriority = Priority.IMMEDIATE;
		private final String mUrl;
		private Object mTag;
		private final HashMap<String, List<String>> mHeadersMap = new HashMap<>();
		private final HashMap<String, List<String>> mQueryParameterMap = new HashMap<>();
		private final HashMap<String, String> mPathParameterMap = new HashMap<>();
		private final String mDirPath;
		private final String mFileName;
		private CacheControl mCacheControl;
		private int mPercentageThresholdForCancelling = 0;
		private Executor mExecutor;
		private OkHttpClient mOkHttpClient;
		private String mUserAgent;

		public DownloadBuilder(String url, String dirPath, String fileName) {
			this.mUrl = url;
			this.mDirPath = dirPath;
			this.mFileName = fileName;
		}

		@Override
		public T setPriority(Priority priority) {
			mPriority = priority;
			return (T) this;
		}

		@Override
		public T setTag(Object tag) {
			mTag = tag;
			return (T) this;
		}

		@Override
		public T addHeaders(String key, String value) {
			List<String> list = mHeadersMap.get(key);
			if (list == null) {
				list = new ArrayList<>();
				mHeadersMap.put(key, list);
			}
			if (!list.contains(value)) {
				list.add(value);
			}
			return (T) this;
		}

		@Override
		public T addHeaders(Map<String, String> headerMap) {
			if (headerMap != null) {
				for (HashMap.Entry<String, String> entry : headerMap.entrySet()) {
					addHeaders(entry.getKey(), entry.getValue());
				}
			}
			return (T) this;
		}

		@Override
		public T addQueryParameter(String key, String value) {
			List<String> list = mQueryParameterMap.get(key);
			if (list == null) {
				list = new ArrayList<>();
				mQueryParameterMap.put(key, list);
			}
			if (!list.contains(value)) {
				list.add(value);
			}
			return (T) this;
		}

		@Override
		public T addQueryParameter(Map<String, String> queryParameterMap) {
			if (queryParameterMap != null) {
				for (HashMap.Entry<String, String> entry : queryParameterMap.entrySet()) {
					addQueryParameter(entry.getKey(), entry.getValue());
				}
			}
			return (T) this;
		}

		@Override
		public T addPathParameter(String key, String value) {
			mPathParameterMap.put(key, value);
			return (T) this;
		}

		@Override
		public T addPathParameter(Map<String, String> pathParameterMap) {
			if (pathParameterMap != null) {
				mPathParameterMap.putAll(pathParameterMap);
			}
			return (T) this;
		}

		@Override
		public T doNotCacheResponse() {
			mCacheControl = new CacheControl.Builder().noStore().build();
			return (T) this;
		}

		@Override
		public T getResponseOnlyIfCached() {
			mCacheControl = CacheControl.FORCE_CACHE;
			return (T) this;
		}

		@Override
		public T getResponseOnlyFromNetwork() {
			mCacheControl = CacheControl.FORCE_NETWORK;
			return (T) this;
		}

		@Override
		public T setMaxAgeCacheControl(int maxAge, TimeUnit timeUnit) {
			mCacheControl = new CacheControl.Builder().maxAge(maxAge, timeUnit).build();
			return (T) this;
		}

		@Override
		public T setMaxStaleCacheControl(int maxStale, TimeUnit timeUnit) {
			mCacheControl = new CacheControl.Builder().maxStale(maxStale, timeUnit).build();
			return (T) this;
		}

		@Override
		public T setExecutor(Executor executor) {
			mExecutor = executor;
			return (T) this;
		}

		@Override
		public T setOkHttpClient(OkHttpClient okHttpClient) {
			mOkHttpClient = okHttpClient;
			return (T) this;
		}

		@Override
		public T setUserAgent(String userAgent) {
			mUserAgent = userAgent;
			return (T) this;
		}

		public T setPercentageThresholdForCancelling(int percentageThresholdForCancelling) {
			mPercentageThresholdForCancelling = percentageThresholdForCancelling;
			return (T) this;
		}

		public KnRequest build() {
			return new KnRequest(this);
		}
	}

	public static class MultiPartBuilder<T extends MultiPartBuilder> implements RequestBuilder {

		private Priority mPriority = Priority.IMMEDIATE;
		private final String mUrl;
		private Object mTag;
		private final HashMap<String, List<String>> mHeadersMap = new HashMap<>();
		private final HashMap<String, List<String>> mQueryParameterMap = new HashMap<>();
		private final HashMap<String, String> mPathParameterMap = new HashMap<>();
		private final HashMap<String, MultipartStringBody> mMultiPartParameterMap = new HashMap<>();
		private final HashMap<String, List<MultipartFileBody>> mMultiPartFileMap = new HashMap<>();
		private CacheControl mCacheControl;
		private int mPercentageThresholdForCancelling = 0;
		private Executor mExecutor;
		private OkHttpClient mOkHttpClient;
		private String mUserAgent;
		private String mCustomContentType;

		public MultiPartBuilder(String url) {
			this.mUrl = url;
		}

		@Override
		public T setPriority(Priority priority) {
			mPriority = priority;
			return (T) this;
		}

		@Override
		public T setTag(Object tag) {
			mTag = tag;
			return (T) this;
		}

		@Override
		public T addQueryParameter(String key, String value) {
			List<String> list = mQueryParameterMap.get(key);
			if (list == null) {
				list = new ArrayList<>();
				mQueryParameterMap.put(key, list);
			}
			if (!list.contains(value)) {
				list.add(value);
			}
			return (T) this;
		}

		@Override
		public T addQueryParameter(Map<String, String> queryParameterMap) {
			if (queryParameterMap != null) {
				for (HashMap.Entry<String, String> entry : queryParameterMap.entrySet()) {
					addQueryParameter(entry.getKey(), entry.getValue());
				}
			}
			return (T) this;
		}

		@Override
		public T addPathParameter(String key, String value) {
			mPathParameterMap.put(key, value);
			return (T) this;
		}

		@Override
		public T addPathParameter(Map<String, String> pathParameterMap) {
			if (pathParameterMap != null) {
				mPathParameterMap.putAll(pathParameterMap);
			}
			return (T) this;
		}

		@Override
		public T addHeaders(String key, String value) {
			List<String> list = mHeadersMap.get(key);
			if (list == null) {
				list = new ArrayList<>();
				mHeadersMap.put(key, list);
			}
			if (!list.contains(value)) {
				list.add(value);
			}
			return (T) this;
		}

		@Override
		public T addHeaders(Map<String, String> headerMap) {
			if (headerMap != null) {
				for (HashMap.Entry<String, String> entry : headerMap.entrySet()) {
					addHeaders(entry.getKey(), entry.getValue());
				}
			}
			return (T) this;
		}

		@Override
		public T doNotCacheResponse() {
			mCacheControl = new CacheControl.Builder().noStore().build();
			return (T) this;
		}

		@Override
		public T getResponseOnlyIfCached() {
			mCacheControl = CacheControl.FORCE_CACHE;
			return (T) this;
		}

		@Override
		public T getResponseOnlyFromNetwork() {
			mCacheControl = CacheControl.FORCE_NETWORK;
			return (T) this;
		}

		@Override
		public T setMaxAgeCacheControl(int maxAge, TimeUnit timeUnit) {
			mCacheControl = new CacheControl.Builder().maxAge(maxAge, timeUnit).build();
			return (T) this;
		}

		@Override
		public T setMaxStaleCacheControl(int maxStale, TimeUnit timeUnit) {
			mCacheControl = new CacheControl.Builder().maxStale(maxStale, timeUnit).build();
			return (T) this;
		}

		@Override
		public T setExecutor(Executor executor) {
			mExecutor = executor;
			return (T) this;
		}

		@Override
		public T setOkHttpClient(OkHttpClient okHttpClient) {
			mOkHttpClient = okHttpClient;
			return (T) this;
		}

		@Override
		public T setUserAgent(String userAgent) {
			mUserAgent = userAgent;
			return (T) this;
		}

		public T addMultipartParameter(String key, String value) {
			return addMultipartParameter(key, value, null);
		}

		public T addMultipartParameter(String key, String value, String contentType) {
			MultipartStringBody stringBody = new MultipartStringBody(value, contentType);
			mMultiPartParameterMap.put(key, stringBody);
			return (T) this;
		}

		public T addMultipartParameter(Map<String, String> multiPartParameterMap) {
			return addMultipartParameter(multiPartParameterMap, null);
		}

		public T addMultipartParameter(Map<String, String> multiPartParameterMap, String contentType) {
			if (multiPartParameterMap != null) {
				Map<String, MultipartStringBody> parameterMap = new HashMap<>();
				for (HashMap.Entry<String, String> entry : multiPartParameterMap.entrySet()) {
					MultipartStringBody stringBody = new MultipartStringBody(entry.getValue(), contentType);
					parameterMap.put(entry.getKey(), stringBody);
				}
				mMultiPartParameterMap.putAll(parameterMap);
			}
			return (T) this;
		}

		public T addMultipartFile(String key, File file) {
			return addMultipartFile(key, file, null);
		}

		public T addMultipartFile(String key, File file, String contentType) {
			MultipartFileBody fileBody = new MultipartFileBody(file, contentType);
			addMultipartFileWithKey(key, fileBody);
			return (T) this;
		}

		public T addMultipartFile(Map<String, File> multiPartFileMap) {
			return addMultipartFile(multiPartFileMap, null);
		}

		public T addMultipartFile(Map<String, File> multiPartFileMap, String contentType) {
			if (multiPartFileMap != null) {
				for (HashMap.Entry<String, File> entry : multiPartFileMap.entrySet()) {
					MultipartFileBody fileBody = new MultipartFileBody(entry.getValue(), contentType);
					addMultipartFileWithKey(entry.getKey(), fileBody);
				}
			}
			return (T) this;
		}

		public T addMultipartFileList(String key, List<File> files) {
			return addMultipartFileList(key, files, null);
		}

		public T addMultipartFileList(String key, List<File> files, String contentType) {
			if (files != null) {
				for (File file : files) {
					MultipartFileBody fileBody = new MultipartFileBody(file, contentType);
					addMultipartFileWithKey(key, fileBody);
				}
			}
			return (T) this;
		}

		public T addMultipartFileList(Map<String, List<File>> multiPartFileMap) {
			return addMultipartFileList(multiPartFileMap, null);
		}

		public T addMultipartFileList(Map<String, List<File>> multiPartFileMap, String contentType) {
			if (multiPartFileMap != null) {
				Map<String, List<MultipartFileBody>> parameterMap = new HashMap<>();
				for (HashMap.Entry<String, List<File>> entry : multiPartFileMap.entrySet()) {
					List<File> files = entry.getValue();
					List<MultipartFileBody> fileBodies = new ArrayList<>();
					for (File file : files) {
						MultipartFileBody fileBody = new MultipartFileBody(file, contentType);
						fileBodies.add(fileBody);
					}
					parameterMap.put(entry.getKey(), fileBodies);
				}
				mMultiPartFileMap.putAll(parameterMap);
			}
			return (T) this;
		}

		public T setPercentageThresholdForCancelling(int percentageThresholdForCancelling) {
			this.mPercentageThresholdForCancelling = percentageThresholdForCancelling;
			return (T) this;
		}

		public T setContentType(String contentType) {
			mCustomContentType = contentType;
			return (T) this;
		}

		private void addMultipartFileWithKey(String key, MultipartFileBody fileBody) {
			List<MultipartFileBody> fileBodies = mMultiPartFileMap.get(key);
			if (fileBodies == null) {
				fileBodies = new ArrayList<>();
			}
			fileBodies.add(fileBody);
			mMultiPartFileMap.put(key, fileBodies);
		}

		public KnRequest build() {
			return new KnRequest(this);
		}
	}

	@NonNull
	@Override
	public String toString() {
		return "KnRequest{" +
				"sequenceNumber='" + sequenceNumber +
				", mMethod=" + mMethod +
				", mPriority=" + mPriority +
				", mRequestType=" + mRequestType +
				", mUrl=" + mUrl +
				'}';
	}
}

package ovh.karewan.knhttp.internal;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import okhttp3.Response;
import ovh.karewan.knhttp.KnHttp;
import ovh.karewan.knhttp.cache.LruBitmapCache;
import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.error.KnError;
import ovh.karewan.knhttp.interfaces.BitmapRequestListener;

import java.util.HashMap;
import java.util.LinkedList;

@SuppressWarnings("unused")
public final class KnImageLoader {
	private static volatile KnImageLoader sInstance;

	// Max size of the memory cache: 1/8th of the available VM memory by default (in bytes, like LruBitmapCache.sizeOf()).
	// It is a limit, the cache only holds the loaded images.
	private static volatile int sMaxCacheSize = (int) Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 8);
	private static Context sMemoryCallbacksContext = null; // Guarded by KnImageLoader.class

	// 0: the responses received during the same main loop iteration are still batched, without added latency
	private int mBatchResponseDelayMs = 0;

	// Created on first use only
	private volatile ImageCache mCache;

	private final HashMap<String, BatchedImageRequest> mInFlightRequests =
			new HashMap<>();

	private final HashMap<String, BatchedImageRequest> mBatchedResponses =
			new HashMap<>();

	private final Handler mHandler = new Handler(Looper.getMainLooper());

	private Runnable mRunnable;

	// Static: setting them must not create the loader
	private static volatile BitmapFactory.Options sBitmapOptions = null;

	private KnImageLoader() {}

	public static KnImageLoader gi() {
		if (sInstance == null) {
			synchronized (KnImageLoader.class) {
				if (sInstance == null)  sInstance = new KnImageLoader();
			}
		}

		return sInstance;
	}

	/**
	 * @return The instance, without creating it
	 */
	@Nullable
	public static KnImageLoader peek() {
		return sInstance;
	}

	/**
	 * @return The memory cache, without creating it
	 */
	@Nullable
	public static ImageCache peekImageCache() {
		final KnImageLoader instance = sInstance;
		return instance != null ? instance.mCache : null;
	}

	public static void setMaxCacheSize(int maxSizeBytes) {
		if (maxSizeBytes <= 0) throw new IllegalArgumentException("maxSizeBytes <= 0");

		sMaxCacheSize = maxSizeBytes;
		final ImageCache cache = peekImageCache();
		if (cache instanceof LruBitmapCache) ((LruBitmapCache) cache).resize(maxSizeBytes);
	}

	/**
	 * Releases the cached images when the system needs memory
	 */
	public static void registerMemoryCallbacks(@NonNull Context context) {
		final Context appContext = context.getApplicationContext();

		// Once per application (the application context lives as long as the process)
		synchronized (KnImageLoader.class) {
			if (sMemoryCallbacksContext == appContext) return;
			sMemoryCallbacksContext = appContext;
		}

		appContext.registerComponentCallbacks(new ComponentCallbacks2() {
			@Override
			public void onTrimMemory(int level) {
				trimMemory(level);
			}

			@Override
			public void onConfigurationChanged(@NonNull Configuration newConfig) {
			}

			@Override
			@SuppressWarnings("deprecation")
			public void onLowMemory() {
				trimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND);
			}
		});
	}

	static void trimMemory(int level) {
		final ImageCache cache = peekImageCache();
		if (cache == null) return;

		if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
			cache.evictAllBitmap();
		} else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && cache instanceof LruBitmapCache) {
			final LruBitmapCache lruCache = (LruBitmapCache) cache;
			lruCache.trimToSize(lruCache.maxSize() / 2);
		}
	}

	public static void shutDown() {
		final KnImageLoader instance;

		synchronized (KnImageLoader.class) {
			instance = sInstance;
			sInstance = null;
		}

		if (instance == null) return;
		instance.mHandler.removeCallbacksAndMessages(null);
		if (instance.mCache != null) instance.mCache.evictAllBitmap();
	}

	public interface ImageCache {
		Bitmap getBitmap(String key);
		void putBitmap(String key, Bitmap bitmap);
		void evictBitmap(String key);
		void evictAllBitmap();
	}

	/**
	 * @return The memory cache, created on first call
	 */
	@NonNull
	public ImageCache getImageCache() {
		ImageCache cache = mCache;
		if (cache != null) return cache;

		synchronized (this) {
			if (mCache == null) mCache = new LruBitmapCache(sMaxCacheSize);
			return mCache;
		}
	}

	public static ImageListener getImageListener(final ImageView view, final int defaultImageResId, final int errorImageResId) {
		return new ImageListener() {
			@Override
			public void onResponse(ImageContainer response, boolean isImmediate) {
				if (response.getBitmap() != null) view.setImageBitmap(response.getBitmap());
				else if (defaultImageResId != 0) view.setImageResource(defaultImageResId);
			}

			@Override
			public void onError(KnError knError) {
				if (errorImageResId != 0) view.setImageResource(errorImageResId);
			}
		};
	}

	public interface ImageListener {
		void onResponse(ImageContainer response, boolean isImmediate);
		void onError(KnError knError);
	}

	public boolean isCached(String requestUrl, int maxWidth, int maxHeight) {
		return isCached(requestUrl, maxWidth, maxHeight, ImageView.ScaleType.CENTER_INSIDE);
	}

	public boolean isCached(String requestUrl, int maxWidth, int maxHeight, ImageView.ScaleType scaleType) {
		throwIfNotOnMainThread();

		String cacheKey = getCacheKey(requestUrl, maxWidth, maxHeight, scaleType);
		final ImageCache cache = mCache;
		return cache != null && cache.getBitmap(cacheKey) != null;
	}

	public ImageContainer get(String requestUrl, final ImageListener listener) {
		return get(requestUrl, listener, 0, 0);
	}

	public ImageContainer get(String requestUrl, ImageListener imageListener, int maxWidth, int maxHeight) {
		return get(requestUrl, imageListener, maxWidth, maxHeight, ImageView.ScaleType.CENTER_INSIDE);
	}

	public ImageContainer get(String requestUrl, ImageListener imageListener, int maxWidth, int maxHeight, ImageView.ScaleType scaleType) {
		throwIfNotOnMainThread();

		final String cacheKey = getCacheKey(requestUrl, maxWidth, maxHeight, scaleType);

		Bitmap cachedBitmap = getImageCache().getBitmap(cacheKey);
		if (cachedBitmap != null) {
			ImageContainer container = new ImageContainer(cachedBitmap, requestUrl, null, null);
			imageListener.onResponse(container, true);
			return container;
		}

		ImageContainer imageContainer = new ImageContainer(null, requestUrl, cacheKey, imageListener);
		imageListener.onResponse(imageContainer, true);

		BatchedImageRequest request = mInFlightRequests.get(cacheKey);
		if (request != null) {
			request.addContainer(imageContainer);
			return imageContainer;
		}

		KnRequest newRequest = makeImageRequest(requestUrl, maxWidth, maxHeight, scaleType, cacheKey);

		mInFlightRequests.put(cacheKey, new BatchedImageRequest(newRequest, imageContainer));
		return imageContainer;
	}

	private KnRequest makeImageRequest(String requestUrl, int maxWidth, int maxHeight, ImageView.ScaleType scaleType, final String cacheKey) {
		final KnRequest request = KnHttp.get(requestUrl)
				.setTag("ImageRequestTag")
				.setBitmapMaxHeight(maxHeight)
				.setBitmapMaxWidth(maxWidth)
				.setImageScaleType(scaleType)
				.setBitmapConfig(Bitmap.Config.RGB_565)
				.setBitmapOptions(sBitmapOptions)
				.build();

		request.getAsBitmap(new BitmapRequestListener() {
			@Override
			public void onResponse(@NonNull Bitmap response, @NonNull Response okHttpResponse) {
				onGetImageSuccess(cacheKey, response);
			}

			@Override
			public void onError(@NonNull KnError knError) {
				onGetImageError(cacheKey, knError);
			}
		});

		return request;
	}

	public void setBitmapDecodeOptions(BitmapFactory.Options bitmapOptions) {
		setDefaultBitmapDecodeOptions(bitmapOptions);
	}

	/**
	 * Decode options of the images loaded by KnImageLoader, without creating the loader
	 */
	public static void setDefaultBitmapDecodeOptions(@Nullable BitmapFactory.Options bitmapOptions) {
		sBitmapOptions = bitmapOptions;
	}

	public void setBatchedResponseDelay(int newBatchedResponseDelayMs) {
		mBatchResponseDelayMs = newBatchedResponseDelayMs;
	}

	private void onGetImageSuccess(String cacheKey, Bitmap response) {
		getImageCache().putBitmap(cacheKey, response);
		BatchedImageRequest request = mInFlightRequests.remove(cacheKey);

		if (request != null) {
			request.mResponseBitmap = response;
			batchResponse(cacheKey, request);
		}
	}

	private void onGetImageError(String cacheKey, KnError knError) {
		BatchedImageRequest request = mInFlightRequests.remove(cacheKey);

		if (request != null) {
			request.setError(knError);
			batchResponse(cacheKey, request);
		}
	}

	public class ImageContainer {

		private Bitmap mBitmap;

		private final ImageListener mListener;

		private final String mCacheKey;

		private final String mRequestUrl;

		public ImageContainer(Bitmap bitmap, String requestUrl, String cacheKey, ImageListener listener) {
			mBitmap = bitmap;
			mRequestUrl = requestUrl;
			mCacheKey = cacheKey;
			mListener = listener;
		}

		public void cancelRequest() {
			if (mListener == null) return;

			BatchedImageRequest request = mInFlightRequests.get(mCacheKey);
			if (request != null) {
				boolean canceled = request.removeContainerAndCancelIfNecessary(this);
				if (canceled) mInFlightRequests.remove(mCacheKey);
			} else {
				request = mBatchedResponses.get(mCacheKey);
				if (request != null) {
					request.removeContainerAndCancelIfNecessary(this);
					if (request.mContainers.isEmpty()) mBatchedResponses.remove(mCacheKey);
				}
			}
		}

		public Bitmap getBitmap() {
			return mBitmap;
		}


		public String getRequestUrl() {
			return mRequestUrl;
		}
	}

	private class BatchedImageRequest {

		private final KnRequest mRequest;

		private Bitmap mResponseBitmap;

		private KnError mKnError;

		private final LinkedList<ImageContainer> mContainers = new LinkedList<>();

		public BatchedImageRequest(KnRequest request, ImageContainer container) {
			mRequest = request;
			mContainers.add(container);
		}

		public void setError(KnError knError) {
			mKnError = knError;
		}

		public KnError getError() {
			return mKnError;
		}

		public void addContainer(ImageContainer container) {
			mContainers.add(container);
		}

		public boolean removeContainerAndCancelIfNecessary(ImageContainer container) {
			mContainers.remove(container);
			if (mContainers.isEmpty()) {
				mRequest.cancel(true);
				return true;
			}
			return false;
		}
	}

	private void batchResponse(String cacheKey, BatchedImageRequest request) {
		mBatchedResponses.put(cacheKey, request);
		if (mRunnable == null) {
			mRunnable = () -> {
				for (BatchedImageRequest bir : mBatchedResponses.values()) {
					for (ImageContainer container : bir.mContainers) {
						if (container.mListener == null) continue;

						if (bir.getError() == null) {
							container.mBitmap = bir.mResponseBitmap;
							container.mListener.onResponse(container, false);
						} else {
							container.mListener.onError(bir.getError());
						}
					}
				}
				mBatchedResponses.clear();
				mRunnable = null;
			};
			mHandler.postDelayed(mRunnable, mBatchResponseDelayMs);
		}
	}

	private void throwIfNotOnMainThread() {
		if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("ImageLoader must be invoked from the main thread.");
	}

	private static String getCacheKey(String url, int maxWidth, int maxHeight, ImageView.ScaleType scaleType) {
		return "#W" + maxWidth + "#H" + maxHeight + "#S" + scaleType.ordinal() + url;
	}
}

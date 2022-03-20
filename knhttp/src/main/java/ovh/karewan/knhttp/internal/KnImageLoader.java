package ovh.karewan.knhttp.internal;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import okhttp3.Response;
import ovh.karewan.knhttp.KnHttp;
import ovh.karewan.knhttp.cache.LruBitmapCache;
import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.error.KnError;
import ovh.karewan.knhttp.interfaces.BitmapRequestListener;

import java.util.HashMap;
import java.util.LinkedList;

@SuppressWarnings({"unused", "rawtypes"})
public final class KnImageLoader {
	private static volatile KnImageLoader sInstance;

	// Get max available VM memory, exceeding this amount will throw an
	// OutOfMemory exception. Stored in kilobytes as LruCache takes an
	// int in its constructor.
	private static final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

	// Use 1/8th of the available memory for this memory cache.
	private static final int cacheSize = maxMemory / 8;

	private int mBatchResponseDelayMs = 100;

	private final ImageCache mCache;

	private final HashMap<String, BatchedImageRequest> mInFlightRequests =
			new HashMap<>();

	private final HashMap<String, BatchedImageRequest> mBatchedResponses =
			new HashMap<>();

	private final Handler mHandler = new Handler(Looper.getMainLooper());

	private Runnable mRunnable;

	private BitmapFactory.Options mBitmapOptions = new BitmapFactory.Options();

	private KnImageLoader(ImageCache imageCache) {
		mCache = imageCache;
	}

	public static KnImageLoader gi() {
		if (sInstance == null) {
			synchronized (KnImageLoader.class) {
				if (sInstance == null)  sInstance = new KnImageLoader(new LruBitmapCache(cacheSize));
			}
		}

		return sInstance;
	}

	public static void shutDown() {
		if(sInstance == null) return;

		sInstance.mHandler.removeCallbacks(null);
		if(sInstance.mCache != null) sInstance.mCache.evictAllBitmap();

		synchronized (KnImageLoader.class) {
			sInstance = null;
		}
	}

	public interface ImageCache {
		Bitmap getBitmap(String key);
		void putBitmap(String key, Bitmap bitmap);
		void evictBitmap(String key);
		void evictAllBitmap();
	}

	public ImageCache getImageCache() {
		return mCache;
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
		return mCache.getBitmap(cacheKey) != null;
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

		Bitmap cachedBitmap = mCache.getBitmap(cacheKey);
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

	protected KnRequest makeImageRequest(String requestUrl, int maxWidth, int maxHeight, ImageView.ScaleType scaleType, final String cacheKey) {
		KnRequest KnRequest = KnHttp.get(requestUrl)
				.setTag("ImageRequestTag")
				.setBitmapMaxHeight(maxHeight)
				.setBitmapMaxWidth(maxWidth)
				.setImageScaleType(scaleType)
				.setBitmapConfig(Bitmap.Config.RGB_565)
				.setBitmapOptions(mBitmapOptions)
				.build();

		KnRequest.getAsBitmap(new BitmapRequestListener() {
			@Override
			public void onResponse(Bitmap response, Response okHttpResponse) {
				onGetImageSuccess(cacheKey, response);
			}

			@Override
			public void onError(KnError knError) {
				onGetImageError(cacheKey, knError);
			}
		});

		return KnRequest;
	}

	public void setBitmapDecodeOptions(BitmapFactory.Options bitmapOptions) {
		mBitmapOptions = bitmapOptions;
	}

	public void setBatchedResponseDelay(int newBatchedResponseDelayMs) {
		mBatchResponseDelayMs = newBatchedResponseDelayMs;
	}

	protected void onGetImageSuccess(String cacheKey, Bitmap response) {
		mCache.putBitmap(cacheKey, response);
		BatchedImageRequest request = mInFlightRequests.remove(cacheKey);

		if (request != null) {
			request.mResponseBitmap = response;
			batchResponse(cacheKey, request);
		}
	}

	protected void onGetImageError(String cacheKey, KnError knError) {
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
					if (request.mContainers.size() == 0) mBatchedResponses.remove(mCacheKey);
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
			if (mContainers.size() == 0) {
				mRequest.cancel(true);
				if (mRequest.isCanceled()) {
					mRequest.destroy();
					KnRequestQueue.gi().finish(mRequest);
				}
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

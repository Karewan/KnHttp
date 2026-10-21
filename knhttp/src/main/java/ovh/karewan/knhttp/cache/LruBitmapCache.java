package ovh.karewan.knhttp.cache;

import android.graphics.Bitmap;
import android.util.LruCache;

import ovh.karewan.knhttp.internal.KnImageLoader;

@SuppressWarnings("unused")
public final class LruBitmapCache extends LruCache<String, Bitmap>
		implements KnImageLoader.ImageCache {

	/**
	 * @param maxSize The max size of the cache in bytes
	 */
	public LruBitmapCache(int maxSize) {
		super(maxSize);
	}

	@Override
	protected int sizeOf(String key, Bitmap value) {
		return value.getAllocationByteCount();
	}

	@Override
	public Bitmap getBitmap(String key) {
		return get(key);
	}

	@Override
	public void evictBitmap(String key) {
		remove(key);
	}

	@Override
	public void evictAllBitmap() {
		evictAll();
	}

	@Override
	public void putBitmap(String url, Bitmap bitmap) {
		put(url, bitmap);
	}

}

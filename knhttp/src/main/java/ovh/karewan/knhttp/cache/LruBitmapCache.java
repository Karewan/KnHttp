package ovh.karewan.knhttp.cache;

import android.graphics.Bitmap;

import ovh.karewan.knhttp.internal.ANImageLoader;

public final class LruBitmapCache extends LruCache<String, Bitmap>
		implements ANImageLoader.ImageCache {

	public LruBitmapCache(int maxSize) {
		super(maxSize);
	}

	@Override
	protected int sizeOf(String key, Bitmap value) {
		return value.getRowBytes() * value.getHeight();
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

package ovh.karewan.knhttp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.ComponentCallbacks2;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;

import mockwebserver3.MockResponse;
import okio.Buffer;
import ovh.karewan.knhttp.cache.LruBitmapCache;
import ovh.karewan.knhttp.internal.KnImageLoader;

public class ImageCacheTest extends BaseTest {

	@After
	public void restoreTheDefaultCacheSize() {
		KnHttp.setBitmapCacheMaxSize((int) Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 8));
	}

	@Test
	public void theImageLoaderAndItsCache_areNotCreatedUntilUsed() {
		server.enqueue(new MockResponse.Builder().body(new Buffer().write(new byte[] { 1, 2, 3 })).build());

		// None of these use the image loader
		KnHttp.setBitmapDecodeOptions(new BitmapFactory.Options());
		KnHttp.setBitmapCacheMaxSize(1024 * 1024);
		KnHttp.evictBitmap("key");
		KnHttp.evictAllBitmap();
		final Recorder<Bitmap> r = new Recorder<>();
		KnHttp.get(url("/image")).build().getAsBitmap(bitmapListener(r));
		r.await();

		assertNull(KnImageLoader.peek());
		assertNull(KnImageLoader.peekImageCache());
	}

	@Test
	public void theCache_isCreatedOnFirstUse_withTheConfiguredMaxSize() {
		KnHttp.setBitmapCacheMaxSize(2 * 1024 * 1024);

		final KnImageLoader loader = KnImageLoader.gi();
		assertNull(KnImageLoader.peekImageCache());

		final LruBitmapCache cache = (LruBitmapCache) loader.getImageCache();
		assertNotNull(KnImageLoader.peekImageCache());
		assertEquals(2 * 1024 * 1024, cache.maxSize());

		KnHttp.setBitmapCacheMaxSize(1024 * 1024);
		assertEquals(1024 * 1024, cache.maxSize());
	}

	@Test
	public void theCache_isReleasedWhenTheSystemNeedsMemory() {
		final Application application = ApplicationProvider.getApplicationContext();
		final Bitmap[] bitmaps = new Bitmap[4];
		for (int i = 0; i < bitmaps.length; i++) bitmaps[i] = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
		final int bitmapSize = bitmaps[0].getAllocationByteCount();

		KnHttp.setBitmapCacheMaxSize(bitmaps.length * bitmapSize);
		final LruBitmapCache cache = (LruBitmapCache) KnImageLoader.gi().getImageCache();
		for (int i = 0; i < bitmaps.length; i++) cache.putBitmap("bitmap-" + i, bitmaps[i]);
		assertEquals(bitmaps.length * bitmapSize, cache.size());

		// App in background: the cache is halved
		application.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
		assertEquals(2 * bitmapSize, cache.size());

		// The process may be killed: the cache is emptied
		application.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND);
		assertEquals(0, cache.size());
	}
}

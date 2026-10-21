package ovh.karewan.knhttp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.junit.Test;
import org.robolectric.annotation.GraphicsMode;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import mockwebserver3.MockResponse;
import okio.Buffer;
import ovh.karewan.knhttp.error.KnError;
import ovh.karewan.knhttp.internal.KnImageLoader;

@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageLoaderTest extends BaseTest {

	private static byte[] png() {
		final Bitmap bitmap = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888);
		bitmap.eraseColor(Color.RED);
		bitmap.setHasAlpha(false); // KnImageLoader decodes in RGB_565
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
		return out.toByteArray();
	}

	/**
	 * Records the callbacks of KnImageLoader
	 */
	private static final class Events implements KnImageLoader.ImageListener {
		final List<Bitmap> bitmaps = new CopyOnWriteArrayList<>();
		final List<Boolean> immediates = new CopyOnWriteArrayList<>();
		final CountDownLatch done = new CountDownLatch(1);
		volatile KnError error;

		@Override
		public void onResponse(KnImageLoader.ImageContainer response, boolean isImmediate) {
			bitmaps.add(response.getBitmap() != null ? response.getBitmap() : Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8));
			immediates.add(isImmediate);
			if (response.getBitmap() != null) done.countDown();
		}

		@Override
		public void onError(KnError knError) {
			error = knError;
			done.countDown();
		}
	}

	@Test
	public void sameUrl_isDownloadedOnce_thenServedFromTheMemoryCache() {
		server.enqueue(new MockResponse.Builder().body(new Buffer().write(png())).build());
		final String url = url("/image.png");

		final Events first = new Events();
		final Events second = new Events();
		KnImageLoader.gi().get(url, first, 0, 0);
		KnImageLoader.gi().get(url, second, 0, 0); // Same image in flight: no second request
		await(first.done);
		await(second.done);

		assertEquals(1, server.getRequestCount());
		assertEquals(2, first.bitmaps.size()); // Immediate placeholder call, then the image
		assertTrue(first.immediates.get(0));
		assertFalse(first.immediates.get(1));
		assertEquals(40, first.bitmaps.get(1).getWidth());
		assertEquals(Bitmap.Config.RGB_565, first.bitmaps.get(1).getConfig());
		assertEquals(2, second.bitmaps.size());

		// Cached: a single immediate call with the image
		assertTrue(KnImageLoader.gi().isCached(url, 0, 0));
		final Events third = new Events();
		KnImageLoader.gi().get(url, third, 0, 0);
		assertEquals(1, third.bitmaps.size());
		assertTrue(third.immediates.get(0));
		assertEquals(40, third.bitmaps.get(0).getWidth());
		assertEquals(1, server.getRequestCount());
	}

	@Test
	public void errors_reachTheListener() {
		server.enqueue(new MockResponse.Builder().code(404).build());

		final Events events = new Events();
		KnImageLoader.gi().get(url("/missing.png"), events, 0, 0);
		await(events.done);

		assertNotNull(events.error);
		assertEquals(KnError.Type.HTTP, events.error.getType());
		assertEquals(404, events.error.getErrorCode());
	}

	@Test
	public void cancellingTheLastContainer_cancelsTheRequest() {
		server.enqueue(new MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).body(new Buffer().write(png())).build());

		final Events events = new Events();
		final KnImageLoader.ImageContainer container = KnImageLoader.gi().get(url("/slow.png"), events, 0, 0);
		container.cancelRequest();
		idleMainLooper(300);

		// Only the immediate placeholder call: no image and no error after the cancellation
		assertEquals(1, events.bitmaps.size());
		assertNull(events.error);
		assertFalse(KnImageLoader.gi().isCached(url("/slow.png"), 0, 0));
		assertFalse(KnHttp.isRequestRunning("ImageRequestTag"));
	}
}

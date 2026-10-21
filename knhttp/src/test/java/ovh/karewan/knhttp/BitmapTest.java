package ovh.karewan.knhttp;

import static org.junit.Assert.assertEquals;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import org.junit.Test;
import org.robolectric.annotation.GraphicsMode;

import java.io.ByteArrayOutputStream;

import mockwebserver3.MockResponse;
import okio.Buffer;

@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BitmapTest extends BaseTest {

	private static byte[] png(int width, int height) {
		final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bitmap.eraseColor(Color.BLUE);
		// Opaque PNG (no alpha channel), otherwise the decoder can't use RGB_565
		bitmap.setHasAlpha(false);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
		return out.toByteArray();
	}

	@Test
	public void getAsBitmap_resizes_andAppliesTheConfigWhenResizing() {
		server.enqueue(new MockResponse.Builder().body(new Buffer().write(png(400, 200))).build());

		final Recorder<Bitmap> r = new Recorder<>();
		KnHttp.get(url("/image.png"))
				.setBitmapMaxWidth(100)
				.setBitmapMaxHeight(100)
				.setBitmapConfig(Bitmap.Config.RGB_565)
				.build()
				.getAsBitmap(bitmapListener(r));
		r.await().assertSuccess();

		assertEquals(100, r.result.getWidth());
		assertEquals(50, r.result.getHeight());
		// v4 ignored the config as soon as a max size was set
		assertEquals(Bitmap.Config.RGB_565, r.result.getConfig());
	}

	@Test
	public void parallelDecodes_withSharedOptions_areIndependent() {
		final int count = 8;
		final BitmapFactory.Options sharedOptions = new BitmapFactory.Options();
		@SuppressWarnings("unchecked") final Recorder<Bitmap>[] recorders = new Recorder[count];

		for (int i = 0; i < count; i++) {
			server.enqueue(new MockResponse.Builder().body(new Buffer().write(png(64 * (i + 1), 64))).build());
		}

		for (int i = 0; i < count; i++) {
			recorders[i] = new Recorder<>();
			KnHttp.get(url("/image/" + i)).setBitmapOptions(sharedOptions).build().getAsBitmap(bitmapListener(recorders[i]));
		}

		int totalWidth = 0;
		for (Recorder<Bitmap> r : recorders) {
			r.await().assertSuccess();
			assertEquals(64, r.result.getHeight());
			totalWidth += r.result.getWidth();
		}

		// Each image keeps its own size (the responses can arrive in any order)
		assertEquals(64 * (count * (count + 1) / 2), totalWidth);
	}
}

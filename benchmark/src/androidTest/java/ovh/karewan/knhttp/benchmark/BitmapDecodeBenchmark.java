package ovh.karewan.knhttp.benchmark;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.ImageView;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;

import ovh.karewan.knhttp.utils.Utils;

@RunWith(AndroidJUnit4.class)
public class BitmapDecodeBenchmark {

	@Rule
	public BenchmarkRule benchmarkRule = new BenchmarkRule();

	private static byte[] sJpeg;

	@BeforeClass
	public static void createImage() {
		final Bitmap bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888);
		bitmap.eraseColor(Color.rgb(30, 120, 200));
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
		bitmap.recycle();
		sJpeg = out.toByteArray();
	}

	@Test
	public void decodeAndResizeJpeg() {
		final BenchmarkState state = benchmarkRule.getState();

		while (state.keepRunning()) {
			final Bitmap bitmap = Utils.decodeBitmap(sJpeg, 400, 300, Bitmap.Config.RGB_565, null, ImageView.ScaleType.CENTER_INSIDE);
			if (bitmap != null) bitmap.recycle();
		}
	}
}

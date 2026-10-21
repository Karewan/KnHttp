package ovh.karewan.knhttp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import mockwebserver3.MockWebServer;
import okhttp3.Response;
import ovh.karewan.knhttp.common.KnSettings;
import ovh.karewan.knhttp.error.KnError;
import ovh.karewan.knhttp.interfaces.BitmapRequestListener;
import ovh.karewan.knhttp.interfaces.DownloadListener;
import ovh.karewan.knhttp.interfaces.JSONArrayRequestListener;
import ovh.karewan.knhttp.interfaces.JSONObjectRequestListener;
import ovh.karewan.knhttp.interfaces.OkHttpResponseListener;
import ovh.karewan.knhttp.interfaces.ParsedRequestListener;
import ovh.karewan.knhttp.interfaces.StringRequestListener;

@RunWith(RobolectricTestRunner.class)
public abstract class BaseTest {
	protected static final long TIMEOUT_MS = 10_000;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	protected MockWebServer server;

	@Before
	public void setUpServer() throws IOException {
		server = new MockWebServer();
		server.start();
		// MockWebServer is plain HTTP
		initKnHttp(new KnSettings.Builder().setAllowCleartextTraffic(true));
	}

	@After
	public void tearDownServer() {
		KnHttp.shutDown();
		server.close();
	}

	protected void initKnHttp(@NonNull KnSettings.Builder settings) {
		KnHttp.init(ApplicationProvider.getApplicationContext(), settings.build());
	}

	protected String url(String path) {
		return server.url(path).toString();
	}

	/**
	 * URL template not encoded by HttpUrl, the way users write them: "http://host/items/{id}"
	 */
	protected String rawUrl(String path) {
		return "http://" + server.getHostName() + ":" + server.getPort() + path;
	}

	/**
	 * Waits for the latch while running the main looper (where the callbacks are delivered)
	 */
	protected static void await(CountDownLatch latch) {
		final long deadline = System.currentTimeMillis() + TIMEOUT_MS;

		try {
			while (latch.getCount() > 0) {
				shadowOf(Looper.getMainLooper()).idle();
				if (System.currentTimeMillis() > deadline) fail("Timeout while waiting for the callback");
				latch.await(5, TimeUnit.MILLISECONDS);
			}
		} catch (InterruptedException e) {
			throw new AssertionError(e);
		}

		shadowOf(Looper.getMainLooper()).idle();
	}

	/**
	 * Runs the main looper during the given time, to catch unexpected extra callbacks
	 */
	protected static void idleMainLooper(long durationMs) {
		final long end = System.currentTimeMillis() + durationMs;

		while (System.currentTimeMillis() < end) {
			shadowOf(Looper.getMainLooper()).idle();
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				throw new AssertionError(e);
			}
		}
	}

	/**
	 * Records the callbacks of a request
	 */
	protected static final class Recorder<T> {
		public final CountDownLatch latch = new CountDownLatch(1);
		public final AtomicInteger calls = new AtomicInteger();
		public final List<long[]> progress = new CopyOnWriteArrayList<>();
		public volatile T result;
		public volatile Response response;
		public volatile KnError error;
		public volatile Thread thread;

		void onResult(T result, Response response) {
			this.result = result;
			this.response = response;
			this.thread = Thread.currentThread();
			calls.incrementAndGet();
			latch.countDown();
		}

		void onFailure(KnError error) {
			this.error = error;
			this.thread = Thread.currentThread();
			calls.incrementAndGet();
			latch.countDown();
		}

		public Recorder<T> await() {
			BaseTest.await(latch);
			return this;
		}

		public Recorder<T> awaitOffMainThread() {
			try {
				if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) fail("Timeout while waiting for the callback");
			} catch (InterruptedException e) {
				throw new AssertionError(e);
			}
			return this;
		}

		public void assertSuccess() {
			if (error != null) throw new AssertionError("Unexpected error: " + error.getErrorDetail() + " / " + error.getErrorCode(), error);
			assertEquals(1, calls.get());
		}

		public void assertError() {
			if (error == null) fail("An error was expected, got: " + result);
			assertEquals(1, calls.get());
		}

		public void onProgress(long current, long total) {
			progress.add(new long[] { current, total });
		}
	}

	@SuppressWarnings("unchecked")
	protected static StringRequestListener stringListener(Recorder<?> recorder) {
		final Recorder<String> r = (Recorder<String>) recorder;
		return new StringRequestListener() {
			@Override
			public void onResponse(@NonNull String str, @NonNull Response okHttpRes) {
				r.onResult(str, okHttpRes);
			}

			@Override
			public void onError(@NonNull KnError err) {
				r.onFailure(err);
			}
		};
	}

	@SuppressWarnings("unchecked")
	protected static JSONObjectRequestListener jsonObjectListener(Recorder<?> recorder) {
		final Recorder<JSONObject> r = (Recorder<JSONObject>) recorder;
		return new JSONObjectRequestListener() {
			@Override
			public void onResponse(@NonNull JSONObject obj, @NonNull Response okHttpRes) {
				r.onResult(obj, okHttpRes);
			}

			@Override
			public void onError(@NonNull KnError err) {
				r.onFailure(err);
			}
		};
	}

	@SuppressWarnings("unchecked")
	protected static JSONArrayRequestListener jsonArrayListener(Recorder<?> recorder) {
		final Recorder<JSONArray> r = (Recorder<JSONArray>) recorder;
		return new JSONArrayRequestListener() {
			@Override
			public void onResponse(@NonNull JSONArray arr, @NonNull Response okHttpRes) {
				r.onResult(arr, okHttpRes);
			}

			@Override
			public void onError(@NonNull KnError err) {
				r.onFailure(err);
			}
		};
	}

	protected static <T> ParsedRequestListener<T> parsedListener(Recorder<T> r) {
		return new ParsedRequestListener<T>() {
			@Override
			public void onResponse(@NonNull T obj, @NonNull Response okHttpRes) {
				r.onResult(obj, okHttpRes);
			}

			@Override
			public void onError(@NonNull KnError err) {
				r.onFailure(err);
			}
		};
	}

	protected static OkHttpResponseListener okHttpListener(Recorder<String> r) {
		return new OkHttpResponseListener() {
			@Override
			public void onResponse(@NonNull Response okHttpRes) {
				// The listener owns the response: it must be able to read and close it
				try (Response res = okHttpRes) {
					r.onResult(res.body().string(), res);
				} catch (IOException e) {
					r.onFailure(new KnError(e));
				}
			}

			@Override
			public void onError(@NonNull KnError err) {
				r.onFailure(err);
			}
		};
	}

	protected static DownloadListener downloadListener(Recorder<String> r) {
		return new DownloadListener() {
			@Override
			public void onDownloadComplete(@NonNull Response okHttpRes) {
				r.onResult("done", okHttpRes);
			}

			@Override
			public void onError(@NonNull KnError err) {
				r.onFailure(err);
			}
		};
	}

	protected static BitmapRequestListener bitmapListener(Recorder<Bitmap> r) {
		return new BitmapRequestListener() {
			@Override
			public void onResponse(@NonNull Bitmap bitmap, @NonNull Response okHttpRes) {
				r.onResult(bitmap, okHttpRes);
			}

			@Override
			public void onError(@NonNull KnError err) {
				r.onFailure(err);
			}
		};
	}
}

package ovh.karewan.knhttp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.robolectric.shadows.ShadowLog;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okio.Buffer;
import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnSettings;
import ovh.karewan.knhttp.error.KnError;
import ovh.karewan.knhttp.interceptors.HttpLoggingInterceptor;

/**
 * Races, limits and the less common options
 */
public class RobustnessTest extends BaseTest {

	@Test
	public void cancellationsRacingWithTheResponses_deliverExactlyOneCallback() {
		server.setDispatcher(new Dispatcher() {
			@NonNull
			@Override
			public MockResponse dispatch(@NonNull RecordedRequest request) {
				return new MockResponse.Builder().body("ok").build();
			}
		});

		final ExecutorService cancellers = Executors.newFixedThreadPool(4);
		final Random random = new Random(42);
		final List<Recorder<String>> recorders = new ArrayList<>();

		for (int i = 0; i < 200; i++) {
			final Recorder<String> r = new Recorder<>();
			final KnRequest request = KnHttp.get(url("/r" + i)).build();
			request.getAsString(stringListener(r));

			final long delayNs = random.nextInt(3000) * 1000L;
			cancellers.execute(() -> {
				LockSupport.parkNanos(delayNs);
				request.cancel(true);
			});
			recorders.add(r);
		}

		for (Recorder<String> r : recorders) r.await();
		cancellers.shutdown();
		idleMainLooper(200);

		for (Recorder<String> r : recorders) {
			assertEquals(1, r.calls.get());
			if (r.error != null) assertTrue(String.valueOf(r.error), r.error.isCancellation());
		}
	}

	@Test
	public void isRequestRunning_whileTheRequestRuns() {
		server.enqueue(new MockResponse.Builder().headersDelay(300, TimeUnit.MILLISECONDS).body("slow").build());

		final Recorder<String> r = new Recorder<>();
		KnHttp.get(url("/slow")).setTag("tag").build().getAsString(stringListener(r));

		final long deadline = System.currentTimeMillis() + 2000;
		while (!KnHttp.isRequestRunning("tag") && System.currentTimeMillis() < deadline) Thread.yield();
		assertTrue(KnHttp.isRequestRunning("tag"));
		assertFalse(KnHttp.isRequestRunning("other"));

		r.await().assertSuccess();
		assertFalse(KnHttp.isRequestRunning("tag"));
	}

	@Test
	public void aCancelledSynchronousRequest_isNotSent() {
		final KnRequest request = KnHttp.get(url("/x")).build();
		request.cancel(false);

		assertEquals(KnError.Type.CANCELLED, request.executeForString().getError().getType());
		assertEquals(0, server.getRequestCount());
	}

	private MockResponse slowDownload() {
		return new MockResponse.Builder()
				.body(new Buffer().write(new byte[256 * 1024]))
				.throttleBody(16 * 1024, 20, TimeUnit.MILLISECONDS)
				.build();
	}

	@Test
	public void cancel_isIgnoredPastTheThreshold_butForceCancelStillWorks() throws Exception {
		server.enqueue(slowDownload());
		server.enqueue(slowDownload());
		final File dir = tmp.newFolder("downloads");

		final Recorder<String> kept = new Recorder<>();
		final KnRequest request = KnHttp.download(url("/a"), dir.getPath(), "a.bin").setPercentageThresholdForCancelling(50).build();
		request.setDownloadProgressListener((done, total) -> {
			if (done * 100 / total >= 60) request.cancel(false);
		}).startDownload(downloadListener(kept));
		kept.await().assertSuccess();
		assertEquals(256 * 1024, new File(dir, "a.bin").length());

		final Recorder<String> forced = new Recorder<>();
		final KnRequest forcedRequest = KnHttp.download(url("/b"), dir.getPath(), "b.bin").setPercentageThresholdForCancelling(50).build();
		forcedRequest.setDownloadProgressListener((done, total) -> {
			if (done * 100 / total >= 60) forcedRequest.cancel(true);
		}).startDownload(downloadListener(forced));
		forced.await().assertError();
		assertTrue(forced.error.isCancellation());
		assertFalse(new File(dir, "b.bin").exists());
	}

	@Test
	public void parallelBursts_keepTheirConnectionsForReuse() {
		final int count = 10;
		final List<Recorder<String>> recorders = new ArrayList<>();
		for (int i = 0; i < count; i++) server.enqueue(new MockResponse.Builder().headersDelay(300, TimeUnit.MILLISECONDS).body("ok").build());

		for (int i = 0; i < count; i++) {
			final Recorder<String> r = new Recorder<>();
			KnHttp.get(url("/burst")).build().getAsString(stringListener(r));
			recorders.add(r);
		}
		for (Recorder<String> r : recorders) r.await().assertSuccess();

		// HTTP/1.1: one connection per parallel request, all kept (OkHttp's default pool keeps 5)
		assertEquals(count, KnHttp.getOkHttpClient().connectionPool().idleConnectionCount());
	}

	@Test
	public void maxIdleConnections_isConfigurable() {
		assertEquals(16, new KnSettings.Builder().build().getMaxIdleConnections());
		assertEquals(0, new KnSettings.Builder().setMaxIdleConnections(0).build().getMaxIdleConnections());
		assertThrows(IllegalArgumentException.class, () -> new KnSettings.Builder().setMaxIdleConnections(-1));
	}

	@Test
	public void bodyLogging_keepsTheResponsesAndTheUploadsIntact() throws Exception {
		KnHttp.enableLogging(HttpLoggingInterceptor.Level.BODY);
		final int bigSize = 2 * 1024 * 1024;
		server.enqueue(new MockResponse.Builder().body("{\"logged\":true}").build());
		server.enqueue(new MockResponse.Builder().body(new Buffer().write(new byte[bigSize])).build());
		server.enqueue(new MockResponse.Builder().body("uploaded").build());

		assertTrue(KnHttp.get(url("/json")).build().executeForJSONObject().getResult().getBooleanValue("logged"));
		// Larger than the logging limit: not held in memory by the logger, but fully delivered
		assertEquals(bigSize, KnHttp.get(url("/big")).build().executeForString().getResult().length());

		final File file = tmp.newFile("upload.bin");
		Files.write(file.toPath(), new byte[64 * 1024]);
		final Recorder<String> upload = new Recorder<>();
		KnHttp.upload(url("/upload")).addMultipartFile("file", file).build()
				.setUploadProgressListener(upload::onProgress)
				.getAsString(stringListener(upload));
		upload.await().assertSuccess();

		// Logging the multipart body would report a first fake 100% progress
		int completeEvents = 0;
		for (long[] progress : upload.progress) if (progress[0] == progress[1]) completeEvents++;
		assertEquals(1, completeEvents);

		final StringBuilder logs = new StringBuilder();
		for (ShadowLog.LogItem item : ShadowLog.getLogsForTag("KnHttp")) logs.append(item.msg).append('\n');
		assertTrue(logs.toString(), logs.toString().contains("{\"logged\":true}"));
		assertTrue(logs.toString(), logs.toString().contains("omitted"));
	}

	@Test
	public void perRequestClients_areLoggedToo() {
		KnHttp.enableLogging(HttpLoggingInterceptor.Level.BASIC);
		server.enqueue(new MockResponse.Builder().body("ok").build());
		ShadowLog.clear();

		final okhttp3.OkHttpClient custom = new okhttp3.OkHttpClient.Builder().build();
		assertTrue(KnHttp.get(url("/custom")).setOkHttpClient(custom).build().executeForString().isSuccess());

		boolean logged = false;
		for (ShadowLog.LogItem item : ShadowLog.getLogsForTag("KnHttp")) logged |= item.msg.contains("--> GET " + url("/custom"));
		assertTrue(logged);
	}
}

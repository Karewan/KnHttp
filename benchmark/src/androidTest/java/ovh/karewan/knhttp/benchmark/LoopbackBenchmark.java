package ovh.karewan.knhttp.benchmark;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ServerSocketFactory;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import ovh.karewan.knhttp.KnHttp;
import ovh.karewan.knhttp.common.KnSettings;
import ovh.karewan.knhttp.error.KnError;
import ovh.karewan.knhttp.interfaces.DownloadListener;
import ovh.karewan.knhttp.interfaces.StringRequestListener;

/**
 * End-to-end benchmarks against a local server: KnHttp overhead, parsing, concurrency and progress
 */
@RunWith(AndroidJUnit4.class)
public class LoopbackBenchmark {
	private static final int PARALLEL_REQUESTS = 32;

	@Rule
	public BenchmarkRule benchmarkRule = new BenchmarkRule();

	private MockWebServer server;
	private Context context;

	public static class Item {
		public int id;
		public String title;
		public String body;
		public boolean published;
	}

	private static String jsonList(int count) {
		final StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < count; i++) {
			if (i > 0) sb.append(',');
			sb.append("{\"id\":").append(i).append(",\"title\":\"Title ").append(i)
					.append("\",\"body\":\"Lorem ipsum dolor sit amet, consectetur adipiscing elit\",\"published\":true}");
		}
		return sb.append(']').toString();
	}

	@Before
	public void setUp() throws IOException {
		context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		final String json = jsonList(200); // ~20 KB
		final Buffer bigBody = new Buffer().write(new byte[4 * 1024 * 1024]);

		server = new MockWebServer();
		server.setServerSocketFactory(new NoDelayServerSocketFactory());
		server.setDispatcher(new Dispatcher() {
			@NonNull
			@Override
			public MockResponse dispatch(@NonNull RecordedRequest request) {
				switch (request.getTarget()) {
					case "/json":
						return new MockResponse.Builder().body(json).build();
					case "/delay":
						return new MockResponse.Builder().bodyDelay(20, TimeUnit.MILLISECONDS).body("ok").build();
					case "/big":
						return new MockResponse.Builder().body(bigBody.clone()).build();
					default:
						return new MockResponse.Builder().body("ok").build();
				}
			}
		});
		server.start();

		KnHttp.init(context, new KnSettings.Builder().setAllowCleartextTraffic(true).build());
	}

	@After
	public void tearDown() {
		KnHttp.shutDown();
		server.close();
	}

	/**
	 * Real servers disable Nagle's algorithm (TCP_NODELAY), MockWebServer doesn't: its response is written in two
	 * parts, the second one waiting for the delayed ACK of the client (~40 ms per request on Android).
	 */
	private static final class NoDelayServerSocketFactory extends ServerSocketFactory {
		@Override
		public ServerSocket createServerSocket() throws IOException {
			return new NoDelayServerSocket();
		}

		@Override
		public ServerSocket createServerSocket(int port) throws IOException {
			return createServerSocket(port, 50, null);
		}

		@Override
		public ServerSocket createServerSocket(int port, int backlog) throws IOException {
			return createServerSocket(port, backlog, null);
		}

		@Override
		public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
			final ServerSocket serverSocket = new NoDelayServerSocket();
			serverSocket.bind(new InetSocketAddress(ifAddress, port), backlog);
			return serverSocket;
		}
	}

	private static final class NoDelayServerSocket extends ServerSocket {
		NoDelayServerSocket() throws IOException {
			super();
		}

		@Override
		public Socket accept() throws IOException {
			final Socket socket = super.accept();
			socket.setTcpNoDelay(true);
			return socket;
		}
	}

	@Test
	public void syncGetString() {
		final String url = server.url("/small").toString();
		final BenchmarkState state = benchmarkRule.getState();

		while (state.keepRunning()) {
			assertTrue(KnHttp.get(url).build().executeForString().isSuccess());
		}
	}

	@Test
	public void syncGetString_withPerRequestTimeout() {
		final String url = server.url("/small").toString();
		final BenchmarkState state = benchmarkRule.getState();

		while (state.keepRunning()) {
			assertTrue(KnHttp.get(url).setReadTimeout(5, TimeUnit.SECONDS).build().executeForString().isSuccess());
		}
	}

	@Test
	public void syncGetObjectList_20KB() {
		final String url = server.url("/json").toString();
		final BenchmarkState state = benchmarkRule.getState();

		while (state.keepRunning()) {
			final List<Item> items = KnHttp.get(url).build().executeForObjectList(Item.class).getResult();
			assertTrue(items.size() == 200);
		}
	}

	/**
	 * 32 requests at once with the default priority, each one waiting 20 ms for the server.
	 * v4 (default priority IMMEDIATE, 2 threads): ~16 x 20 ms. v5: all start at once (limit 64).
	 */
	@Test
	public void parallelAsyncRequests_defaultPriority() throws InterruptedException {
		final String url = server.url("/delay").toString();
		final BenchmarkState state = benchmarkRule.getState();

		while (state.keepRunning()) {
			final CountDownLatch latch = new CountDownLatch(PARALLEL_REQUESTS);
			final StringRequestListener listener = new StringRequestListener() {
				@Override
				public void onResponse(@NonNull String str, @NonNull Response okHttpRes) {
					latch.countDown();
				}

				@Override
				public void onError(@NonNull KnError err) {
					latch.countDown();
				}
			};

			for (int i = 0; i < PARALLEL_REQUESTS; i++) KnHttp.get(url).build().getAsString(listener);
			assertTrue(latch.await(30, TimeUnit.SECONDS));
		}
	}

	/**
	 * Same as above without server delay: the cost of KnHttp and of the connections for 32 parallel requests
	 */
	@Test
	public void parallelAsyncRequests_noServerDelay() throws InterruptedException {
		runParallelRequests(server.url("/small").toString());
	}

	/**
	 * Baseline: plain OkHttp asynchronous calls (dispatcher allowing 64 requests per host), same client and server
	 */
	@Test
	public void parallelRequests_okHttpEnqueueBaseline() throws InterruptedException {
		final okhttp3.Dispatcher dispatcher = new okhttp3.Dispatcher();
		dispatcher.setMaxRequests(64);
		dispatcher.setMaxRequestsPerHost(64);
		final OkHttpClient client = KnHttp.getOkHttpClient().newBuilder().dispatcher(dispatcher).build();
		final Request request = new Request.Builder().url(server.url("/delay")).build();
		final BenchmarkState state = benchmarkRule.getState();

		while (state.keepRunning()) {
			final CountDownLatch latch = new CountDownLatch(PARALLEL_REQUESTS);
			final Callback callback = new Callback() {
				@Override
				public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
					try (Response res = response) {
						res.body().string();
					}
					latch.countDown();
				}

				@Override
				public void onFailure(@NonNull Call call, @NonNull IOException e) {
					latch.countDown();
				}
			};

			for (int i = 0; i < PARALLEL_REQUESTS; i++) client.newCall(request).enqueue(callback);
			assertTrue(latch.await(30, TimeUnit.SECONDS));
		}
	}

	private void runParallelRequests(String url) throws InterruptedException {
		final BenchmarkState state = benchmarkRule.getState();

		while (state.keepRunning()) {
			final CountDownLatch latch = new CountDownLatch(PARALLEL_REQUESTS);
			final StringRequestListener listener = new StringRequestListener() {
				@Override
				public void onResponse(@NonNull String str, @NonNull Response okHttpRes) {
					latch.countDown();
				}

				@Override
				public void onError(@NonNull KnError err) {
					latch.countDown();
				}
			};

			for (int i = 0; i < PARALLEL_REQUESTS; i++) KnHttp.get(url).build().getAsString(listener);
			assertTrue(latch.await(30, TimeUnit.SECONDS));
		}
	}

	/**
	 * 4 MB download with a progress listener: v4 posted one main thread message per 8 KiB segment
	 */
	@Test
	public void download4MB_withProgressListener() throws InterruptedException {
		final String url = server.url("/big").toString();
		final File dir = context.getCacheDir();
		final BenchmarkState state = benchmarkRule.getState();

		while (state.keepRunning()) {
			final CountDownLatch latch = new CountDownLatch(1);

			KnHttp.download(url, dir.getAbsolutePath(), "benchmark.bin")
					.build()
					.setDownloadProgressListener((downloaded, total) -> {})
					.startDownload(new DownloadListener() {
						@Override
						public void onDownloadComplete(@NonNull Response okHttpRes) {
							latch.countDown();
						}

						@Override
						public void onError(@NonNull KnError err) {
							latch.countDown();
						}
					});

			assertTrue(latch.await(30, TimeUnit.SECONDS));
		}
	}
}

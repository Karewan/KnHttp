package ovh.karewan.knhttp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Looper;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import org.junit.Test;

import java.net.UnknownServiceException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import ovh.karewan.knhttp.common.KnConstants;
import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnSettings;
import ovh.karewan.knhttp.common.Method;
import okhttp3.Response;
import ovh.karewan.knhttp.error.KnError;
import ovh.karewan.knhttp.interfaces.OkHttpResponseListener;
import ovh.karewan.knhttp.internal.KnEngine;

public class AsyncRequestTest extends BaseTest {

	public static class PostItem {
		public int userId;
		public int id;
		public String title;
	}

	@Test
	public void getAsString_deliversTheBodyOnTheMainThread() {
		server.enqueue(new MockResponse.Builder().body("hello").build());

		final Recorder<String> r = new Recorder<>();
		KnHttp.get(url("/hello")).build().getAsString(stringListener(r));
		r.await();

		r.assertSuccess();
		assertEquals("hello", r.result);
		assertEquals(200, r.response.code());
		assertSame(Looper.getMainLooper().getThread(), r.thread);
	}

	@Test
	public void getAsJSONObject_andJSONArray_parseTheBody() {
		server.enqueue(new MockResponse.Builder().body("{\"id\":1,\"title\":\"é\"}").build());
		server.enqueue(new MockResponse.Builder().body("[1,2,3]").build());

		final Recorder<JSONObject> object = new Recorder<>();
		KnHttp.get(url("/object")).build().getAsJSONObject(jsonObjectListener(object));
		object.await().assertSuccess();
		assertEquals(1, object.result.getIntValue("id"));
		assertEquals("é", object.result.getString("title"));

		final Recorder<JSONArray> array = new Recorder<>();
		KnHttp.get(url("/array")).build().getAsJSONArray(jsonArrayListener(array));
		array.await().assertSuccess();
		assertEquals(3, array.result.size());
	}

	@Test
	public void getAsObject_andObjectList_mapThePojos() {
		server.enqueue(new MockResponse.Builder().body("{\"userId\":2,\"id\":3,\"title\":\"foo\"}").build());
		server.enqueue(new MockResponse.Builder().body("[{\"id\":1},{\"id\":2}]").build());

		final Recorder<PostItem> item = new Recorder<>();
		KnHttp.get(url("/post")).build().getAsObject(PostItem.class, parsedListener(item));
		item.await().assertSuccess();
		assertEquals(2, item.result.userId);
		assertEquals("foo", item.result.title);

		final Recorder<List<PostItem>> list = new Recorder<>();
		KnHttp.get(url("/posts")).build().getAsObjectList(PostItem.class, parsedListener(list));
		list.await().assertSuccess();
		assertEquals(2, list.result.size());
		assertEquals(2, list.result.get(1).id);
	}

	@Test
	public void invalidJson_deliversAParseError() {
		server.enqueue(new MockResponse.Builder().body("<html>").build());

		final Recorder<JSONObject> r = new Recorder<>();
		KnHttp.get(url("/html")).build().getAsJSONObject(jsonObjectListener(r));
		r.await().assertError();
		assertEquals(KnConstants.PARSE_ERROR, r.error.getErrorDetail());
	}

	@Test
	public void serverError_exposesTheCodeAndTheBody() {
		server.enqueue(new MockResponse.Builder().code(404).body("not found").build());

		final Recorder<String> r = new Recorder<>();
		KnHttp.get(url("/missing")).build().getAsString(stringListener(r));
		r.await().assertError();

		assertEquals(404, r.error.getErrorCode());
		assertEquals("not found", r.error.getErrorBody());
		assertEquals(KnConstants.RESPONSE_FROM_SERVER_ERROR, r.error.getErrorDetail());
		assertEquals(404, r.error.getResponse().code());
	}

	@Test
	public void getAsOkHttpResponse_givesAnOpenResponseToTheListener() {
		server.enqueue(new MockResponse.Builder().body("payload").build());
		server.enqueue(new MockResponse.Builder().code(500).body("boom").build());

		final Recorder<String> ok = new Recorder<>();
		KnHttp.get(url("/raw")).build().getAsOkHttpResponse(okHttpListener(ok));
		ok.await().assertSuccess();
		assertEquals("payload", ok.result);

		// As before, any HTTP status is delivered to onResponse()
		final Recorder<String> error = new Recorder<>();
		KnHttp.get(url("/raw-error")).build().getAsOkHttpResponse(okHttpListener(error));
		error.await().assertSuccess();
		assertEquals("boom", error.result);
		assertEquals(500, error.response.code());
	}

	@Test
	public void getAsOkHttpResponse_onTheMainThread_releasesTheConnectionBeforeTheCallback() {
		server.enqueue(new MockResponse.Builder().body("buffered").build());

		final Recorder<String> r = new Recorder<>();
		KnHttp.get(url("/buffered")).build().getAsOkHttpResponse(new OkHttpResponseListener() {
			@Override
			public void onResponse(@NonNull Response okHttpRes) {
				// Not closed on purpose: the body is already in memory, nothing leaks
				r.result = String.valueOf(KnHttp.getOkHttpClient().connectionPool().idleConnectionCount());
				r.calls.incrementAndGet();
				r.latch.countDown();
			}

			@Override
			public void onError(@NonNull KnError err) {
				r.error = err;
				r.latch.countDown();
			}
		});
		r.await().assertSuccess();

		assertEquals("1", r.result);
	}

	@Test
	public void getAsOkHttpResponse_onACustomExecutor_streamsTheBody() {
		server.enqueue(new MockResponse.Builder().body("streamed").build());
		final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "custom-delivery"));

		try {
			final Recorder<String> r = new Recorder<>();
			KnHttp.get(url("/streamed")).setExecutor(executor).build().getAsOkHttpResponse(okHttpListener(r));
			r.awaitOffMainThread().assertSuccess();
			assertEquals("streamed", r.result);
			assertEquals("custom-delivery", r.thread.getName());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	public void errors_areDeliveredOnTheCustomExecutor() {
		server.enqueue(new MockResponse.Builder().code(500).build());
		final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "custom-delivery"));

		try {
			final Recorder<String> r = new Recorder<>();
			KnHttp.get(url("/error")).setExecutor(executor).build().getAsString(stringListener(r));
			r.awaitOffMainThread().assertError();
			assertEquals("custom-delivery", r.thread.getName());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	public void successAndProgress_areDeliveredOnTheCustomExecutor() throws Exception {
		server.enqueue(new MockResponse.Builder().body("uploaded").build());
		final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "custom-delivery"));
		final java.io.File file = tmp.newFile("upload.bin");
		java.nio.file.Files.write(file.toPath(), new byte[32 * 1024]);
		final List<String> progressThreads = new java.util.concurrent.CopyOnWriteArrayList<>();

		try {
			final Recorder<String> r = new Recorder<>();
			KnHttp.upload(url("/upload")).addMultipartFile("file", file).setExecutor(executor).build()
					.setUploadProgressListener((uploaded, total) -> progressThreads.add(Thread.currentThread().getName()))
					.getAsString(stringListener(r));
			r.awaitOffMainThread().assertSuccess();

			assertEquals("custom-delivery", r.thread.getName());
			assertFalse(progressThreads.isEmpty());
			for (String name : progressThreads) assertEquals("custom-delivery", name);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	public void cancel_fromTheMainThread_deliversExactlyOneSynchronousCancellation() {
		server.enqueue(new MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).body("late").build());

		final Recorder<String> r = new Recorder<>();
		final KnRequest request = KnHttp.get(url("/slow")).build();
		request.getAsString(stringListener(r));
		request.cancel(false);

		// Synchronous, like in v4, when cancelled from the delivery thread
		assertEquals(1, r.calls.get());
		assertNotNull(r.error);
		assertTrue(r.error.isCancellation());
		assertEquals(KnConstants.REQUEST_CANCELLED_ERROR, r.error.getErrorDetail());

		idleMainLooper(500);
		assertEquals(1, r.calls.get());
		assertNull(r.result);
		assertFalse(request.isRunning());
	}

	@Test
	public void cancel_byTag_onlyCancelsTheMatchingRequests() {
		// Routed by path: the cancelled request may never reach the server
		server.setDispatcher(new Dispatcher() {
			@NonNull
			@Override
			public MockResponse dispatch(@NonNull RecordedRequest request) {
				if ("/slow".equals(request.getTarget())) return new MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).body("late").build();
				return new MockResponse.Builder().body("fast").build();
			}
		});

		final Recorder<String> cancelled = new Recorder<>();
		KnHttp.get(url("/slow")).setTag("cancel-me").build().getAsString(stringListener(cancelled));
		assertTrue(waitUntilRunning("cancel-me"));
		KnHttp.cancel("cancel-me");

		final Recorder<String> kept = new Recorder<>();
		KnHttp.get(url("/fast")).setTag("keep-me").build().getAsString(stringListener(kept));

		cancelled.await().assertError();
		assertTrue(cancelled.error.isCancellation());
		kept.await().assertSuccess();
		assertEquals("fast", kept.result);

		idleMainLooper(300);
		assertEquals(1, cancelled.calls.get());
	}

	@Test
	public void startingARequestTwice_throws() {
		server.enqueue(new MockResponse.Builder().body("once").build());

		final Recorder<String> r = new Recorder<>();
		final KnRequest request = KnHttp.get(url("/once")).build();
		request.getAsString(stringListener(r));

		try {
			request.getAsString(stringListener(new Recorder<>()));
			fail("IllegalStateException expected");
		} catch (IllegalStateException expected) {
			// OK
		}

		r.await().assertSuccess();
	}

	@Test
	public void invalidUrl_deliversAnInvalidRequestError() {
		final Recorder<String> r = new Recorder<>();
		KnHttp.get("not a valid url").build().getAsString(stringListener(r));
		r.await().assertError();

		assertEquals(KnConstants.INVALID_REQUEST_ERROR, r.error.getErrorDetail());
		assertEquals(0, server.getRequestCount());
	}

	@Test
	public void invalidHeader_deliversAnInvalidRequestError() {
		final Recorder<String> r = new Recorder<>();
		KnHttp.get(url("/")).addHeaders("X-Bad", "line\nbreak").build().getAsString(stringListener(r));
		r.await().assertError();

		assertEquals(KnConstants.INVALID_REQUEST_ERROR, r.error.getErrorDetail());
	}

	@Test
	public void pathParameters_arePercentEncoded() throws Exception {
		server.enqueue(new MockResponse.Builder().body("ok").build());

		final Recorder<String> r = new Recorder<>();
		KnHttp.get(rawUrl("/items/{id}/details"))
				.addPathParameter("id", "a b/c?d#é")
				.build()
				.getAsString(stringListener(r));
		r.await().assertSuccess();

		assertEquals("/items/a%20b%2Fc%3Fd%23%C3%A9/details", server.takeRequest().getTarget());
	}

	@Test
	public void encodedPathParameters_areInsertedAsIs() throws Exception {
		server.enqueue(new MockResponse.Builder().body("ok").build());

		final Recorder<String> r = new Recorder<>();
		KnHttp.get(rawUrl("/{path}")).addEncodedPathParameter("path", "a/b").build().getAsString(stringListener(r));
		r.await().assertSuccess();

		assertEquals("/a/b", server.takeRequest().getTarget());
	}

	@Test
	public void queryParameters_keepTheirInsertionOrder() throws Exception {
		server.enqueue(new MockResponse.Builder().body("ok").build());

		final Recorder<String> r = new Recorder<>();
		KnHttp.get(url("/search"))
				.addQueryParameter("z", "1")
				.addQueryParameter("a", "2 3")
				.addQueryParameter("m", "4")
				.addQueryParameter("z", "5")
				.build()
				.getAsString(stringListener(r));
		r.await().assertSuccess();

		assertEquals("/search?z=1&z=5&a=2%203&m=4", server.takeRequest().getTarget());
	}

	@Test
	public void userAgent_headerWinsOverRequestWhichWinsOverGlobal() throws Exception {
		for (int i = 0; i < 4; i++) server.enqueue(new MockResponse.Builder().body("ok").build());

		final Recorder<String> r1 = new Recorder<>();
		KnHttp.get(url("/1")).build().getAsString(stringListener(r1));
		r1.await().assertSuccess();
		assertEquals("KnHttp/" + BuildConfig.VERSION_NAME, server.takeRequest().getHeaders().get("User-Agent"));

		KnHttp.setUserAgent("Global/1");
		final Recorder<String> r2 = new Recorder<>();
		KnHttp.get(url("/2")).build().getAsString(stringListener(r2));
		r2.await().assertSuccess();
		assertEquals("Global/1", server.takeRequest().getHeaders().get("User-Agent"));

		final Recorder<String> r3 = new Recorder<>();
		KnHttp.get(url("/3")).setUserAgent("Request/1").build().getAsString(stringListener(r3));
		r3.await().assertSuccess();
		assertEquals("Request/1", server.takeRequest().getHeaders().get("User-Agent"));

		final Recorder<String> r4 = new Recorder<>();
		KnHttp.get(url("/4")).setUserAgent("Request/1").addHeaders("user-agent", "Header/1").build().getAsString(stringListener(r4));
		r4.await().assertSuccess();
		final RecordedRequest recorded = server.takeRequest();
		assertEquals("Header/1", recorded.getHeaders().get("User-Agent"));
		assertEquals(1, recorded.getHeaders().values("User-Agent").size());
	}

	@Test
	public void jsonObjectBody_isSentAsUtf8Json() throws Exception {
		server.enqueue(new MockResponse.Builder().body("{}").build());

		final JSONObject json = new JSONObject();
		json.put("title", "é \"quoted\"");
		json.put("nested", new JSONObject().fluentPut("list", new JSONArray().fluentAdd(1).fluentAdd("two")));

		final Recorder<JSONObject> r = new Recorder<>();
		KnHttp.post(url("/json")).addJSONObjectBody(json).build().getAsJSONObject(jsonObjectListener(r));
		r.await().assertSuccess();

		final RecordedRequest recorded = server.takeRequest();
		assertEquals("application/json; charset=utf-8", recorded.getHeaders().get("Content-Type"));
		assertArrayEquals(json.toString().getBytes(StandardCharsets.UTF_8), recorded.getBody().toByteArray());
	}

	@Test
	public void objectBody_serializesThePojo() throws Exception {
		server.enqueue(new MockResponse.Builder().body("{}").build());

		final PostItem item = new PostItem();
		item.id = 7;
		item.title = "foo";

		final Recorder<JSONObject> r = new Recorder<>();
		KnHttp.put(url("/posts/{id}")).addPathParameter("id", "7").addObjectBody(item).build().getAsJSONObject(jsonObjectListener(r));
		r.await().assertSuccess();

		final RecordedRequest recorded = server.takeRequest();
		assertEquals("PUT", recorded.getMethod());
		final JSONObject sent = JSONObject.parseObject(recorded.getBody().utf8());
		assertEquals(7, sent.getIntValue("id"));
		assertEquals("foo", sent.getString("title"));
	}

	@Test
	public void customJsonContentType_getsTheUtf8Charset() throws Exception {
		server.enqueue(new MockResponse.Builder().body("{}").build());

		final Recorder<JSONObject> r = new Recorder<>();
		KnHttp.post(url("/json")).setContentType("application/json+lama").addJSONObjectBody(new JSONObject().fluentPut("a", 1)).build().getAsJSONObject(jsonObjectListener(r));
		r.await().assertSuccess();

		assertEquals("application/json+lama; charset=utf-8", server.takeRequest().getHeaders().get("Content-Type"));
	}

	@Test
	public void formBody_keepsTheParametersOrder() throws Exception {
		server.enqueue(new MockResponse.Builder().body("ok").build());

		final Recorder<String> r = new Recorder<>();
		KnHttp.request(url("/form"), Method.PATCH)
				.addBodyParameter("z", "1")
				.addBodyParameter("a", "é")
				.build()
				.getAsString(stringListener(r));
		r.await().assertSuccess();

		final RecordedRequest recorded = server.takeRequest();
		assertEquals("PATCH", recorded.getMethod());
		assertEquals("z=1&a=%C3%A9", recorded.getBody().utf8());
	}

	@Test
	public void cleartext_isRefusedByDefault() {
		KnHttp.shutDown();
		initKnHttp(new KnSettings.Builder());

		final Recorder<String> r = new Recorder<>();
		KnHttp.get(url("/http")).build().getAsString(stringListener(r));
		r.await().assertError();

		assertEquals(KnError.Type.INVALID_REQUEST, r.error.getType());
		assertEquals(KnConstants.INVALID_REQUEST_ERROR, r.error.getErrorDetail());
		assertTrue(r.error.getCause() instanceof UnknownServiceException);
		assertEquals(0, server.getRequestCount());
	}

	@Test
	public void shutDown_releasesEverything_andKnHttpIsUsableAgain() {
		server.enqueue(new MockResponse.Builder().body("before").build());
		server.enqueue(new MockResponse.Builder().body("after").build());

		final Recorder<String> before = new Recorder<>();
		KnHttp.get(url("/before")).build().getAsString(stringListener(before));
		before.await().assertSuccess();

		final KnEngine engine = KnEngine.peek();
		assertNotNull(engine);
		KnHttp.shutDown();
		assertNull(KnEngine.peek());
		assertTrue(engine.getDispatcher().isShutdown());

		initKnHttp(new KnSettings.Builder().setAllowCleartextTraffic(true));
		final Recorder<String> after = new Recorder<>();
		KnHttp.get(url("/after")).build().getAsString(stringListener(after));
		after.await().assertSuccess();
		assertEquals("after", after.result);
	}

	@Test
	public void manyParallelRequests_withTheDefaultPriority_allSucceed() {
		final int count = 24;
		for (int i = 0; i < count; i++) server.enqueue(new MockResponse.Builder().bodyDelay(100, TimeUnit.MILLISECONDS).body("ok").build());

		final long start = System.nanoTime();
		@SuppressWarnings("unchecked") final Recorder<String>[] recorders = new Recorder[count];
		for (int i = 0; i < count; i++) {
			recorders[i] = new Recorder<>();
			KnHttp.get(url("/parallel/" + i)).build().getAsString(stringListener(recorders[i]));
		}
		for (Recorder<String> r : recorders) r.await().assertSuccess();
		final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

		// v4 ran the IMMEDIATE requests (its default priority) on 2 threads only: >= 1.2 s here
		assertTrue("Took " + elapsedMs + " ms", elapsedMs < 1200);
	}

	private static boolean waitUntilRunning(Object tag) {
		final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
		while (System.currentTimeMillis() < deadline) {
			if (KnHttp.isRequestRunning(tag)) return true;
			idleMainLooper(5);
		}
		return false;
	}
}

package ovh.karewan.knhttp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alibaba.fastjson2.JSONObject;

import org.junit.Test;

import java.io.File;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import mockwebserver3.MockResponse;
import okhttp3.OkHttpClient;
import ovh.karewan.knhttp.common.KnConstants;
import ovh.karewan.knhttp.common.KnResponse;
import ovh.karewan.knhttp.error.KnError;

/**
 * Each failure gets the type an app needs to show the right message
 */
public class ErrorTypeTest extends BaseTest {

	public static class ApiError {
		public String message;
	}

	@Test
	public void httpError_hasTheStatus_andIsNotRetryable() {
		server.enqueue(new MockResponse.Builder().code(404).body("missing").build());

		final KnError err = KnHttp.get(url("/missing")).build().executeForString().getError();

		assertEquals(KnError.Type.HTTP, err.getType());
		assertEquals(404, err.getErrorCode());
		assertEquals("missing", err.getErrorBody());
		assertEquals(KnConstants.RESPONSE_FROM_SERVER_ERROR, err.getErrorDetail());
		assertTrue(err.getMessage(), err.getMessage().startsWith("HTTP 404"));
		assertTrue(err.toString(), err.toString().contains("type=HTTP"));
		assertFalse(err.isRetryable());
	}

	@Test
	public void serverOverloaded_isRetryable() {
		server.enqueue(new MockResponse.Builder().code(503).build());
		server.enqueue(new MockResponse.Builder().code(429).build());
		server.enqueue(new MockResponse.Builder().code(501).build());

		assertTrue(KnHttp.get(url("/a")).build().executeForString().getError().isRetryable());
		assertTrue(KnHttp.get(url("/b")).build().executeForString().getError().isRetryable());
		assertFalse(KnHttp.get(url("/c")).build().executeForString().getError().isRetryable());
	}

	@Test
	public void errorBody_canBeParsedToShowTheServerMessage() {
		server.enqueue(new MockResponse.Builder().code(422).body("{\"message\":\"Invalid email\",\"field\":\"email\"}").build());
		server.enqueue(new MockResponse.Builder().code(500).body("<html>Oops</html>").build());

		final KnError err = KnHttp.post(url("/users")).addStringBody("{}").build().executeForString().getError();
		assertEquals("Invalid email", err.getErrorBodyAs(ApiError.class).message);
		assertEquals("email", err.getErrorBodyAs(JSONObject.class).getString("field"));

		final KnError html = KnHttp.get(url("/broken")).build().executeForString().getError();
		assertNull(html.getErrorBodyAs(ApiError.class));
		assertNull(new KnError("no body").getErrorBodyAs(ApiError.class));
	}

	@Test
	public void connectionRefused_isNetwork_andRetryable() throws Exception {
		final int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}

		final KnError err = KnHttp.get("http://127.0.0.1:" + closedPort + "/").build().executeForString().getError();

		assertEquals(KnError.Type.NETWORK, err.getType());
		assertEquals(KnConstants.CONNECTION_ERROR, err.getErrorDetail());
		assertTrue(err.isRetryable());
	}

	@Test
	public void unknownHost_isNetwork() {
		final OkHttpClient offline = new OkHttpClient.Builder().dns(hostname -> {
			throw new UnknownHostException(hostname);
		}).build();

		final KnError err = KnHttp.get("http://api.example.com/").setOkHttpClient(offline).build().executeForString().getError();

		assertEquals(KnError.Type.NETWORK, err.getType());
		assertTrue(err.getCause() instanceof UnknownHostException);
	}

	@Test
	public void readTimeout_isTimeout_andRetryable() {
		server.enqueue(new MockResponse.Builder().headersDelay(3, TimeUnit.SECONDS).build());

		final KnError err = KnHttp.get(url("/slow")).setReadTimeout(200, TimeUnit.MILLISECONDS).build().executeForString().getError();

		assertEquals(KnError.Type.TIMEOUT, err.getType());
		assertEquals(KnConstants.CONNECTION_ERROR, err.getErrorDetail());
		assertTrue(err.isRetryable());
	}

	@Test
	public void callTimeout_isTimeout() {
		server.enqueue(new MockResponse.Builder().bodyDelay(3, TimeUnit.SECONDS).body("late").build());

		final KnError err = KnHttp.get(url("/slow-body")).setCallTimeout(300, TimeUnit.MILLISECONDS).build().executeForString().getError();

		assertEquals(KnError.Type.TIMEOUT, err.getType());
	}

	@Test
	public void invalidJson_isParse() {
		server.enqueue(new MockResponse.Builder().body("not json").build());

		final KnError err = KnHttp.get(url("/json")).build().executeForJSONObject().getError();

		assertEquals(KnError.Type.PARSE, err.getType());
		assertEquals(KnConstants.PARSE_ERROR, err.getErrorDetail());
		assertFalse(err.isRetryable());
	}

	@Test
	public void invalidUrl_isInvalidRequest() {
		final KnError err = KnHttp.get("htp//not a url").build().executeForString().getError();

		assertEquals(KnError.Type.INVALID_REQUEST, err.getType());
		assertEquals(KnConstants.INVALID_REQUEST_ERROR, err.getErrorDetail());
	}

	@Test
	public void cancelledRequest_isCancelled() {
		final Recorder<String> r = new Recorder<>();
		KnHttp.get(url("/never")).build().getAsString(stringListener(r));
		KnHttp.cancelAll();
		r.await().assertError();

		assertEquals(KnError.Type.CANCELLED, r.error.getType());
		assertTrue(r.error.isCancellation());
		assertFalse(r.error.isRetryable());
	}

	@Test
	public void downloadFileNotWritable_isStorage() throws Exception {
		server.enqueue(new MockResponse.Builder().body("content").build());
		final File notADirectory = tmp.newFile("file.txt");

		final KnResponse<String> res = KnHttp.download(url("/file"), notADirectory.getAbsolutePath(), "download.bin").build().executeForDownload();

		assertEquals(KnError.Type.STORAGE, res.getError().getType());
		assertFalse(res.getError().isRetryable());
	}

	@Test
	public void unexpectedException_isUnknown() {
		final OkHttpClient broken = new OkHttpClient.Builder().addInterceptor(chain -> {
			throw new IllegalStateException("bug");
		}).build();

		final KnError err = KnHttp.get(url("/")).setOkHttpClient(broken).build().executeForString().getError();

		assertEquals(KnError.Type.UNKNOWN, err.getType());
		assertTrue(err.getCause() instanceof IllegalStateException);
	}

	@Test
	public void errorsCreatedByTheApp_areUnknown() {
		final KnError err = new KnError("app error");

		assertEquals(KnError.Type.UNKNOWN, err.getType());
		assertFalse(err.isRetryable());
		assertFalse(err.isCancellation());
	}
}

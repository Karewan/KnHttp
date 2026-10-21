package ovh.karewan.knhttp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.alibaba.fastjson2.JSONObject;

import org.junit.Test;

import java.util.List;

import mockwebserver3.MockResponse;
import okhttp3.Response;
import ovh.karewan.knhttp.common.KnConstants;
import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnResponse;

public class SyncRequestTest extends BaseTest {

	public static class Item {
		public int id;
	}

	@Test
	public void executeForString_returnsTheBody() {
		server.enqueue(new MockResponse.Builder().body("sync").build());

		final KnResponse<String> res = KnHttp.get(url("/sync")).build().executeForString();

		assertTrue(res.isSuccess());
		assertEquals("sync", res.getResult());
		assertEquals(200, res.getOkHttpResponse().code());
	}

	@Test
	public void executeForObject_andObjectList_areTyped() {
		server.enqueue(new MockResponse.Builder().body("{\"id\":4}").build());
		server.enqueue(new MockResponse.Builder().body("[{\"id\":5}]").build());

		final KnResponse<Item> item = KnHttp.get(url("/item")).build().executeForObject(Item.class);
		assertEquals(4, item.getResult().id);

		final KnResponse<List<Item>> items = KnHttp.get(url("/items")).build().executeForObjectList(Item.class);
		assertEquals(5, items.getResult().get(0).id);
	}

	@Test
	public void executeForOkHttpResponse_theCallerOwnsAnOpenResponse() throws Exception {
		server.enqueue(new MockResponse.Builder().body("raw body").build());

		final KnResponse<Response> res = KnHttp.get(url("/raw")).build().executeForOkHttpResponse();
		assertTrue(res.isSuccess());

		try (Response response = res.getResult()) {
			assertEquals("raw body", response.body().string());
		}
	}

	@Test
	public void serverError_keepsTheResponseAndTheErrorBody() {
		server.enqueue(new MockResponse.Builder().code(503).body("unavailable").build());

		final KnResponse<JSONObject> res = KnHttp.get(url("/down")).build().executeForJSONObject();

		assertFalse(res.isSuccess());
		assertEquals(503, res.getError().getErrorCode());
		assertEquals("unavailable", res.getError().getErrorBody());
		assertEquals(KnConstants.RESPONSE_FROM_SERVER_ERROR, res.getError().getErrorDetail());
		assertNotNull(res.getOkHttpResponse());
	}

	@Test
	public void aSynchronousRequest_canBeExecutedSeveralTimes() {
		server.enqueue(new MockResponse.Builder().body("1").build());
		server.enqueue(new MockResponse.Builder().body("2").build());

		final KnRequest request = KnHttp.get(url("/poll")).build();
		assertEquals("1", request.executeForString().getResult());
		assertEquals("2", request.executeForString().getResult());
	}

	@Test
	public void errorBody_isLimitedTo1MiB() {
		final StringBuilder sb = new StringBuilder();
		while (sb.length() < 2 * KnConstants.MAX_ERROR_BODY_SIZE) sb.append("0123456789abcdef");
		server.enqueue(new MockResponse.Builder().code(500).body(sb.toString()).build());

		final KnResponse<String> res = KnHttp.get(url("/huge-error")).build().executeForString();

		assertEquals(KnConstants.MAX_ERROR_BODY_SIZE, res.getError().getErrorBody().length());
	}
}

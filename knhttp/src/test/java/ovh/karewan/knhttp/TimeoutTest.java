package ovh.karewan.knhttp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;

import mockwebserver3.MockResponse;
import ovh.karewan.knhttp.common.KnConstants;
import ovh.karewan.knhttp.common.KnResponse;
import ovh.karewan.knhttp.common.KnSettings;

public class TimeoutTest extends BaseTest {

	@Test
	public void perRequestReadTimeout_isShorterThanTheGlobalOne() {
		server.enqueue(new MockResponse.Builder().headersDelay(3, TimeUnit.SECONDS).body("late").build());

		final long start = System.nanoTime();
		final Recorder<String> r = new Recorder<>();
		KnHttp.get(url("/slow")).setReadTimeout(200, TimeUnit.MILLISECONDS).build().getAsString(stringListener(r));
		r.await().assertError();
		final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

		assertEquals(KnConstants.CONNECTION_ERROR, r.error.getErrorDetail());
		assertTrue(r.error.getCause() instanceof InterruptedIOException);
		assertTrue("Took " + elapsedMs + " ms", elapsedMs < 2500);
	}

	@Test
	public void perRequestReadTimeout_canBeLongerThanTheGlobalOne() {
		KnHttp.shutDown();
		initKnHttp(new KnSettings.Builder().setAllowCleartextTraffic(true).setReadTimeout(200));

		server.enqueue(new MockResponse.Builder().headersDelay(600, TimeUnit.MILLISECONDS).body("global").build());
		server.enqueue(new MockResponse.Builder().headersDelay(600, TimeUnit.MILLISECONDS).body("override").build());

		final KnResponse<String> global = KnHttp.get(url("/global")).build().executeForString();
		assertTrue(global.getError().getCause() instanceof InterruptedIOException);

		final KnResponse<String> override = KnHttp.get(url("/override")).setReadTimeout(5, TimeUnit.SECONDS).build().executeForString();
		assertTrue(override.isSuccess());
		assertEquals("override", override.getResult());
	}

	@Test
	public void perRequestCallTimeout_coversTheWholeCall() {
		server.enqueue(new MockResponse.Builder().bodyDelay(3, TimeUnit.SECONDS).body("late").build());

		final long start = System.nanoTime();
		final KnResponse<String> res = KnHttp.get(url("/slow-body")).setCallTimeout(300, TimeUnit.MILLISECONDS).build().executeForString();
		final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

		assertTrue(res.getError().getCause() instanceof InterruptedIOException);
		assertTrue("Took " + elapsedMs + " ms", elapsedMs < 2500);
	}

	@Test
	public void invalidTimeouts_areRejectedByTheBuilder() {
		try {
			KnHttp.get(url("/")).setReadTimeout(-1, TimeUnit.SECONDS);
			fail("IllegalArgumentException expected");
		} catch (IllegalArgumentException expected) {
			// OK
		}

		try {
			KnHttp.get(url("/")).setConnectTimeout(100, TimeUnit.MICROSECONDS);
			fail("IllegalArgumentException expected");
		} catch (IllegalArgumentException expected) {
			// OK
		}

		// 0 = no timeout, like OkHttp
		KnHttp.get(url("/")).setWriteTimeout(0, TimeUnit.SECONDS).build();
	}
}

package ovh.karewan.knhttp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;

import mockwebserver3.MockResponse;
import okio.Buffer;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;
import ovh.karewan.knhttp.common.KnResponse;
import ovh.karewan.knhttp.common.KnSettings;
import ovh.karewan.knhttp.error.KnError;

/**
 * HTTP cache, compression and redirects
 */
public class CacheAndCompressionTest extends BaseTest {

	private void initWith(KnSettings.Builder settings) {
		KnHttp.shutDown();
		initKnHttp(settings.setAllowCleartextTraffic(true));
	}

	private static MockResponse cacheable(String body) {
		return new MockResponse.Builder().addHeader("Cache-Control", "max-age=60").body(body).build();
	}

	private static Buffer gzip(String text) throws IOException {
		final Buffer gzipped = new Buffer();
		try (BufferedSink sink = Okio.buffer(new GzipSink(gzipped))) {
			sink.writeUtf8(text);
		}
		return gzipped;
	}

	@Test
	public void cache_servesFreshResponses_withoutTheNetwork() {
		initWith(new KnSettings.Builder().setEnableCache(true));
		server.enqueue(cacheable("cached"));

		assertEquals("cached", KnHttp.get(url("/c")).build().executeForString().getResult());
		final KnResponse<String> second = KnHttp.get(url("/c")).build().executeForString();

		assertEquals("cached", second.getResult());
		assertNull(second.getOkHttpResponse().networkResponse());
		assertNotNull(second.getOkHttpResponse().cacheResponse());
		assertEquals(1, server.getRequestCount());
	}

	@Test
	public void requestCacheControls_forceTheNetworkOrTheCache() {
		initWith(new KnSettings.Builder().setEnableCache(true));
		server.enqueue(cacheable("v1"));
		server.enqueue(cacheable("v2"));

		assertEquals("v1", KnHttp.get(url("/c")).build().executeForString().getResult());
		assertEquals("v2", KnHttp.get(url("/c")).getResponseOnlyFromNetwork().build().executeForString().getResult());
		assertEquals("v2", KnHttp.get(url("/c")).getResponseOnlyIfCached().build().executeForString().getResult());
		assertEquals(2, server.getRequestCount());
	}

	@Test
	public void doNotCacheResponse_isNotStored() {
		initWith(new KnSettings.Builder().setEnableCache(true));
		server.enqueue(cacheable("secret"));

		assertEquals("secret", KnHttp.get(url("/c")).doNotCacheResponse().build().executeForString().getResult());
		final KnError err = KnHttp.get(url("/c")).getResponseOnlyIfCached().build().executeForString().getError();

		// OkHttp answers 504 when an only-if-cached request isn't in the cache
		assertEquals(KnError.Type.HTTP, err.getType());
		assertEquals(504, err.getErrorCode());
		assertEquals(1, server.getRequestCount());
	}

	@Test
	public void gzipResponses_areDecompressed_andBrotliIsAccepted() throws Exception {
		server.enqueue(new MockResponse.Builder().addHeader("Content-Encoding", "gzip").body(gzip("compressed text")).build());

		assertEquals("compressed text", KnHttp.get(url("/gz")).build().executeForString().getResult());
		assertEquals("br, gzip", server.takeRequest().getHeaders().get("Accept-Encoding"));
	}

	@Test
	public void withoutBrotli_gzipIsStillDecompressed() throws Exception {
		initWith(new KnSettings.Builder().setEnableBrotli(false));
		server.enqueue(new MockResponse.Builder().addHeader("Content-Encoding", "gzip").body(gzip("compressed text")).build());

		assertEquals("compressed text", KnHttp.get(url("/gz")).build().executeForString().getResult());
		assertEquals("gzip", server.takeRequest().getHeaders().get("Accept-Encoding"));
	}

	@Test
	public void redirects_areNotFollowedByDefault() {
		server.enqueue(new MockResponse.Builder().code(302).addHeader("Location", "/target").body("moved").build());

		final KnResponse<String> res = KnHttp.get(url("/old")).build().executeForString();

		assertTrue(res.isSuccess());
		assertEquals(302, res.getOkHttpResponse().code());
		assertEquals(1, server.getRequestCount());
	}

	@Test
	public void redirects_areFollowedWhenEnabled() {
		initWith(new KnSettings.Builder().setFollowRedirect(true));
		server.enqueue(new MockResponse.Builder().code(302).addHeader("Location", "/target").build());
		server.enqueue(new MockResponse.Builder().body("target").build());

		assertEquals("target", KnHttp.get(url("/old")).build().executeForString().getResult());
		assertEquals(2, server.getRequestCount());
	}
}

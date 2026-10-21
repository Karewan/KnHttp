package ovh.karewan.knhttp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.conscrypt.Conscrypt;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.UnknownServiceException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLSocket;

import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.TlsVersion;
import ovh.karewan.knhttp.common.KnResponse;
import ovh.karewan.knhttp.common.KnSettings;
import ovh.karewan.knhttp.error.KnError;

/**
 * Checks what only a real device can: the native conscrypt-android library, the TLS handshake with a real server
 * and the Android network security policy. Run it on the oldest supported Android versions too (API 21+).
 * Internet access is needed (https://www.cloudflare.com/cdn-cgi/trace).
 */
@RunWith(AndroidJUnit4.class)
public class DeviceNetworkTest {
	private static final String TRACE_URL = "https://www.cloudflare.com/cdn-cgi/trace";

	private Context context;

	@Before
	public void setUp() {
		context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		KnHttp.init(context);
	}

	@After
	public void tearDown() {
		KnHttp.shutDown();
	}

	/**
	 * The trace seen by Cloudflare: "key=value" lines (tls=TLSv1.3, kex=X25519, http=http/2...)
	 */
	private static String trace(Response response) throws IOException {
		return response.body().string();
	}

	private static String traceValue(String trace, String key) {
		for (String line : trace.split("\n")) {
			if (line.startsWith(key + "=")) return line.substring(key.length() + 1);
		}
		return null;
	}

	@Test
	public void theBundledConscryptIsUsed() throws Exception {
		final SSLSocket socket = (SSLSocket) KnHttp.getOkHttpClient().sslSocketFactory().createSocket();
		assertTrue(Conscrypt.isConscrypt(socket));
		socket.close();
	}

	@Test
	public void tls13_x25519_http2_andEccCertificates_byDefault() throws Exception {
		final KnResponse<Response> res = KnHttp.get(TRACE_URL).build().executeForOkHttpResponse();
		assertTrue(String.valueOf(res.getError()), res.isSuccess());

		try (Response response = res.getResult()) {
			// HTTP/2 needs ALPN, negotiated by OkHttp through the Conscrypt socket
			assertEquals(Protocol.HTTP_2, response.protocol());
			assertEquals(TlsVersion.TLS_1_3, response.handshake().tlsVersion());

			final X509Certificate leaf = (X509Certificate) response.handshake().peerCertificates().get(0);
			assertEquals("EC", leaf.getPublicKey().getAlgorithm());

			final String trace = trace(response);
			assertEquals(trace, "TLSv1.3", traceValue(trace, "tls"));
			assertEquals(trace, "http/2", traceValue(trace, "http"));
			// Classic key exchange by default: no post-quantum hybrid
			assertEquals(trace, "X25519", traceValue(trace, "kex"));
		}
	}

	@Test
	public void postQuantumKeyExchange_whenEnabled() throws Exception {
		KnHttp.shutDown();
		KnHttp.init(context, new KnSettings.Builder().setEnablePostQuantumKeyExchange(true).build());

		final KnResponse<Response> res = KnHttp.get(TRACE_URL).build().executeForOkHttpResponse();
		assertTrue(String.valueOf(res.getError()), res.isSuccess());

		try (Response response = res.getResult()) {
			final String trace = trace(response);
			assertEquals(trace, "X25519MLKEM768", traceValue(trace, "kex"));
		}
	}

	@Test
	public void cleartext_allowedByKnHttp_isStillBlockedByTheAndroidNetworkSecurityPolicy() {
		KnHttp.shutDown();
		KnHttp.init(context, new KnSettings.Builder().setAllowCleartextTraffic(true).build());

		// Refused before any connection: no network needed
		final KnError err = KnHttp.get("http://example.com/").build().executeForString().getError();

		assertEquals(KnError.Type.INVALID_REQUEST, err.getType());
		assertTrue(String.valueOf(err.getCause()), err.getCause() instanceof UnknownServiceException);
		assertTrue(err.getCause().getMessage(), err.getCause().getMessage().contains("network security policy"));
	}
}

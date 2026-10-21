package ovh.karewan.knhttp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.conscrypt.Conscrypt;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import mockwebserver3.MockResponse;
import okhttp3.Response;
import okhttp3.TlsVersion;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import ovh.karewan.knhttp.common.KnConstants;
import ovh.karewan.knhttp.common.KnResponse;
import ovh.karewan.knhttp.common.KnSettings;
import ovh.karewan.knhttp.error.KnError;

/**
 * Runs against the JVM build of Conscrypt (same version and same Java code as conscrypt-android)
 */
public class TlsTest extends BaseTest {

	private HandshakeCertificates serverCertificates;

	@Before
	public void useHttps() {
		final HeldCertificate certificate = new HeldCertificate.Builder().addSubjectAlternativeName(server.getHostName()).build();
		serverCertificates = new HandshakeCertificates.Builder().heldCertificate(certificate).build();
		server.useHttps(serverCertificates.sslSocketFactory());
	}

	/**
	 * A Conscrypt server which only accepts the hybrid post-quantum key exchange
	 */
	private void usePostQuantumOnlyServer() throws Exception {
		final SSLContext context = SSLContext.getInstance("TLS", Conscrypt.newProvider());
		context.init(new KeyManager[] { serverCertificates.keyManager() }, null, null);
		final SSLSocketFactory socketFactory = context.getSocketFactory();
		Conscrypt.setNamedGroups(socketFactory, new String[] { "X25519MLKEM768" });
		server.useHttps(socketFactory);
	}

	@Test
	public void postQuantumKeyExchange_isNotOfferedByDefault() throws Exception {
		usePostQuantumOnlyServer();
		KnHttp.shutDown();
		initKnHttp(new KnSettings.Builder().setAllowInvalidTlsCertificates(true));
		server.enqueue(new MockResponse.Builder().body("secure").build());

		final KnResponse<String> res = KnHttp.get(url("/secure")).build().executeForString();

		assertTrue(String.valueOf(res.getError().getCause()), hasCause(res.getError().getCause(), SSLHandshakeException.class));
		assertEquals(KnError.Type.TLS, res.getError().getType());
		assertEquals(0, server.getRequestCount());
	}

	@Test
	public void postQuantumKeyExchange_canBeEnabled() throws Exception {
		usePostQuantumOnlyServer();
		KnHttp.shutDown();
		initKnHttp(new KnSettings.Builder().setAllowInvalidTlsCertificates(true).setEnablePostQuantumKeyExchange(true));
		server.enqueue(new MockResponse.Builder().body("secure").build());

		final KnResponse<String> res = KnHttp.get(url("/secure")).build().executeForString();

		assertTrue(String.valueOf(res.getError()), res.isSuccess());
		assertEquals("secure", res.getResult());
	}

	@Test
	public void theDefaultClient_negotiatesTls13WithConscrypt() throws Exception {
		KnHttp.shutDown();
		initKnHttp(new KnSettings.Builder().setAllowInvalidTlsCertificates(true));
		server.enqueue(new MockResponse.Builder().body("secure").build());

		final KnResponse<Response> res = KnHttp.get(url("/secure")).build().executeForOkHttpResponse();

		try (Response response = res.getResult()) {
			assertEquals("secure", response.body().string());
			assertEquals(TlsVersion.TLS_1_3, response.handshake().tlsVersion());
		}

		final SSLSocket socket = (SSLSocket) KnHttp.getOkHttpClient().sslSocketFactory().createSocket();
		assertTrue(Conscrypt.isConscrypt(socket));
		final List<String> enabled = Arrays.asList(socket.getEnabledProtocols());
		assertTrue(enabled.contains("TLSv1.3"));
		assertTrue(enabled.contains("TLSv1.2"));
		assertFalse(enabled.contains("TLSv1"));
		socket.close();
	}

	@Test
	public void untrustedCertificates_areRejectedByDefault() {
		KnHttp.shutDown();
		initKnHttp(new KnSettings.Builder());
		server.enqueue(new MockResponse.Builder().body("secure").build());

		final KnResponse<String> res = KnHttp.get(url("/secure")).build().executeForString();

		assertEquals(KnConstants.CONNECTION_ERROR, res.getError().getErrorDetail());
		// localhost may also resolve to ::1 where nothing listens: OkHttp reports the other routes failures as suppressed
		assertTrue(String.valueOf(res.getError().getCause()), hasCause(res.getError().getCause(), SSLHandshakeException.class));
		assertEquals(KnError.Type.TLS, res.getError().getType());
		assertFalse(res.getError().isRetryable());
		assertEquals(0, server.getRequestCount());
	}

	private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
		if (throwable == null) return false;
		if (type.isInstance(throwable)) return true;
		for (Throwable suppressed : throwable.getSuppressed()) if (hasCause(suppressed, type)) return true;
		return hasCause(throwable.getCause(), type);
	}

	@Test
	public void obsoleteTls_enablesTls10And11WithoutAnySocketFactoryWrapper() throws Exception {
		KnHttp.shutDown();
		initKnHttp(new KnSettings.Builder().setAllowObsoleteTls(true));

		final SSLSocket socket = (SSLSocket) KnHttp.getOkHttpClient().sslSocketFactory().createSocket();
		final List<String> enabled = Arrays.asList(socket.getEnabledProtocols());
		assertTrue(enabled.contains("TLSv1"));
		assertTrue(enabled.contains("TLSv1.1"));
		assertTrue(enabled.contains("TLSv1.3"));
		socket.close();
	}
}

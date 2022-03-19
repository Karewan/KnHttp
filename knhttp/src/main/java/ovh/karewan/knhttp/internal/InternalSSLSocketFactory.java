package ovh.karewan.knhttp.internal;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

@SuppressWarnings("DuplicateThrows")
public final class InternalSSLSocketFactory extends SSLSocketFactory {
	private final SSLSocketFactory mSSLSocketFactory;
	private final String[] mTlsProtocols;

	public InternalSSLSocketFactory(SSLSocketFactory sslSocketFactory, boolean obsoleteTls) throws KeyManagementException, NoSuchAlgorithmException {
		this.mSSLSocketFactory = sslSocketFactory;
		this.mTlsProtocols = obsoleteTls ? new String[] {"TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"} : new String[] {"TLSv1.2", "TLSv1.3"};
	}

	@Override
	public String[] getDefaultCipherSuites() {
		return mSSLSocketFactory.getDefaultCipherSuites();
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return mSSLSocketFactory.getSupportedCipherSuites();
	}

	@Override
	public Socket createSocket() throws IOException {
		return enableTLSOnSocket(mSSLSocketFactory.createSocket());
	}

	@Override
	public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
		return enableTLSOnSocket(mSSLSocketFactory.createSocket(s, host, port, autoClose));
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
		return enableTLSOnSocket(mSSLSocketFactory.createSocket(host, port));
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
		return enableTLSOnSocket(mSSLSocketFactory.createSocket(host, port, localHost, localPort));
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		return enableTLSOnSocket(mSSLSocketFactory.createSocket(host, port));
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
		return enableTLSOnSocket(mSSLSocketFactory.createSocket(address, port, localAddress, localPort));
	}

	private Socket enableTLSOnSocket(Socket socket) {
		if (socket instanceof SSLSocket) ((SSLSocket) socket).setEnabledProtocols(mTlsProtocols);
		return socket;
	}
}

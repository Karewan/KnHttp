/*
    KnHttp

    Copyright (c) 2019 Florent VIALATTE
    Copyright (c) 2016-2019 Amit Shekhar

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */
package ovh.karewan.knhttp.internal;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

@SuppressWarnings("DuplicateThrows")
public final class InternalSSLSocketFactory extends SSLSocketFactory {
	private final SSLSocketFactory internalSSLSocketFactory;
	private final String[] mTlsProtocols;

	public InternalSSLSocketFactory(boolean obsoleteTls) throws KeyManagementException, NoSuchAlgorithmException {
		// Define enabled TLS protocols
		if(!obsoleteTls) mTlsProtocols = new String[] {"TLSv1.2", "TLSv1.3"};
		else mTlsProtocols = new String[] {"TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"};

		SSLContext context = SSLContext.getInstance("TLS");
		context.init(null, null, null);
		internalSSLSocketFactory = context.getSocketFactory();
	}

	@Override
	public String[] getDefaultCipherSuites() {
		return internalSSLSocketFactory.getDefaultCipherSuites();
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return internalSSLSocketFactory.getSupportedCipherSuites();
	}

	@Override
	public Socket createSocket() throws IOException {
		return enableTLSOnSocket(internalSSLSocketFactory.createSocket());
	}

	@Override
	public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
		return enableTLSOnSocket(internalSSLSocketFactory.createSocket(s, host, port, autoClose));
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
		return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
		return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port, localHost, localPort));
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
		return enableTLSOnSocket(internalSSLSocketFactory.createSocket(address, port, localAddress, localPort));
	}

	private Socket enableTLSOnSocket(Socket socket) {
		if (socket instanceof SSLSocket) ((SSLSocket) socket).setEnabledProtocols(mTlsProtocols);
		return socket;
	}
}

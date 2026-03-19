package ovh.karewan.knhttp.internal;

import android.annotation.SuppressLint;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

@SuppressLint("CustomX509TrustManager")
public class InternalUnsafeX509 implements X509TrustManager {
	@SuppressLint("TrustAllX509TrustManager")
	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
	}

	@SuppressLint("TrustAllX509TrustManager")
	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return new X509Certificate[]{};
	}
}

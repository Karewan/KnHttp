package ovh.karewan.knhttp.internal;

import androidx.annotation.NonNull;

import ovh.karewan.knhttp.common.KnRequest;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Applies the per-request connect / read / write timeouts. Application interceptor only:
 * the connect timeout can't be changed once the connection is established.
 */
final class RequestTimeoutInterceptor implements Interceptor {

	@NonNull
	@Override
	public Response intercept(@NonNull Chain chain) throws IOException {
		final KnRequest request = chain.request().tag(KnRequest.class);
		if (request == null) return chain.proceed(chain.request());

		Chain newChain = chain;
		if (request.getConnectTimeoutMs() >= 0) newChain = newChain.withConnectTimeout((int) request.getConnectTimeoutMs(), TimeUnit.MILLISECONDS);
		if (request.getReadTimeoutMs() >= 0) newChain = newChain.withReadTimeout((int) request.getReadTimeoutMs(), TimeUnit.MILLISECONDS);
		if (request.getWriteTimeoutMs() >= 0) newChain = newChain.withWriteTimeout((int) request.getWriteTimeoutMs(), TimeUnit.MILLISECONDS);
		return newChain.proceed(newChain.request());
	}
}

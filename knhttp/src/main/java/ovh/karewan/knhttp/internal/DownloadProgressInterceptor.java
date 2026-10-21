package ovh.karewan.knhttp.internal;

import androidx.annotation.NonNull;

import ovh.karewan.knhttp.common.KnRequest;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Network interceptor tracking the download progress on the raw (still compressed) bytes,
 * consistent with the Content-Length sent by the server.
 */
final class DownloadProgressInterceptor implements Interceptor {

	@NonNull
	@Override
	public Response intercept(@NonNull Chain chain) throws IOException {
		final Response response = chain.proceed(chain.request());

		final KnRequest request = chain.request().tag(KnRequest.class);
		if (request == null || !response.isSuccessful()) return response;

		final ProgressReporter reporter = request.getDownloadProgressReporter();
		if (reporter == null) return response;

		return response.newBuilder().body(new ResponseProgressBody(response.body(), reporter)).build();
	}
}

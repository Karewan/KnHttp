package ovh.karewan.knhttp.internal;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import ovh.karewan.knhttp.common.KnConstants;
import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnResponse;
import ovh.karewan.knhttp.common.RequestType;
import ovh.karewan.knhttp.common.ResponseType;
import ovh.karewan.knhttp.utils.Utils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * Executes a request on the calling thread. Shared by the asynchronous and the synchronous APIs.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class RequestExecutor {

	private RequestExecutor() {}

	/**
	 * Never throws. The OkHttp response is closed, except for {@link ResponseType#OK_HTTP_RESPONSE}
	 * successes where its ownership is transferred to the caller.
	 */
	@NonNull
	public static KnResponse<?> execute(@NonNull KnRequest request, @NonNull KnEngine engine) {
		if (request.isCanceled()) return KnResponse.failed(Utils.getErrorForCancellation());

		final OkHttpClient client;
		final Request okHttpRequest;
		try {
			client = engine.getClientProvider().clientFor(request);
			okHttpRequest = buildOkHttpRequest(request, client, engine.getUserAgent());
		} catch (IllegalArgumentException | IllegalStateException | NullPointerException e) {
			// Invalid URL, header, body...
			return KnResponse.failed(Utils.getErrorForInvalidRequest(e));
		}

		Response response = null;
		boolean transferOwnership = false;

		try {
			final Call call = client.newCall(okHttpRequest);
			if (request.getCallTimeoutMs() >= 0) call.timeout().timeout(request.getCallTimeoutMs(), TimeUnit.MILLISECONDS);
			request.setCall(call);
			// Cancelled between the check above and setCall()
			if (request.isCanceled()) call.cancel();

			response = call.execute();

			if (request.getRequestType() == RequestType.DOWNLOAD) {
				if (response.code() >= 400) return serverError(response);
				Utils.saveFile(response, request.getDirPath(), request.getFileName());
				return success(KnConstants.SUCCESS, response);
			}

			if (request.getResponseAs() == ResponseType.OK_HTTP_RESPONSE) {
				if (request.deliversOnMainThread()) {
					// Reading or closing a network body on the main thread is forbidden (NetworkOnMainThreadException) or may freeze it
					final Response bufferedResponse = bufferBody(response);
					return success(bufferedResponse, bufferedResponse);
				}

				transferOwnership = true;
				return success(response, response);
			}

			if (response.code() >= 400) return serverError(response);

			final KnResponse<?> parsedResponse = request.parseResponse(response);
			parsedResponse.setOkHttpResponse(response);
			return parsedResponse;
		} catch (Exception e) {
			return KnResponse.failed(request.isCanceled() ? Utils.getErrorForCancellation() : Utils.getErrorForConnection(e));
		} finally {
			if (!transferOwnership) Utils.closeQuietly(response);
		}
	}

	/**
	 * Reads the whole body in memory: the network resources are released at once
	 */
	@NonNull
	private static Response bufferBody(@NonNull Response response) throws IOException {
		final ResponseBody body = response.body();
		final Buffer buffer = new Buffer();
		buffer.writeAll(body.source());
		return response.newBuilder().body(ResponseBody.create(buffer, body.contentType(), buffer.size())).build();
	}

	@NonNull
	private static <T> KnResponse<T> success(@NonNull T result, @NonNull Response response) {
		final KnResponse<T> knResponse = KnResponse.success(result);
		knResponse.setOkHttpResponse(response);
		return knResponse;
	}

	@NonNull
	private static KnResponse<?> serverError(@NonNull Response response) {
		final KnResponse<?> knResponse = KnResponse.failed(Utils.getErrorForServerResponse(response));
		knResponse.setOkHttpResponse(response);
		return knResponse;
	}

	@NonNull
	static Request buildOkHttpRequest(@NonNull KnRequest request, @NonNull OkHttpClient client, String globalUserAgent) {
		final Request.Builder builder = new Request.Builder().url(request.buildHttpUrl());

		// Only needed by the interceptors of the extended / derived clients
		if (request.needsExtendedClient() || request.getOkHttpClient() != null) builder.tag(KnRequest.class, request);

		request.applyHeaders(builder, globalUserAgent);

		switch (request.getMethod()) {
			case GET:
				builder.get();
				break;
			case HEAD:
				builder.head();
				break;
			case OPTIONS:
				builder.method(KnConstants.OPTIONS, null);
				break;
			case POST:
				builder.post(requestBody(request));
				break;
			case PUT:
				builder.put(requestBody(request));
				break;
			case DELETE:
				builder.delete(requestBody(request));
				break;
			case PATCH:
				builder.patch(requestBody(request));
				break;
		}

		final CacheControl cacheControl = request.getCacheControl();
		if (cacheControl != null && client.cache() != null) builder.cacheControl(cacheControl);

		return builder.build();
	}

	@NonNull
	private static RequestBody requestBody(@NonNull KnRequest request) {
		if (request.getRequestType() != RequestType.MULTIPART) return request.createRequestBody();

		final RequestBody body = request.createMultipartRequestBody();
		final ProgressReporter reporter = request.getUploadProgressReporter();
		return reporter != null ? new RequestProgressBody(body, reporter) : body;
	}
}

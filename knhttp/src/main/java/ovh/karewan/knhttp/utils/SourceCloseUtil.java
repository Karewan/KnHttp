package ovh.karewan.knhttp.utils;

import ovh.karewan.knhttp.common.ANRequest;
import ovh.karewan.knhttp.common.ResponseType;

import okhttp3.Response;

public final class SourceCloseUtil {

	private SourceCloseUtil() {
	}

	public static void close(Response response, ANRequest request) {
		if (request.getResponseAs() != ResponseType.OK_HTTP_RESPONSE && response != null && response.body() != null && response.body().source() != null) {
			try {
				response.body().source().close();
			} catch (Exception ignore) {

			}
		}
	}
}

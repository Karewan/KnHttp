package ovh.karewan.knhttp.utils;

import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.ResponseType;

import okhttp3.Response;

@SuppressWarnings("rawtypes")
public final class SourceCloseUtil {

	private SourceCloseUtil() {
	}

	public static void close(Response response, KnRequest request) {
		//noinspection ConstantConditions
		if (request.getResponseAs() != ResponseType.OK_HTTP_RESPONSE && response != null && response.body() != null && response.body().source() != null) {
			try {
				//noinspection ConstantConditions
				response.body().source().close();
			} catch (Exception ignore) {

			}
		}
	}
}

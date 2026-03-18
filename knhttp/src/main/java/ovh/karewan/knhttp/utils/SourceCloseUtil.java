package ovh.karewan.knhttp.utils;

import okhttp3.Response;

@SuppressWarnings("rawtypes")
public final class SourceCloseUtil {

	private SourceCloseUtil() {
	}

	public static void close(Response response) {
		if (response == null) return;

		try {
			response.body().close();
		} catch (Exception ignore) {
		}
	}
}

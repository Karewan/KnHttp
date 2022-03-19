package ovh.karewan.knhttp.interfaces;

import okhttp3.Response;
import ovh.karewan.knhttp.error.ANError;

public interface ParsedRequestListener<T> {
	void onResponse(T response, Response okHttpResponse);
	void onError(ANError anError);
}

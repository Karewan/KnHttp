package ovh.karewan.knhttp.interfaces;

import okhttp3.Response;
import ovh.karewan.knhttp.error.ANError;

public interface StringRequestListener {
	void onResponse(String response, Response okHttpResponse);
	void onError(ANError anError);
}

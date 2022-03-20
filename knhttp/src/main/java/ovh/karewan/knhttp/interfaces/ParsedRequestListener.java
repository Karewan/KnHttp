package ovh.karewan.knhttp.interfaces;

import okhttp3.Response;
import ovh.karewan.knhttp.error.KnError;

public interface ParsedRequestListener<T> {
	void onResponse(T obj, Response okHttpRes);
	void onError(KnError err);
}

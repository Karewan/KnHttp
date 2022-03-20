package ovh.karewan.knhttp.interfaces;

import okhttp3.Response;
import ovh.karewan.knhttp.error.KnError;

public interface StringRequestListener {
	void onResponse(String str, Response okHttpRes);
	void onError(KnError err);
}

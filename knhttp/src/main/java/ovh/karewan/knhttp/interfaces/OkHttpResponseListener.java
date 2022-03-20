package ovh.karewan.knhttp.interfaces;

import ovh.karewan.knhttp.error.KnError;

import okhttp3.Response;

public interface OkHttpResponseListener {
	void onResponse(Response okHttpRes);
	void onError(KnError err);
}

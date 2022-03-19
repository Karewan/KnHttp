package ovh.karewan.knhttp.interfaces;

import ovh.karewan.knhttp.error.ANError;

import okhttp3.Response;

public interface OkHttpResponseListener {
	void onResponse(Response response);
	void onError(ANError anError);
}

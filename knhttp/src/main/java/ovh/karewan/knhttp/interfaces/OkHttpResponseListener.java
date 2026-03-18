package ovh.karewan.knhttp.interfaces;

import androidx.annotation.NonNull;

import ovh.karewan.knhttp.error.KnError;

import okhttp3.Response;

public interface OkHttpResponseListener {
	void onResponse(@NonNull Response okHttpRes);
	void onError(@NonNull KnError err);
}

package ovh.karewan.knhttp.interfaces;

import androidx.annotation.NonNull;

import okhttp3.Response;
import ovh.karewan.knhttp.error.KnError;

public interface ParsedRequestListener<T> {
	void onResponse(@NonNull T obj, @NonNull Response okHttpRes);
	void onError(@NonNull KnError err);
}

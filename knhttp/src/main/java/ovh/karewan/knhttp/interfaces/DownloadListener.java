package ovh.karewan.knhttp.interfaces;

import androidx.annotation.NonNull;

import okhttp3.Response;
import ovh.karewan.knhttp.error.KnError;

public interface DownloadListener {
	void onDownloadComplete(@NonNull Response okHttpRes);
	void onError(@NonNull KnError err);
}

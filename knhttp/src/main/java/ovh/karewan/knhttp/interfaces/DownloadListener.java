package ovh.karewan.knhttp.interfaces;

import okhttp3.Response;
import ovh.karewan.knhttp.error.KnError;

public interface DownloadListener {
	void onDownloadComplete(Response okHttpRes);
	void onError(KnError err);
}

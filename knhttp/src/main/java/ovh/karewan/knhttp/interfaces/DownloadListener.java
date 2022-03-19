package ovh.karewan.knhttp.interfaces;

import ovh.karewan.knhttp.error.ANError;

public interface DownloadListener {
	void onDownloadComplete();
	void onError(ANError anError);
}

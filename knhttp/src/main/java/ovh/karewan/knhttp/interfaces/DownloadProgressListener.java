package ovh.karewan.knhttp.interfaces;

public interface DownloadProgressListener {
	void onProgress(long bytesDownloaded, long totalBytes);
}

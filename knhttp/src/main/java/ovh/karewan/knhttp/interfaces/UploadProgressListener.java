package ovh.karewan.knhttp.interfaces;

public interface UploadProgressListener {
	void onProgress(long bytesUploaded, long totalBytes);
}

package ovh.karewan.knhttp.internal;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import ovh.karewan.knhttp.common.KnConstants;
import ovh.karewan.knhttp.interfaces.DownloadProgressListener;
import ovh.karewan.knhttp.model.Progress;

public final class DownloadProgressHandler extends Handler {

	private final DownloadProgressListener mDownloadProgressListener;

	public DownloadProgressHandler(DownloadProgressListener downloadProgressListener) {
		super(Looper.getMainLooper());
		mDownloadProgressListener = downloadProgressListener;
	}

	@Override
	public void handleMessage(Message msg) {
		if (msg.what == KnConstants.UPDATE) {
			if (mDownloadProgressListener != null) {
				final Progress progress = (Progress) msg.obj;
				mDownloadProgressListener.onProgress(progress.currentBytes, progress.totalBytes);
			}
		} else {
			super.handleMessage(msg);
		}
	}
}

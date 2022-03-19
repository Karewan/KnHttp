package ovh.karewan.knhttp.internal;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import ovh.karewan.knhttp.common.ANConstants;
import ovh.karewan.knhttp.interfaces.UploadProgressListener;
import ovh.karewan.knhttp.model.Progress;

public final class UploadProgressHandler extends Handler {

	private final UploadProgressListener mUploadProgressListener;

	public UploadProgressHandler(UploadProgressListener uploadProgressListener) {
		super(Looper.getMainLooper());
		mUploadProgressListener = uploadProgressListener;
	}

	@Override
	public void handleMessage(Message msg) {
		if (msg.what == ANConstants.UPDATE) {
			if (mUploadProgressListener != null) {
				final Progress progress = (Progress) msg.obj;
				mUploadProgressListener.onProgress(progress.currentBytes, progress.totalBytes);
			}
		} else {
			super.handleMessage(msg);
		}
	}
}

package ovh.karewan.knhttp.internal;

import androidx.annotation.NonNull;

import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.model.Progress;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Throttles and coalesces the progress events of a request: at most one event every
 * {@link #MIN_INTERVAL_MS} ms (plus the final one) and never more than one pending event
 * on the delivery thread.
 */
public final class ProgressReporter {
	public static final long MIN_INTERVAL_MS = 100;
	private static final long MIN_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(MIN_INTERVAL_MS);

	private final KnRequest mRequest;
	private final boolean mUpload;
	private final AtomicBoolean mPending = new AtomicBoolean();
	private final Runnable mDispatchRunnable = this::dispatch;
	private volatile Progress mLatest;
	// IO thread only
	private boolean mEmitted;
	private long mLastEmitNs;

	public ProgressReporter(@NonNull KnRequest request, boolean upload) {
		mRequest = request;
		mUpload = upload;
	}

	/**
	 * Called from the IO thread
	 */
	public void onProgress(long currentBytes, long totalBytes, boolean done) {
		mRequest.updateProgress(currentBytes, totalBytes);
		if (!mRequest.hasProgressListener(mUpload)) return;

		final long now = System.nanoTime();
		if (!done && mEmitted && now - mLastEmitNs < MIN_INTERVAL_NS) return;
		mEmitted = true;
		mLastEmitNs = now;

		mLatest = new Progress(currentBytes, totalBytes);
		if (mPending.compareAndSet(false, true)) mRequest.runOnDeliveryThread(mDispatchRunnable);
	}

	private void dispatch() {
		mPending.set(false);
		final Progress progress = mLatest;
		if (progress != null) mRequest.dispatchProgress(mUpload, progress.currentBytes, progress.totalBytes);
	}
}

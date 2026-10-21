package ovh.karewan.knhttp.internal;

import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import ovh.karewan.knhttp.common.Priority;
import ovh.karewan.knhttp.core.PriorityThreadFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Runs the asynchronous requests.
 * <ul>
 * <li>A single pool of threads, created on demand and reused: a thread stops after 60 s without work.</li>
 * <li>While less than {@link #getMaxConcurrentRequests()} requests are running, a new request starts at once,
 * whatever its priority: a request alone never waits.</li>
 * <li>Above this limit, the requests are queued and started by priority (HIGH, MEDIUM, LOW), then in their order of arrival.</li>
 * <li>{@link Priority#IMMEDIATE} requests always start at once: they ignore the limit and are not counted in it.</li>
 * </ul>
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class RequestDispatcher {
	private static final String TAG = "KnHttp";
	public static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 64;
	private static final long KEEP_ALIVE_SECONDS = 60;

	// Slightly above the background priority: background threads are confined to the little cores,
	// while these threads also parse JSON and decode bitmaps
	private static final int THREAD_PRIORITY = Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_MORE_FAVORABLE;

	private static final Comparator<InternalRunnable> QUEUE_ORDER = (a, b) -> {
		if (a.getPriority() != b.getPriority()) return Integer.compare(b.getPriority().ordinal(), a.getPriority().ordinal());
		return Integer.compare(a.sequence, b.sequence);
	};

	private final ThreadPoolExecutor mExecutor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
			new SynchronousQueue<>(), new PriorityThreadFactory(THREAD_PRIORITY, "KnHttp-"));

	private final Object mLock = new Object();
	// Guarded by mLock
	private final PriorityQueue<InternalRunnable> mQueuedRequests = new PriorityQueue<>(16, QUEUE_ORDER);
	private int mRunningRequests; // Limited requests only (not IMMEDIATE)
	private int mMaxConcurrentRequests = DEFAULT_MAX_CONCURRENT_REQUESTS;

	void enqueue(@NonNull InternalRunnable task) {
		if (task.getPriority() == Priority.IMMEDIATE) {
			mExecutor.execute(new Worker(task, false));
			return;
		}

		synchronized (mLock) {
			if (mRunningRequests >= mMaxConcurrentRequests) {
				mQueuedRequests.add(task);
				return;
			}
			mRunningRequests++;
		}

		startLimited(task);
	}

	/**
	 * Runs a task outside of the limits (internal work like the client prewarm)
	 */
	void execute(@NonNull Runnable runnable) {
		mExecutor.execute(runnable);
	}

	public int getMaxConcurrentRequests() {
		synchronized (mLock) {
			return mMaxConcurrentRequests;
		}
	}

	void setMaxConcurrentRequests(int maxConcurrentRequests) {
		if (maxConcurrentRequests < 1) throw new IllegalArgumentException("maxConcurrentRequests < 1: " + maxConcurrentRequests);

		final List<InternalRunnable> toStart = new ArrayList<>();
		synchronized (mLock) {
			mMaxConcurrentRequests = maxConcurrentRequests;
			while (mRunningRequests < mMaxConcurrentRequests && !mQueuedRequests.isEmpty()) {
				mRunningRequests++;
				toStart.add(mQueuedRequests.poll());
			}
		}

		for (InternalRunnable task : toStart) startLimited(task);
	}

	public int getRunningRequestsCount() {
		synchronized (mLock) {
			return mRunningRequests;
		}
	}

	public int getQueuedRequestsCount() {
		synchronized (mLock) {
			return mQueuedRequests.size();
		}
	}

	public boolean isShutdown() {
		return mExecutor.isShutdown();
	}

	void shutdown() {
		mExecutor.shutdown();
		synchronized (mLock) {
			mQueuedRequests.clear();
		}
	}

	private void startLimited(@NonNull InternalRunnable task) {
		try {
			mExecutor.execute(new Worker(task, true));
		} catch (RejectedExecutionException e) {
			synchronized (mLock) {
				mRunningRequests--;
			}
			throw e;
		}
	}

	/**
	 * Called by a worker when its limited request is done: returns the next request to run on the same thread
	 * (no thread hand-off), or releases the slot.
	 */
	private InternalRunnable next() {
		synchronized (mLock) {
			final InternalRunnable next = mRunningRequests <= mMaxConcurrentRequests ? mQueuedRequests.poll() : null;
			if (next == null) mRunningRequests--;
			return next;
		}
	}

	private final class Worker implements Runnable {
		private final InternalRunnable mFirstTask;
		private final boolean mLimited;

		Worker(InternalRunnable firstTask, boolean limited) {
			mFirstTask = firstTask;
			mLimited = limited;
		}

		@Override
		public void run() {
			InternalRunnable task = mFirstTask;

			while (task != null) {
				try {
					task.run();
				} catch (Throwable t) {
					// Never leak a slot: the dispatcher would end up blocked
					Log.e(TAG, "Unexpected error while running " + task.request, t);
				}

				task = mLimited ? next() : null;
			}
		}
	}
}

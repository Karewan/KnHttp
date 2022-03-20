package ovh.karewan.knhttp.core;

import androidx.annotation.NonNull;

import ovh.karewan.knhttp.common.Priority;
import ovh.karewan.knhttp.internal.InternalRunnable;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class KnExecutor extends ThreadPoolExecutor {
	KnExecutor(int maxNumThreads, ThreadFactory threadFactory) {
		super(maxNumThreads, maxNumThreads, 0, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>(), threadFactory);
	}

	@Override
	@NonNull
	public Future<?> submit(Runnable task) {
		AndroidNetworkingFutureTask futureTask = new AndroidNetworkingFutureTask((InternalRunnable) task);
		execute(futureTask);
		return futureTask;
	}

	private static final class AndroidNetworkingFutureTask extends FutureTask<InternalRunnable> implements Comparable<AndroidNetworkingFutureTask> {
		private final InternalRunnable hunter;

		public AndroidNetworkingFutureTask(InternalRunnable hunter) {
			super(hunter, null);
			this.hunter = hunter;
		}

		@Override
		public int compareTo(AndroidNetworkingFutureTask other) {
			Priority p1 = hunter.getPriority();
			Priority p2 = other.hunter.getPriority();
			return (p1 == p2 ? hunter.sequence - other.hunter.sequence : p2.ordinal() - p1.ordinal());
		}
	}
}

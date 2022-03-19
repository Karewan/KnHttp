package ovh.karewan.knhttp.core;

import android.os.Process;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

public final class DefaultExecutorSupplier implements ExecutorSupplier {

	public static final int DEFAULT_MAX_NUM_THREADS = 2 * Runtime.getRuntime().availableProcessors() + 1;
	private final ANExecutor mNetworkExecutor;
	private final ANExecutor mImmediateNetworkExecutor;
	private final Executor mMainThreadExecutor;

	public DefaultExecutorSupplier() {
		ThreadFactory backgroundPriorityThreadFactory = new PriorityThreadFactory(Process.THREAD_PRIORITY_BACKGROUND);
		mNetworkExecutor = new ANExecutor(DEFAULT_MAX_NUM_THREADS, backgroundPriorityThreadFactory);
		mImmediateNetworkExecutor = new ANExecutor(2, backgroundPriorityThreadFactory);
		mMainThreadExecutor = new MainThreadExecutor();
	}

	@Override
	public ANExecutor forNetworkTasks() {
		return mNetworkExecutor;
	}

	@Override
	public ANExecutor forImmediateNetworkTasks() {
		return mImmediateNetworkExecutor;
	}

	@Override
	public Executor forMainThreadTasks() {
		return mMainThreadExecutor;
	}
}

package ovh.karewan.knhttp.core;

import android.os.Process;

import androidx.annotation.NonNull;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class PriorityThreadFactory implements ThreadFactory {

	private final int mThreadPriority;
	private final String mNamePrefix;
	private final AtomicInteger mThreadCount = new AtomicInteger();

	public PriorityThreadFactory(int threadPriority, @NonNull String namePrefix) {
		mThreadPriority = threadPriority;
		mNamePrefix = namePrefix;
	}

	@Override
	public Thread newThread(@NonNull final Runnable runnable) {
		Runnable wrapperRunnable = () -> {
			try {
				Process.setThreadPriority(mThreadPriority);
			} catch (Throwable t) {
				t.printStackTrace();
			}
			runnable.run();
		};
		return new Thread(wrapperRunnable, mNamePrefix + mThreadCount.incrementAndGet());
	}

}

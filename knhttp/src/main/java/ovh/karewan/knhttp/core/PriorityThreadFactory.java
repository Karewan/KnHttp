package ovh.karewan.knhttp.core;

import android.os.Process;

import androidx.annotation.NonNull;

import java.util.concurrent.ThreadFactory;

public final class PriorityThreadFactory implements ThreadFactory {

    private final int mThreadPriority;

    public PriorityThreadFactory(int threadPriority) {
        mThreadPriority = threadPriority;
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
        return new Thread(wrapperRunnable);
    }

}

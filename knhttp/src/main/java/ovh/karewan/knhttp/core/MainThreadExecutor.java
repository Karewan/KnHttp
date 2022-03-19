package ovh.karewan.knhttp.core;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

public final class MainThreadExecutor implements Executor {

	private final Handler handler = new Handler(Looper.getMainLooper());

	@Override
	public void execute(@NonNull Runnable runnable) {
		handler.post(runnable);
	}
}

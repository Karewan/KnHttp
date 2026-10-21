package ovh.karewan.knhttp.core;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

public final class MainThreadExecutor implements Executor {

	private final Handler handler = new Handler(Looper.getMainLooper());

	/**
	 * Asynchronous messages (API 22+) are not held back while a frame is being prepared (like Kotlin's Dispatchers.Main):
	 * a result received at that time is shown in this frame instead of the next one.
	 */
	@Override
	public void execute(@NonNull Runnable runnable) {
		final Message message = Message.obtain(handler, runnable);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) message.setAsynchronous(true);
		handler.sendMessage(message);
	}
}

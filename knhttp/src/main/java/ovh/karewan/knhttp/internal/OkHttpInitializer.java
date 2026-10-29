package ovh.karewan.knhttp.internal;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.startup.Initializer;

import ovh.karewan.knhttp.core.PriorityThreadFactory;

import java.util.Collections;
import java.util.List;

import okhttp3.OkHttp;

/**
 * Replaces OkHttp's startup initializer (removed in the manifest), which detects the platform on the main thread
 * before Application.onCreate(): OkHttp gets its context from a background thread instead.
 * Without this context, the public suffix list (cookies with a Domain attribute, HttpUrl.topPrivateDomain())
 * fails for the whole process and every OkHttp client of the app, so it is given at startup even if KnHttp is never
 * initialized with a context.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class OkHttpInitializer implements Initializer<Thread> {
	private static final String TAG = "KnHttp";

	/**
	 * @return The thread giving the context to OkHttp, already started
	 */
	@NonNull
	@Override
	public Thread create(@NonNull Context context) {
		final Thread thread = new PriorityThreadFactory(RequestDispatcher.THREAD_PRIORITY, "KnHttp-startup-").newThread(() -> {
			try {
				OkHttp.INSTANCE.initialize(context);
			} catch (Throwable t) {
				// Never crash the app from a startup thread: KnHttp.init(context) tries again
				Log.e(TAG, "Unable to initialize OkHttp", t);
			}
		});

		thread.start();
		return thread;
	}

	@NonNull
	@Override
	public List<Class<? extends Initializer<?>>> dependencies() {
		return Collections.emptyList();
	}
}

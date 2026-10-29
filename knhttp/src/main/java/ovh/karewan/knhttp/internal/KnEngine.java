package ovh.karewan.knhttp.internal;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import ovh.karewan.knhttp.BuildConfig;
import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnSettings;
import ovh.karewan.knhttp.core.MainThreadExecutor;
import ovh.karewan.knhttp.interceptors.HttpLoggingInterceptor.Level;
import ovh.karewan.knhttp.utils.Utils;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttp;
import okhttp3.OkHttpClient;

/**
 * The single internal singleton behind the KnHttp static facade: OkHttp clients, executors
 * and in-flight requests share the same lifecycle.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class KnEngine {
	private static final String TAG = "KnHttp";
	private static final Object sLock = new Object();
	private static volatile KnEngine sInstance;

	private final ClientProvider mClientProvider = new ClientProvider();
	private final RequestDispatcher mDispatcher = new RequestDispatcher();
	private final Executor mMainThreadExecutor = new MainThreadExecutor();
	private final Set<KnRequest> mCurrentRequests = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final AtomicInteger mSequenceGenerator = new AtomicInteger();
	private volatile String mUserAgent = "KnHttp/" + BuildConfig.VERSION_NAME;

	private KnEngine() {}

	@NonNull
	public static KnEngine get() {
		KnEngine engine = sInstance;
		if (engine != null) return engine;

		synchronized (sLock) {
			if (sInstance == null) sInstance = new KnEngine();
			return sInstance;
		}
	}

	@Nullable
	public static KnEngine peek() {
		return sInstance;
	}

	public static void shutDown() {
		final KnEngine engine;

		synchronized (sLock) {
			engine = sInstance;
			sInstance = null;
		}

		if (engine == null) return;
		engine.cancelAll(true);
		engine.mDispatcher.shutdown();
		engine.mClientProvider.shutdown();
	}

	public void init(@Nullable Context context, @Nullable KnSettings settings) {
		mClientProvider.configure(context, settings);
		if (context != null) KnImageLoader.registerMemoryCallbacks(context);
		prewarm(context);
	}

	public void init(@NonNull OkHttpClient okHttpClient) {
		mClientProvider.configure(okHttpClient);
	}

	/**
	 * Builds the client (Conscrypt native library loading, TLS context...) off the main thread.
	 * OkHttp's context is normally given by OkHttpInitializer, given again here in case the app disables androidx.startup
	 * (does nothing when it is already set).
	 */
	private void prewarm(@Nullable Context context) {
		try {
			mDispatcher.execute(() -> {
				try {
					if (context != null) OkHttp.INSTANCE.initialize(context);
					mClientProvider.getBaseClient();
				} catch (Throwable t) {
					// Never crash the app from a pool thread: the first request will report the error
					Log.e(TAG, "Unable to build the OkHttp client", t);
				}
			});
		} catch (RejectedExecutionException ignore) {
		}
	}

	@NonNull
	public ClientProvider getClientProvider() {
		return mClientProvider;
	}

	@NonNull
	public OkHttpClient getClient() {
		return mClientProvider.getBaseClient();
	}

	@NonNull
	public RequestDispatcher getDispatcher() {
		return mDispatcher;
	}

	@NonNull
	public Executor getMainThreadExecutor() {
		return mMainThreadExecutor;
	}

	public void setMaxConcurrentRequests(int maxConcurrentRequests) {
		mDispatcher.setMaxConcurrentRequests(maxConcurrentRequests);
	}

	@Nullable
	public String getUserAgent() {
		return mUserAgent;
	}

	public void setUserAgent(@Nullable String userAgent) {
		mUserAgent = userAgent;
	}

	public void enableLogging(@NonNull Level level) {
		mClientProvider.enableLogging(level);
	}

	public void submit(@NonNull KnRequest request) {
		mCurrentRequests.add(request);
		request.setSequenceNumber(mSequenceGenerator.incrementAndGet());

		try {
			mDispatcher.enqueue(new InternalRunnable(request));
		} catch (RejectedExecutionException e) {
			// KnHttp is shutting down: the listener must still be called
			request.deliverRejection(Utils.getErrorForCancellation());
		}
	}

	public void finish(@NonNull KnRequest request) {
		mCurrentRequests.remove(request);
	}

	public void cancelAll(boolean forceCancel) {
		for (KnRequest request : mCurrentRequests) request.cancel(forceCancel);
	}

	public void cancelRequestWithGivenTag(@NonNull Object tag, boolean forceCancel) {
		for (KnRequest request : mCurrentRequests) {
			if (tag.equals(request.getTag())) request.cancel(forceCancel);
		}
	}

	public boolean isRequestRunning(@NonNull Object tag) {
		for (KnRequest request : mCurrentRequests) {
			if (tag.equals(request.getTag()) && request.isRunning()) return true;
		}

		return false;
	}
}

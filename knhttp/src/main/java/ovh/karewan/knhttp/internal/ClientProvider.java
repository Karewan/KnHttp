package ovh.karewan.knhttp.internal;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.conscrypt.Conscrypt;

import ovh.karewan.knhttp.common.KnConstants;
import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnSettings;
import ovh.karewan.knhttp.interceptors.HttpLoggingInterceptor;
import ovh.karewan.knhttp.interceptors.HttpLoggingInterceptor.Level;
import ovh.karewan.knhttp.utils.Utils;

import java.io.File;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cache;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.brotli.BrotliInterceptor;

/**
 * Builds and caches the OkHttp clients used by KnHttp.
 * <ul>
 * <li>base: the client configured by the user (also the one exposed by KnHttp.getOkHttpClient())</li>
 * <li>extended: the base client + per-request timeouts + download progress interceptors, only used by
 * the requests which need them so the other requests pay nothing</li>
 * <li>derived: the same for the custom clients set per request, built once per client</li>
 * </ul>
 * All of them share the same connection pool and dispatcher.
 */
public final class ClientProvider {
	private static final String TAG = "KnHttp";

	// Key exchange groups, by preference. Without them, Conscrypt (BoringSSL) offers X25519MLKEM768 first.
	private static final String[] CLASSIC_GROUPS = { "X25519", "P-256", "P-384" };
	private static final String[] POST_QUANTUM_GROUPS = { "X25519MLKEM768", "X25519", "P-256", "P-384" };

	private final Object mLock = new Object();
	private final HttpLoggingInterceptor mLogging = new HttpLoggingInterceptor();
	private final RequestTimeoutInterceptor mTimeoutInterceptor = new RequestTimeoutInterceptor();
	private final DownloadProgressInterceptor mDownloadProgressInterceptor = new DownloadProgressInterceptor();

	// Guarded by mLock
	private KnSettings mSettings = new KnSettings.Builder().build();
	private Context mContext;
	private OkHttpClient mUserClient;
	private boolean mOwnsBaseClient;
	private boolean mLoggingInstalled;
	private final Map<OkHttpClient, OkHttpClient> mDerivedClients = new WeakHashMap<>();
	// Kept across the configurations: two Cache instances on the same directory would corrupt it
	private Cache mCache;

	private volatile OkHttpClient mBaseClient;
	private volatile OkHttpClient mExtendedClient;

	void configure(@Nullable Context context, @Nullable KnSettings settings) {
		synchronized (mLock) {
			release();
			mContext = context != null ? context.getApplicationContext() : null;
			mSettings = settings != null ? settings : new KnSettings.Builder().build();
			mUserClient = null;
		}
	}

	void configure(@NonNull OkHttpClient okHttpClient) {
		synchronized (mLock) {
			release();
			mContext = null;
			mSettings = new KnSettings.Builder().build();
			mUserClient = okHttpClient;
		}
	}

	/**
	 * The base client, built on first use
	 */
	@NonNull
	public OkHttpClient getBaseClient() {
		final OkHttpClient client = mBaseClient;
		if (client != null) return client;

		synchronized (mLock) {
			if (mBaseClient == null) mBaseClient = buildBaseClient();
			return mBaseClient;
		}
	}

	@NonNull
	OkHttpClient getExtendedClient() {
		final OkHttpClient client = mExtendedClient;
		if (client != null) return client;

		synchronized (mLock) {
			if (mBaseClient == null) mBaseClient = buildBaseClient();
			if (mExtendedClient == null) mExtendedClient = extend(mBaseClient.newBuilder());
			return mExtendedClient;
		}
	}

	@NonNull
	OkHttpClient clientFor(@NonNull KnRequest request) {
		final OkHttpClient customClient = request.getOkHttpClient();
		if (customClient != null) return derivedClient(customClient);
		return request.needsExtendedClient() ? getExtendedClient() : getBaseClient();
	}

	void enableLogging(@NonNull Level level) {
		mLogging.setLevel(level);
		if (level == Level.NONE) return;

		synchronized (mLock) {
			// The derived clients are rebuilt with the logging interceptor
			mDerivedClients.clear();

			// Not built yet: the logging interceptor will be added on build
			if (mLoggingInstalled || mBaseClient == null) return;

			mBaseClient = mBaseClient.newBuilder().addInterceptor(mLogging).build();
			mExtendedClient = null;
			mLoggingInstalled = true;
		}
	}

	void shutdown() {
		synchronized (mLock) {
			release();
			Utils.closeQuietly(mCache);
			mCache = null;
		}
	}

	// Must be called with mLock held
	private void release() {
		final OkHttpClient base = mBaseClient;
		if (base != null && mOwnsBaseClient) {
			try {
				base.connectionPool().evictAll();
			} catch (Exception e) {
				Log.w(TAG, "Unable to evict the idle connections", e);
			}
		}

		mBaseClient = null;
		mExtendedClient = null;
		mOwnsBaseClient = false;
		mLoggingInstalled = false;
		mDerivedClients.clear();
	}

	// Must be called with mLock held
	@NonNull
	private OkHttpClient buildBaseClient() {
		final boolean logging = mLogging.getLevel() != Level.NONE;
		mLoggingInstalled = logging;
		mOwnsBaseClient = mUserClient == null;

		// Keep the exact instance given by the user when there is nothing to add
		if (mUserClient != null && !logging) return mUserClient;

		final Cache cache = mSettings.isCacheEnabled() && mContext != null ? sharedCache(mContext) : null;
		final OkHttpClient.Builder builder = mUserClient != null ? mUserClient.newBuilder() : newDefaultBuilder(mSettings, cache);
		if (logging) builder.addInterceptor(mLogging);
		return builder.build();
	}

	@NonNull
	private OkHttpClient derivedClient(@NonNull OkHttpClient customClient) {
		final Cache cache = getBaseClient().cache();

		synchronized (mLock) {
			OkHttpClient client = mDerivedClients.get(customClient);

			if (client == null) {
				final OkHttpClient.Builder builder = customClient.newBuilder();
				if (customClient.cache() == null && cache != null) builder.cache(cache);
				// Logged like the other requests (the custom client may already come from KnHttp.getOkHttpClient())
				if (mLogging.getLevel() != Level.NONE && !builder.interceptors().contains(mLogging)) builder.addInterceptor(mLogging);
				client = extend(builder);
				mDerivedClients.put(customClient, client);
			}

			return client;
		}
	}

	@NonNull
	private OkHttpClient extend(@NonNull OkHttpClient.Builder builder) {
		return builder
				.addInterceptor(mTimeoutInterceptor)
				.addNetworkInterceptor(mDownloadProgressInterceptor)
				.build();
	}

	// Must be called with mLock held
	@NonNull
	private Cache sharedCache(@NonNull Context context) {
		final File directory = Utils.getDiskCacheDir(context, KnConstants.CACHE_DIR_NAME);

		if (mCache == null || !mCache.directory().equals(directory)) {
			Utils.closeQuietly(mCache);
			mCache = new Cache(directory, KnConstants.MAX_CACHE_SIZE);
		}

		return mCache;
	}

	@NonNull
	private static OkHttpClient.Builder newDefaultBuilder(@NonNull KnSettings settings, @Nullable Cache cache) {
		final List<ConnectionSpec> connectionSpecs = new ArrayList<>(2);
		connectionSpecs.add(settings.isObsoleteTlsAllowed() ? ConnectionSpec.COMPATIBLE_TLS : ConnectionSpec.RESTRICTED_TLS);
		if (settings.isCleartextTrafficAllowed()) connectionSpecs.add(ConnectionSpec.CLEARTEXT);

		// Never new OkHttpClient().newBuilder(): it would build a full client (system trust store, pools...) for nothing
		final OkHttpClient.Builder builder = new OkHttpClient.Builder()
				.connectionPool(new ConnectionPool(settings.getMaxIdleConnections(), 5, TimeUnit.MINUTES))
				.callTimeout(settings.getCallTimeout(), TimeUnit.MILLISECONDS)
				.connectTimeout(settings.getConnectTimeout(), TimeUnit.MILLISECONDS)
				.readTimeout(settings.getReadTimeout(), TimeUnit.MILLISECONDS)
				.writeTimeout(settings.getWriteTimeout(), TimeUnit.MILLISECONDS)
				.followRedirects(settings.isFollowRedirect())
				.connectionSpecs(connectionSpecs);

		if (cache != null) builder.cache(cache);

		// Brotli
		if (settings.isBrotliEnabled()) {
			builder.addInterceptor(BrotliInterceptor.INSTANCE);
		}

		configureTls(builder, settings);

		return builder;
	}

	/**
	 * Uses the bundled Conscrypt on every Android version: same TLS 1.3, ECC, X25519 (and post-quantum
	 * key exchange when enabled) support everywhere, even on devices whose system TLS stack is never updated.
	 */
	private static void configureTls(@NonNull OkHttpClient.Builder builder, @NonNull KnSettings settings) {
		final boolean allowInvalidCertificates = settings.isInvalidCertificatesAllowed();
		if (allowInvalidCertificates) builder.hostnameVerifier((hostname, session) -> true);

		try {
			final Provider provider = ConscryptHolder.get(settings.isObsoleteTlsAllowed());
			final X509TrustManager trustManager = allowInvalidCertificates ? new InternalUnsafeX509() : Conscrypt.getDefaultX509TrustManager();
			final SSLContext sslContext = SSLContext.getInstance("TLS", provider);
			sslContext.init(null, new TrustManager[] { trustManager }, null);
			final SSLSocketFactory socketFactory = sslContext.getSocketFactory();
			setKeyExchangeGroups(socketFactory, settings.isPostQuantumKeyExchangeEnabled());
			builder.sslSocketFactory(socketFactory, trustManager);
			return;
		} catch (Throwable t) {
			// e.g. no Conscrypt native library for the device ABI: better the platform TLS stack than a crash
			Log.e(TAG, "Conscrypt is unavailable, falling back to the platform TLS stack", t);
		}

		if (!allowInvalidCertificates) return;

		try {
			final X509TrustManager trustManager = new InternalUnsafeX509();
			final SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, new TrustManager[] { trustManager }, null);
			builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
		} catch (Exception e) {
			Log.e(TAG, "Unable to configure the platform TLS stack", e);
		}
	}

	private static void setKeyExchangeGroups(@NonNull SSLSocketFactory socketFactory, boolean postQuantum) {
		try {
			Conscrypt.setNamedGroups(socketFactory, postQuantum ? POST_QUANTUM_GROUPS : CLASSIC_GROUPS);
		} catch (Throwable t) {
			// Never worth losing Conscrypt: keep its default groups
			Log.w(TAG, "Unable to set the TLS key exchange groups", t);
		}
	}

	private static final class ConscryptHolder {
		private static Provider sProvider;
		private static boolean sObsoleteTls;

		static synchronized Provider get(boolean obsoleteTls) {
			if (sProvider == null || sObsoleteTls != obsoleteTls) {
				// Since Conscrypt 2.6, TLS 1.0 / 1.1 are unsupported unless explicitly enabled on the provider
				final Provider provider = obsoleteTls
						? Conscrypt.newProviderBuilder().isTlsV1Deprecated(false).isTlsV1Enabled(true).build()
						: Conscrypt.newProvider();

				// Installed once as the default security provider (same behavior as before)
				if (Security.getProvider(provider.getName()) == null) Security.insertProviderAt(provider, 1);

				sProvider = provider;
				sObsoleteTls = obsoleteTls;
			}

			return sProvider;
		}
	}
}

package ovh.karewan.knhttp.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import ovh.karewan.knhttp.KnHttp;
import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnSettings;
import ovh.karewan.knhttp.interceptors.HttpLoggingInterceptor;

@RunWith(RobolectricTestRunner.class)
public class ClientProviderTest {

	@After
	public void tearDown() {
		KnHttp.shutDown();
	}

	private static ClientProvider provider() {
		return KnEngine.get().getClientProvider();
	}

	private static long count(Iterable<Interceptor> interceptors, Class<?> type) {
		long count = 0;
		for (Interceptor interceptor : interceptors) if (type.isInstance(interceptor)) count++;
		return count;
	}

	@Test
	public void reInit_reusesTheSameDiskCache_andShutDownClosesIt() {
		final Context context = ApplicationProvider.getApplicationContext();
		KnHttp.init(context, new KnSettings.Builder().setEnableCache(true).build());
		final Cache cache = KnHttp.getOkHttpClient().cache();
		assertNotNull(cache);

		KnHttp.init(context, new KnSettings.Builder().setEnableCache(true).setConnectTimeout(5000).build());
		assertSame(cache, KnHttp.getOkHttpClient().cache());

		KnHttp.shutDown();
		assertTrue(cache.isClosed());
	}

	@Test
	public void plainRequests_useTheBaseClient_withoutAnyInterceptorOverhead() {
		KnHttp.init(ApplicationProvider.getApplicationContext());

		final KnRequest request = KnHttp.get("https://example.com").build();

		assertSame(KnHttp.getOkHttpClient(), provider().clientFor(request));
		assertEquals(0, count(KnHttp.getOkHttpClient().interceptors(), RequestTimeoutInterceptor.class));
	}

	@Test
	public void requestsWithTimeouts_shareOneExtendedClient() {
		KnHttp.init(ApplicationProvider.getApplicationContext());

		final OkHttpClient first = provider().clientFor(KnHttp.get("https://example.com").setReadTimeout(1, TimeUnit.SECONDS).build());
		final OkHttpClient second = provider().clientFor(KnHttp.post("https://example.com").setConnectTimeout(2, TimeUnit.SECONDS).build());

		assertSame(first, second);
		assertNotSame(KnHttp.getOkHttpClient(), first);
		assertSame(KnHttp.getOkHttpClient().connectionPool(), first.connectionPool());
		assertEquals(1, count(first.interceptors(), RequestTimeoutInterceptor.class));
	}

	@Test
	public void downloadsWithoutListenerNorThreshold_useTheBaseClient() {
		KnHttp.init(ApplicationProvider.getApplicationContext());

		final KnRequest plain = KnHttp.download("https://example.com", "/tmp", "file").build();
		final KnRequest withListener = KnHttp.download("https://example.com", "/tmp", "file").build().setDownloadProgressListener((d, t) -> {});

		assertSame(KnHttp.getOkHttpClient(), provider().clientFor(plain));
		assertNotSame(KnHttp.getOkHttpClient(), provider().clientFor(withListener));
	}

	@Test
	public void perRequestCustomClients_areDerivedOnce() {
		KnHttp.init(ApplicationProvider.getApplicationContext());
		final OkHttpClient custom = new OkHttpClient.Builder().build();

		final OkHttpClient first = provider().clientFor(KnHttp.get("https://example.com").setOkHttpClient(custom).build());
		final OkHttpClient second = provider().clientFor(KnHttp.get("https://example.com").setOkHttpClient(custom).build());

		assertSame(first, second);
		assertSame(custom.connectionPool(), first.connectionPool());
	}

	@Test
	public void initWithACustomClient_exposesTheSameInstance() {
		final OkHttpClient custom = new OkHttpClient.Builder().build();
		KnHttp.init(custom);

		assertSame(custom, KnHttp.getOkHttpClient());
		assertSame(KnHttp.getOkHttpClient(), KnHttp.getOkHttpClient());
	}

	@Test
	public void enableLogging_addsASingleInterceptor_beforeOrAfterInit() {
		KnHttp.enableLogging(HttpLoggingInterceptor.Level.HEADERS);
		KnHttp.init(ApplicationProvider.getApplicationContext());
		assertEquals(1, count(KnHttp.getOkHttpClient().interceptors(), HttpLoggingInterceptor.class));

		final OkHttpClient before = KnHttp.getOkHttpClient();
		KnHttp.enableLogging(HttpLoggingInterceptor.Level.BODY);
		KnHttp.enableLogging();
		assertSame(before, KnHttp.getOkHttpClient());
		assertEquals(1, count(KnHttp.getOkHttpClient().interceptors(), HttpLoggingInterceptor.class));
	}

	@Test
	public void enableLogging_afterTheClientIsBuilt_keepsTheConnectionPool() {
		KnHttp.init(ApplicationProvider.getApplicationContext());
		final OkHttpClient before = KnHttp.getOkHttpClient();

		KnHttp.enableLogging();

		assertNotSame(before, KnHttp.getOkHttpClient());
		assertSame(before.connectionPool(), KnHttp.getOkHttpClient().connectionPool());
		assertEquals(1, count(KnHttp.getOkHttpClient().interceptors(), HttpLoggingInterceptor.class));
	}
}

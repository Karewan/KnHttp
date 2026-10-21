package ovh.karewan.knhttp.sample;

import android.app.Application;

import ovh.karewan.knhttp.KnHttp;
import ovh.karewan.knhttp.common.KnSettings;
import ovh.karewan.knhttp.interceptors.HttpLoggingInterceptor;

public class SampleApp extends Application {

	@Override
	public void onCreate() {
		super.onCreate();

		KnHttp.init(this, new KnSettings.Builder()
				.setAllowCleartextTraffic(true) // HTTP and LAN tests
				.setFollowRedirect(true) // picsum.photos redirects to its CDN
				.setEnableCache(true)
				.build());

		KnHttp.enableLogging(HttpLoggingInterceptor.Level.BASIC); // Logcat, tag KnHttp
		KnHttp.setUserAgent("KnHttpSample/" + BuildConfig.VERSION_NAME);
	}
}

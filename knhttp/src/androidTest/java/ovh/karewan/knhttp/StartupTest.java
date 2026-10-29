package ovh.karewan.knhttp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.startup.AppInitializer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import okhttp3.HttpUrl;
import okhttp3.internal.platform.PlatformInitializer;
import ovh.karewan.knhttp.internal.OkHttpInitializer;

/**
 * Checks on a merged APK manifest that KnHttp replaces OkHttp's startup initializer (main thread) by its own,
 * and that OkHttp still gets its context without KnHttp.init().
 */
@RunWith(AndroidJUnit4.class)
public class StartupTest {
	private AppInitializer initializer;

	@Before
	public void setUp() {
		final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		initializer = AppInitializer.getInstance(context);
	}

	@Test
	public void okHttpInitializerReplacesTheOkHttpOne() {
		assertTrue(initializer.isEagerlyInitialized(OkHttpInitializer.class));
		assertFalse(initializer.isEagerlyInitialized(PlatformInitializer.class));
	}

	@Test
	public void okHttpGetsItsContextAtStartup() throws Exception {
		// Already created at startup: returns the thread started then
		initializer.initializeComponent(OkHttpInitializer.class).join(5000);

		// Public suffix list, read from OkHttp's assets with the context
		assertEquals("example.co.uk", HttpUrl.get("https://www.example.co.uk/").topPrivateDomain());
	}
}

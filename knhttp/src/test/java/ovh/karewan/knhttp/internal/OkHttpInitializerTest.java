package ovh.karewan.knhttp.internal;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import okhttp3.internal.platform.PlatformRegistry;

@RunWith(RobolectricTestRunner.class)
public class OkHttpInitializerTest {

	@Test
	public void givesTheContextToOkHttpFromABackgroundThread() throws Exception {
		final Context context = ApplicationProvider.getApplicationContext();
		PlatformRegistry.INSTANCE.setApplicationContext(null);

		final Thread thread = new OkHttpInitializer().create(context);
		assertNotSame(Thread.currentThread(), thread);
		thread.join(5000);

		assertSame(context, PlatformRegistry.INSTANCE.getApplicationContext());
	}
}

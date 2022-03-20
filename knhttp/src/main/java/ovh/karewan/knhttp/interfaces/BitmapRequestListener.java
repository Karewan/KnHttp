package ovh.karewan.knhttp.interfaces;

import android.graphics.Bitmap;

import okhttp3.Response;
import ovh.karewan.knhttp.error.KnError;

@SuppressWarnings("unused")
public interface BitmapRequestListener {
	void onResponse(Bitmap bitmap, Response okHttpRes);
	void onError(KnError err);
}

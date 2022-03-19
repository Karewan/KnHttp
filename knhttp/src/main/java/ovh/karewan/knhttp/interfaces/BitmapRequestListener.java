package ovh.karewan.knhttp.interfaces;

import android.graphics.Bitmap;

import okhttp3.Response;
import ovh.karewan.knhttp.error.ANError;

public interface BitmapRequestListener {
	void onResponse(Bitmap response, Response okHttpResponse);
	void onError(ANError anError);
}

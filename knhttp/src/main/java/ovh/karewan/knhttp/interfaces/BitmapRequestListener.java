package ovh.karewan.knhttp.interfaces;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import okhttp3.Response;
import ovh.karewan.knhttp.error.KnError;

@SuppressWarnings("unused")
public interface BitmapRequestListener {
	void onResponse(@NonNull Bitmap bitmap, @NonNull Response okHttpRes);
	void onError(@NonNull KnError err);
}

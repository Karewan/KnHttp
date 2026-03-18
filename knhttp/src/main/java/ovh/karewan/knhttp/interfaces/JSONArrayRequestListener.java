package ovh.karewan.knhttp.interfaces;

import androidx.annotation.NonNull;

import com.alibaba.fastjson2.JSONArray;

import okhttp3.Response;
import ovh.karewan.knhttp.error.KnError;

public interface JSONArrayRequestListener {
	void onResponse(@NonNull JSONArray arr, @NonNull Response okHttpRes);
	void onError(@NonNull KnError err);
}

package ovh.karewan.knhttp.interfaces;

import androidx.annotation.NonNull;

import com.alibaba.fastjson2.JSONObject;

import okhttp3.Response;
import ovh.karewan.knhttp.error.KnError;

public interface JSONObjectRequestListener {
	void onResponse(@NonNull JSONObject obj, @NonNull Response okHttpRes);
	void onError(@NonNull KnError err);
}

package ovh.karewan.knhttp.interfaces;

import com.alibaba.fastjson.JSONArray;

import okhttp3.Response;
import ovh.karewan.knhttp.error.KnError;

public interface JSONArrayRequestListener {
	void onResponse(JSONArray arr, Response okHttpRes);
	void onError(KnError err);
}

package ovh.karewan.knhttp.interfaces;

import com.alibaba.fastjson.JSONObject;

import okhttp3.Response;
import ovh.karewan.knhttp.error.KnError;

public interface JSONObjectRequestListener {
	void onResponse(JSONObject obj, Response okHttpRes);
	void onError(KnError err);
}

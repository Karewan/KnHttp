package ovh.karewan.knhttp.interfaces;

import com.alibaba.fastjson.JSONObject;

import okhttp3.Response;
import ovh.karewan.knhttp.error.ANError;

public interface JSONObjectRequestListener {
	void onResponse(JSONObject response, Response okHttpResponse);
	void onError(ANError anError);
}

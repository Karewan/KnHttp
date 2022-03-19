package ovh.karewan.knhttp.interfaces;

import com.alibaba.fastjson.JSONArray;

import okhttp3.Response;
import ovh.karewan.knhttp.error.ANError;

public interface JSONArrayRequestListener {
	void onResponse(JSONArray response, Response okHttpResponse);
	void onError(ANError anError);
}

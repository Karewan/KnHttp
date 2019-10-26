/*
    KnHttp

    Copyright (c) 2019 Florent VIALATTE
    Copyright (c) 2016-2019 Amit Shekhar

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */
package ovh.karewan.knhttp.interfaces;

import com.alibaba.fastjson.JSONArray;

import okhttp3.Response;
import ovh.karewan.knhttp.error.ANError;

public interface JSONArrayRequestListener {
    void onResponse(JSONArray response, Response okHttpResponse);
    void onError(ANError anError);
}

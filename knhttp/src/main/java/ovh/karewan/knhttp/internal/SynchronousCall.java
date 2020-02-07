/*
    KnHttp

    Copyright (c) 2019-2020 Florent VIALATTE
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
package ovh.karewan.knhttp.internal;

import ovh.karewan.knhttp.common.ANConstants;
import ovh.karewan.knhttp.common.ANRequest;
import ovh.karewan.knhttp.common.ANResponse;
import ovh.karewan.knhttp.common.ResponseType;
import ovh.karewan.knhttp.error.ANError;
import ovh.karewan.knhttp.utils.SourceCloseUtil;
import ovh.karewan.knhttp.utils.Utils;

import okhttp3.Response;

import static ovh.karewan.knhttp.common.RequestType.DOWNLOAD;
import static ovh.karewan.knhttp.common.RequestType.MULTIPART;
import static ovh.karewan.knhttp.common.RequestType.SIMPLE;

@SuppressWarnings("unchecked")
public final class SynchronousCall {

    private SynchronousCall() {}

    public static <T> ANResponse<T> execute(ANRequest request) {
        switch (request.getRequestType()) {
            case SIMPLE:
                return executeSimpleRequest(request);
            case DOWNLOAD:
                return executeDownloadRequest(request);
            case MULTIPART:
                return executeUploadRequest(request);
        }

        return new ANResponse<>(new ANError());
    }

    private static <T> ANResponse<T> executeSimpleRequest(ANRequest request) {
        Response okHttpResponse = null;
        try {
            okHttpResponse = InternalNetworking.getInstance().performSimpleRequest(request);
            if (okHttpResponse == null) return new ANResponse<>(Utils.getErrorForConnection(new ANError()));

            if (request.getResponseAs() == ResponseType.OK_HTTP_RESPONSE) {
                ANResponse response = new ANResponse(okHttpResponse);
                response.setOkHttpResponse(okHttpResponse);
                return response;
            }

            if (okHttpResponse.code() >= 400) {
                ANResponse response = new ANResponse<>(Utils.getErrorForServerResponse(new ANError(okHttpResponse), request, okHttpResponse.code()));
                response.setOkHttpResponse(okHttpResponse);
                return response;
            }

            ANResponse response = request.parseResponse(okHttpResponse);
            response.setOkHttpResponse(okHttpResponse);
            return response;
        } catch (Exception se) {
            return new ANResponse<>(Utils.getErrorForConnection(new ANError(se)));
        } finally {
            SourceCloseUtil.close(okHttpResponse, request);
        }
    }

    private static <T> ANResponse<T> executeDownloadRequest(ANRequest request) {
        Response okHttpResponse;
        try {
            okHttpResponse = InternalNetworking.getInstance().performDownloadRequest(request);
            if (okHttpResponse == null) return new ANResponse<>(Utils.getErrorForConnection(new ANError()));

            if (okHttpResponse.code() >= 400) {
                ANResponse response = new ANResponse<>(Utils.getErrorForServerResponse(new ANError(okHttpResponse), request, okHttpResponse.code()));
                response.setOkHttpResponse(okHttpResponse);
                return response;
            }

            ANResponse response = new ANResponse(ANConstants.SUCCESS);
            response.setOkHttpResponse(okHttpResponse);
            return response;
        } catch (Exception se) {
            return new ANResponse<>(Utils.getErrorForConnection(new ANError(se)));
        }
    }

    private static <T> ANResponse<T> executeUploadRequest(ANRequest request) {
        Response okHttpResponse = null;
        try {
            okHttpResponse = InternalNetworking.getInstance().performUploadRequest(request);

            if (okHttpResponse == null) return new ANResponse<>(Utils.getErrorForConnection(new ANError()));

            if (request.getResponseAs() == ResponseType.OK_HTTP_RESPONSE) {
                ANResponse response = new ANResponse(okHttpResponse);
                response.setOkHttpResponse(okHttpResponse);
                return response;
            }

            if (okHttpResponse.code() >= 400) {
                ANResponse response = new ANResponse<>(Utils.getErrorForServerResponse(new ANError(okHttpResponse), request, okHttpResponse.code()));
                response.setOkHttpResponse(okHttpResponse);
                return response;
            }

            ANResponse response = request.parseResponse(okHttpResponse);
            response.setOkHttpResponse(okHttpResponse);
            return response;
        } catch (ANError se) {
            return new ANResponse<>(Utils.getErrorForConnection(se));
        } catch (Exception e) {
            return new ANResponse<>(Utils.getErrorForConnection(new ANError(e)));
        } finally {
            SourceCloseUtil.close(okHttpResponse, request);
        }
    }
}

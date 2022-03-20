package ovh.karewan.knhttp.internal;

import ovh.karewan.knhttp.common.KnConstants;
import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnResponse;
import ovh.karewan.knhttp.common.ResponseType;
import ovh.karewan.knhttp.error.KnError;
import ovh.karewan.knhttp.utils.SourceCloseUtil;
import ovh.karewan.knhttp.utils.Utils;

import okhttp3.Response;

import static ovh.karewan.knhttp.common.RequestType.DOWNLOAD;
import static ovh.karewan.knhttp.common.RequestType.MULTIPART;
import static ovh.karewan.knhttp.common.RequestType.SIMPLE;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class SynchronousCall {

	private SynchronousCall() {}

	public static <T> KnResponse<T> execute(KnRequest request) {
		switch (request.getRequestType()) {
			case SIMPLE:
				return executeSimpleRequest(request);
			case DOWNLOAD:
				return executeDownloadRequest(request);
			case MULTIPART:
				return executeUploadRequest(request);
		}

		return new KnResponse<>(new KnError());
	}

	private static <T> KnResponse<T> executeSimpleRequest(KnRequest request) {
		Response okHttpResponse = null;
		try {
			okHttpResponse = InternalNetworking.gi().performSimpleRequest(request);
			if (okHttpResponse == null) return new KnResponse<>(Utils.getErrorForConnection(new KnError()));

			if (request.getResponseAs() == ResponseType.OK_HTTP_RESPONSE) {
				KnResponse response = new KnResponse(okHttpResponse);
				response.setOkHttpResponse(okHttpResponse);
				return response;
			}

			if (okHttpResponse.code() >= 400) {
				KnResponse response = new KnResponse<>(Utils.getErrorForServerResponse(new KnError(okHttpResponse), request, okHttpResponse.code()));
				response.setOkHttpResponse(okHttpResponse);
				return response;
			}

			KnResponse response = request.parseResponse(okHttpResponse);
			response.setOkHttpResponse(okHttpResponse);
			return response;
		} catch (Exception se) {
			return new KnResponse<>(Utils.getErrorForConnection(new KnError(se)));
		} finally {
			SourceCloseUtil.close(okHttpResponse, request);
		}
	}

	private static <T> KnResponse<T> executeDownloadRequest(KnRequest request) {
		Response okHttpResponse;
		try {
			okHttpResponse = InternalNetworking.gi().performDownloadRequest(request);
			if (okHttpResponse == null) return new KnResponse<>(Utils.getErrorForConnection(new KnError()));

			if (okHttpResponse.code() >= 400) {
				KnResponse response = new KnResponse<>(Utils.getErrorForServerResponse(new KnError(okHttpResponse), request, okHttpResponse.code()));
				response.setOkHttpResponse(okHttpResponse);
				return response;
			}

			KnResponse response = new KnResponse(KnConstants.SUCCESS);
			response.setOkHttpResponse(okHttpResponse);
			return response;
		} catch (Exception se) {
			return new KnResponse<>(Utils.getErrorForConnection(new KnError(se)));
		}
	}

	private static <T> KnResponse<T> executeUploadRequest(KnRequest request) {
		Response okHttpResponse = null;
		try {
			okHttpResponse = InternalNetworking.gi().performUploadRequest(request);

			if (okHttpResponse == null) return new KnResponse<>(Utils.getErrorForConnection(new KnError()));

			if (request.getResponseAs() == ResponseType.OK_HTTP_RESPONSE) {
				KnResponse response = new KnResponse(okHttpResponse);
				response.setOkHttpResponse(okHttpResponse);
				return response;
			}

			if (okHttpResponse.code() >= 400) {
				KnResponse response = new KnResponse<>(Utils.getErrorForServerResponse(new KnError(okHttpResponse), request, okHttpResponse.code()));
				response.setOkHttpResponse(okHttpResponse);
				return response;
			}

			KnResponse response = request.parseResponse(okHttpResponse);
			response.setOkHttpResponse(okHttpResponse);
			return response;
		} catch (KnError se) {
			return new KnResponse<>(Utils.getErrorForConnection(se));
		} catch (Exception e) {
			return new KnResponse<>(Utils.getErrorForConnection(new KnError(e)));
		} finally {
			SourceCloseUtil.close(okHttpResponse, request);
		}
	}
}

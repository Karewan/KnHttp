package ovh.karewan.knhttp.internal;

import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnResponse;
import ovh.karewan.knhttp.common.Priority;
import ovh.karewan.knhttp.common.ResponseType;
import ovh.karewan.knhttp.core.Core;
import ovh.karewan.knhttp.error.KnError;
import ovh.karewan.knhttp.utils.SourceCloseUtil;
import ovh.karewan.knhttp.utils.Utils;

import okhttp3.Response;
import ovh.karewan.knhttp.common.RequestType;

@SuppressWarnings("rawtypes")
public class InternalRunnable implements Runnable {

	private final Priority priority;
	public final int sequence;
	public final KnRequest request;

	public InternalRunnable(KnRequest request) {
		this.request = request;
		this.sequence = request.getSequenceNumber();
		this.priority = request.getPriority();
	}

	@Override
	public void run() {
		request.setRunning(true);
		switch (request.getRequestType()) {
			case RequestType.SIMPLE:
				executeSimpleRequest();
				break;
			case RequestType.DOWNLOAD:
				executeDownloadRequest();
				break;
			case RequestType.MULTIPART:
				executeUploadRequest();
				break;
		}
		request.setRunning(false);
	}

	private void executeSimpleRequest() {
		Response okHttpResponse = null;
		try {
			okHttpResponse = InternalNetworking.gi().performSimpleRequest(request);

			if (okHttpResponse == null) {
				deliverError(request, Utils.getErrorForConnection(new KnError()));
				return;
			}

			if (request.getResponseAs() == ResponseType.OK_HTTP_RESPONSE) {
				request.deliverOkHttpResponse(okHttpResponse);
				return;
			}

			if (okHttpResponse.code() >= 400) {
				deliverError(request, Utils.getErrorForServerResponse(new KnError(okHttpResponse), request, okHttpResponse.code()));
				return;
			}

			KnResponse response = request.parseResponse(okHttpResponse);
			if (!response.isSuccess()) {
				deliverError(request, response.getError());
				return;
			}
			response.setOkHttpResponse(okHttpResponse);
			request.deliverResponse(response);
		} catch (Exception e) {
			deliverError(request, Utils.getErrorForConnection(new KnError(e)));
		} finally {
			SourceCloseUtil.close(okHttpResponse, request);
		}
	}

	private void executeDownloadRequest() {
		Response okHttpResponse;
		try {
			okHttpResponse = InternalNetworking.gi().performDownloadRequest(request);

			if (okHttpResponse == null) {
				deliverError(request, Utils.getErrorForConnection(new KnError()));
				return;
			}

			if (okHttpResponse.code() >= 400) {
				deliverError(request, Utils.getErrorForServerResponse(new KnError(okHttpResponse), request, okHttpResponse.code()));
				return;
			}

			request.updateDownloadCompletion(okHttpResponse);
		} catch (Exception e) {
			deliverError(request, Utils.getErrorForConnection(new KnError(e)));
		}
	}

	private void executeUploadRequest() {
		Response okHttpResponse = null;
		try {
			okHttpResponse = InternalNetworking.gi().performUploadRequest(request);

			if (okHttpResponse == null) {
				deliverError(request, Utils.getErrorForConnection(new KnError()));
				return;
			}

			if (request.getResponseAs() == ResponseType.OK_HTTP_RESPONSE) {
				request.deliverOkHttpResponse(okHttpResponse);
				return;
			}

			if (okHttpResponse.code() >= 400) {
				deliverError(request, Utils.getErrorForServerResponse(new KnError(okHttpResponse), request, okHttpResponse.code()));
				return;
			}

			KnResponse response = request.parseResponse(okHttpResponse);
			if (!response.isSuccess()) {
				deliverError(request, response.getError());
				return;
			}

			response.setOkHttpResponse(okHttpResponse);
			request.deliverResponse(response);
		} catch (Exception e) {
			deliverError(request, Utils.getErrorForConnection(new KnError(e)));
		} finally {
			SourceCloseUtil.close(okHttpResponse, request);
		}
	}

	public Priority getPriority() {
		return priority;
	}

	private void deliverError(final KnRequest request, final KnError knError) {
		Core.gi().getExecutorSupplier().forMainThreadTasks().execute(() -> {
			request.deliverError(knError);
			request.finish();
		});
	}
}

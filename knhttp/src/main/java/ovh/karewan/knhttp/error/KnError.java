package ovh.karewan.knhttp.error;

import ovh.karewan.knhttp.common.KnConstants;

import okhttp3.Response;

@SuppressWarnings("unused")
public final class KnError extends Exception {

	private String errorBody;

	private int errorCode = 0;

	private String errorDetail;

	private Response response;

	public KnError() {
	}

	public KnError(Response response) {
		this.response = response;
	}

	public KnError(String message) {
		super(message);
	}

	public KnError(String message, Response response) {
		super(message);
		this.response = response;
	}

	public KnError(String message, Throwable throwable) {
		super(message, throwable);
	}

	public KnError(String message, Response response, Throwable throwable) {
		super(message, throwable);
		this.response = response;
	}

	public KnError(Response response, Throwable throwable) {
		super(throwable);
		this.response = response;
	}

	public KnError(Throwable throwable) {
		super(throwable);
	}

	public Response getResponse() {
		return response;
	}

	public void setErrorDetail(String errorDetail) {
		this.errorDetail = errorDetail;
	}

	public String getErrorDetail() {
		return this.errorDetail;
	}

	public void setErrorCode(int errorCode) {
		this.errorCode = errorCode;
	}

	public int getErrorCode() {
		return this.errorCode;
	}

	public void setCancellationMessageInError() {
		this.errorDetail = KnConstants.REQUEST_CANCELLED_ERROR;
	}

	public String getErrorBody() {
		return errorBody;
	}

	public void setErrorBody(String errorBody) {
		this.errorBody = errorBody;
	}
}

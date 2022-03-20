package ovh.karewan.knhttp.common;

import ovh.karewan.knhttp.error.KnError;

import okhttp3.Response;

public final class KnResponse<T> {

	private final T mResult;

	private final KnError mKnError;

	private Response response;

	public static <T> KnResponse<T> success(T result) {
		return new KnResponse<>(result);
	}

	public static <T> KnResponse<T> failed(KnError knError) {
		return new KnResponse<>(knError);
	}

	public KnResponse(T result) {
		this.mResult = result;
		this.mKnError = null;
	}

	public KnResponse(KnError knError) {
		this.mResult = null;
		this.mKnError = knError;
	}

	public T getResult() {
		return mResult;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isSuccess() {
		return mKnError == null;
	}

	public KnError getError() {
		return mKnError;
	}

	public void setOkHttpResponse(Response response) {
		this.response = response;
	}

	public Response getOkHttpResponse() {
		return response;
	}

}

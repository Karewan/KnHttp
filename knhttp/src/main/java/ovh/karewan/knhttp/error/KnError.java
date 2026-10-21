package ovh.karewan.knhttp.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.alibaba.fastjson2.JSON;

import ovh.karewan.knhttp.common.KnConstants;

import okhttp3.Response;

/**
 * The error of a request. {@link #getType()} tells what went wrong, to choose the message shown to the user.
 */
@SuppressWarnings("unused")
public class KnError extends Exception {

	/**
	 * What went wrong, from the user point of view
	 */
	public enum Type {
		/** The server couldn't be reached or the connection was lost: no Internet, DNS failure, connection refused or reset... */
		NETWORK,
		/** No answer in time: connect, read, write or call timeout */
		TIMEOUT,
		/** The secure connection was refused: untrusted or expired certificate, wrong hostname... Often a wrong device date or a captive portal (public Wi-Fi) */
		TLS,
		/** The server answered with an HTTP error, status >= 400: see {@link #getErrorCode()} and {@link #getErrorBody()} */
		HTTP,
		/** The response doesn't match the requested type: invalid JSON, not an image... */
		PARSE,
		/** Downloads: the file couldn't be written (storage full, no permission...) */
		STORAGE,
		/** The request couldn't be sent as built: invalid URL, header or body, cleartext not permitted... A bug of the app */
		INVALID_REQUEST,
		/** The request was cancelled by the app: usually nothing to show */
		CANCELLED,
		/** Unexpected error, or an error created by the app */
		UNKNOWN
	}

	private Type type = Type.UNKNOWN;

	private String errorBody;

	private int errorCode = 0;

	private String errorDetail;

	// Not serializable, and already closed: only its status and headers are readable
	private transient Response response;

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

	/**
	 * Builds an error without capturing the stack trace of the worker thread, which is
	 * meaningless for HTTP / cancellation errors and costly to fill.
	 */
	@RestrictTo(RestrictTo.Scope.LIBRARY)
	@NonNull
	public static KnError withoutStackTrace(@NonNull Type type, @Nullable String message, @Nullable Response response, @Nullable Throwable cause) {
		final KnError error = new LightKnError(message, response, cause);
		error.type = type;
		error.errorDetail = detailOf(type);
		return error;
	}

	@NonNull
	private static String detailOf(@NonNull Type type) {
		switch (type) {
			case HTTP: return KnConstants.RESPONSE_FROM_SERVER_ERROR;
			case PARSE: return KnConstants.PARSE_ERROR;
			case INVALID_REQUEST: return KnConstants.INVALID_REQUEST_ERROR;
			case CANCELLED: return KnConstants.REQUEST_CANCELLED_ERROR;
			default: return KnConstants.CONNECTION_ERROR;
		}
	}

	/**
	 * @return What went wrong, never null ({@link Type#UNKNOWN} for the errors created by the app)
	 */
	@NonNull
	public Type getType() {
		return type;
	}

	/**
	 * Whether the same request may succeed later: network errors, timeouts,
	 * and the HTTP statuses 408, 425, 429, 500, 502, 503 and 504.
	 */
	public boolean isRetryable() {
		switch (type) {
			case NETWORK:
			case TIMEOUT:
				return true;
			case HTTP:
				return errorCode == 408 || errorCode == 425 || errorCode == 429 || errorCode == 500 || (errorCode >= 502 && errorCode <= 504);
			default:
				return false;
		}
	}

	public boolean isCancellation() {
		return type == Type.CANCELLED || KnConstants.REQUEST_CANCELLED_ERROR.equals(errorDetail);
	}

	/**
	 * The response of an {@link Type#HTTP} error. Its body is closed: read it with {@link #getErrorBody()}.
	 */
	public Response getResponse() {
		return response;
	}

	public void setErrorDetail(String errorDetail) {
		this.errorDetail = errorDetail;
	}

	/**
	 * @return One of the KnConstants.*_ERROR values (kept for compatibility, {@link #getType()} is more precise)
	 */
	public String getErrorDetail() {
		return this.errorDetail;
	}

	public void setErrorCode(int errorCode) {
		this.errorCode = errorCode;
	}

	/**
	 * @return The HTTP status of an {@link Type#HTTP} error, 0 otherwise
	 */
	public int getErrorCode() {
		return this.errorCode;
	}

	public void setCancellationMessageInError() {
		this.type = Type.CANCELLED;
		this.errorDetail = KnConstants.REQUEST_CANCELLED_ERROR;
	}

	/**
	 * @return The body of an {@link Type#HTTP} error (first MiB), null otherwise
	 */
	public String getErrorBody() {
		return errorBody;
	}

	public void setErrorBody(String errorBody) {
		this.errorBody = errorBody;
	}

	/**
	 * Parses the JSON error body sent by the server, typically to show its message: {"message": "Invalid email"}
	 * @param bodyClass The class of the body (POJO, JSONObject...)
	 * @return null if there is no error body or if it can't be parsed
	 */
	@Nullable
	public <T> T getErrorBodyAs(@NonNull Class<T> bodyClass) {
		final String body = errorBody;
		if (body == null || body.isEmpty()) return null;

		try {
			return JSON.parseObject(body, bodyClass);
		} catch (Exception e) {
			return null;
		}
	}

	@NonNull
	@Override
	public String toString() {
		return "KnError{type=" + type + (errorCode != 0 ? ", errorCode=" + errorCode : "") + ", message=" + getMessage() + '}';
	}

	private static final class LightKnError extends KnError {
		LightKnError(String message, Response response, Throwable cause) {
			super(message != null ? message : (cause != null ? cause.toString() : null), response, cause);
		}

		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}
	}
}

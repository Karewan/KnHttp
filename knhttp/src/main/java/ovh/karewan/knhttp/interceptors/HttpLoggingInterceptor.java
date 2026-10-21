package ovh.karewan.knhttp.interceptors;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Connection;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import ovh.karewan.knhttp.internal.RequestProgressBody;

import android.util.Log;

import androidx.annotation.NonNull;

@SuppressWarnings("unused")
public final class HttpLoggingInterceptor implements Interceptor {

	private static final Charset UTF8 = StandardCharsets.UTF_8;
	private static final String TAG = "KnHttp";
	private static final int MAX_LOG_LENGTH = 4000;
	// Larger bodies are not logged: they would be held in memory (downloads, uploads...)
	private static final long MAX_LOGGED_BODY_SIZE = 1024 * 1024;

	public enum Level {
		/**
		 * No logs.
		 */
		NONE,
		/**
		 * Logs request and response lines.
		 * <p>
		 * <p>Example:
		 * <pre>{@code
		 * --> POST /greeting http/1.1 (3-byte body)
		 *
		 * <-- 200 OK (22ms, 6-byte body)
		 * }</pre>
		 */
		BASIC,
		/**
		 * Logs request and response lines and their respective headers.
		 * <p>
		 * <p>Example:
		 * <pre>{@code
		 * --> POST /greeting http/1.1
		 * Host: example.com
		 * Content-Type: plain/text
		 * Content-Length: 3
		 * --> END POST
		 *
		 * <-- 200 OK (22ms)
		 * Content-Type: plain/text
		 * Content-Length: 6
		 * <-- END HTTP
		 * }</pre>
		 */
		HEADERS,
		/**
		 * Logs request and response lines and their respective headers and bodies (if present).
		 * <p>
		 * <p>Example:
		 * <pre>{@code
		 * --> POST /greeting http/1.1
		 * Host: example.com
		 * Content-Type: plain/text
		 * Content-Length: 3
		 *
		 * Hi?
		 * --> END POST
		 *
		 * <-- 200 OK (22ms)
		 * Content-Type: plain/text
		 * Content-Length: 6
		 *
		 * Hello!
		 * <-- END HTTP
		 * }</pre>
		 */
		BODY
	}

	public interface Logger {
		void log(String message);

		/**
		 * A {@link Logger} writing to Logcat, long messages are split in several lines.
		 */
		Logger DEFAULT = HttpLoggingInterceptor::logToLogcat;
	}

	static void logToLogcat(String message) {
		for (int i = 0, length = message.length(); i < length; i++) {
			int newline = message.indexOf('\n', i);
			newline = newline != -1 ? newline : length;
			do {
				final int end = Math.min(newline, i + MAX_LOG_LENGTH);
				Log.i(TAG, message.substring(i, end));
				i = end;
			} while (i < newline);
		}
	}

	public HttpLoggingInterceptor() {
		this(Logger.DEFAULT);
	}

	public HttpLoggingInterceptor(Logger logger) {
		this.logger = logger;
	}

	private final Logger logger;

	private volatile Level level = Level.NONE;

	/**
	 * Change the level at which this interceptor logs.
	 */
	@SuppressWarnings("UnusedReturnValue")
	public HttpLoggingInterceptor setLevel(Level level) {
		if (level == null) throw new NullPointerException("level == null. Use Level.NONE instead.");
		this.level = level;
		return this;
	}

	public Level getLevel() {
		return level;
	}

	@NonNull
	@Override
	public Response intercept(Chain chain) throws IOException {
		Level level = this.level;

		Request request = chain.request();
		if (level == Level.NONE) {
			return chain.proceed(request);
		}

		boolean logBody = level == Level.BODY;
		boolean logHeaders = logBody || level == Level.HEADERS;

		RequestBody requestBody = request.body();
		boolean hasRequestBody = requestBody != null;

		Connection connection = chain.connection();
		Protocol protocol = connection != null ? connection.protocol() : Protocol.HTTP_1_1;
		String requestStartMessage = "--> " + request.method() + ' ' + request.url() + ' ' + protocol;
		if (!logHeaders && hasRequestBody) requestStartMessage += " (" + requestBody.contentLength() + "-byte body)";
		logger.log(requestStartMessage);

		if (logHeaders) {
			if (hasRequestBody) {
				// Request body headers are only present when installed as a network interceptor. Force
				// them to be included (when available) so there values are known.
				if (requestBody.contentType() != null) logger.log("Content-Type: " + requestBody.contentType());
				if (requestBody.contentLength() != -1) logger.log("Content-Length: " + requestBody.contentLength());
			}

			Headers headers = request.headers();
			for (int i = 0, count = headers.size(); i < count; i++) {
				String name = headers.name(i);
				// Skip headers from the request body as they are explicitly logged above.
				if (!"Content-Type".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name)) logger.log(name + ": " + headers.value(i));
			}

			if (!logBody || !hasRequestBody) logger.log("--> END " + request.method());
			else if (bodyEncoded(request.headers())) logger.log("--> END " + request.method() + " (encoded body omitted)");
			// Writing a multipart body would also report a fake upload progress
			else if (requestBody instanceof RequestProgressBody || requestBody instanceof MultipartBody) logger.log("--> END " + request.method() + " (multipart body omitted)");
			else if (requestBody.contentLength() < 0 || requestBody.contentLength() > MAX_LOGGED_BODY_SIZE) logger.log("--> END " + request.method() + " (" + requestBody.contentLength() + "-byte body omitted)");
			else {
				Buffer buffer = new Buffer();
				requestBody.writeTo(buffer);

				Charset charset = UTF8;
				MediaType contentType = requestBody.contentType();
				if (contentType != null) charset = contentType.charset(UTF8);

				logger.log("");
				if (isPlaintext(buffer)) {
					//noinspection ConstantConditions
					logger.log(buffer.readString(charset));
					logger.log("--> END " + request.method() + " (" + requestBody.contentLength() + "-byte body)");
				} else {
					logger.log("--> END " + request.method() + " (binary " + requestBody.contentLength() + "-byte body omitted)");
				}
			}
		}

		long startNs = System.nanoTime();
		Response response;
		try {
			response = chain.proceed(request);
		} catch (Exception e) {
			logger.log("<-- HTTP FAILED: " + e);
			throw e;
		}
		long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

		ResponseBody responseBody = response.body();
		//noinspection ConstantConditions
		long contentLength = responseBody.contentLength();
		String bodySize = contentLength != -1 ? contentLength + "-byte" : "unknown-length";
		logger.log("<-- " + response.code() + ' ' + response.message() + ' ' + response.request().url() + " (" + tookMs + "ms" + (!logHeaders ? ", " + bodySize + " body" : "") + ')');

		if (logHeaders) {
			Headers headers = response.headers();
			for (int i = 0, count = headers.size(); i < count; i++) logger.log(headers.name(i) + ": " + headers.value(i));

			if (!logBody || !promisesBody(response)) {
				logger.log("<-- END HTTP");
			} else if (bodyEncoded(response.headers())) {
				logger.log("<-- END HTTP (encoded body omitted)");
			} else if (contentLength > MAX_LOGGED_BODY_SIZE || responseBody.source().request(MAX_LOGGED_BODY_SIZE + 1)) {
				// Bounded buffering: the bytes read here stay available for the response
				logger.log("<-- END HTTP (body larger than " + MAX_LOGGED_BODY_SIZE + " bytes omitted)");
			} else {
				BufferedSource source = responseBody.source();
				Buffer buffer = source.getBuffer();

				Charset charset = UTF8;
				MediaType contentType = responseBody.contentType();
				if (contentType != null) {
					charset = contentType.charset(UTF8);
				}

				if (!isPlaintext(buffer)) {
					logger.log("");
					logger.log("<-- END HTTP (binary " + buffer.size() + "-byte body omitted)");
					return response;
				}

				if (contentLength != 0) {
					logger.log("");
					//noinspection ConstantConditions
					logger.log(buffer.clone().readString(charset));
				}

				logger.log("<-- END HTTP (" + buffer.size() + "-byte body)");
			}
		}

		return response;
	}

	/**
	 * Returns true if the body in question probably contains human readable text. Uses a small sample
	 * of code points to detect unicode control characters commonly used in binary file signatures.
	 */
	static boolean isPlaintext(Buffer buffer) {
		try {
			Buffer prefix = new Buffer();
			long byteCount = buffer.size() < 64 ? buffer.size() : 64;
			buffer.copyTo(prefix, 0, byteCount);
			for (int i = 0; i < 16; i++) {
				if (prefix.exhausted()) break;
				int codePoint = prefix.readUtf8CodePoint();
				if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) return false;
			}
			return true;
		} catch (EOFException e) {
			return false; // Truncated UTF-8 sequence.
		}
	}

	/**
	 * Returns true if the response headers and status indicate that the response has a body
	 * (public API equivalent of OkHttp's internal HttpHeaders.promisesBody()).
	 */
	static boolean promisesBody(Response response) {
		if ("HEAD".equals(response.request().method())) return false;

		final int code = response.code();
		if ((code < 100 || code >= 200) && code != 204 && code != 304) return true;

		// If the Content-Length or Transfer-Encoding headers disagree with the response code, the response is malformed: honor the headers
		final String contentLength = response.header("Content-Length");
		if (contentLength != null) {
			try {
				if (Long.parseLong(contentLength) != -1L) return true;
			} catch (NumberFormatException ignore) {
			}
		}

		return "chunked".equalsIgnoreCase(response.header("Transfer-Encoding"));
	}

	private boolean bodyEncoded(Headers headers) {
		String contentEncoding = headers.get("Content-Encoding");
		return contentEncoding != null && !contentEncoding.equalsIgnoreCase("identity");
	}
}

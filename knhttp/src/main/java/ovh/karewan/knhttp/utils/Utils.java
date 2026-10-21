package ovh.karewan.knhttp.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import ovh.karewan.knhttp.common.KnConstants;
import ovh.karewan.knhttp.error.KnError;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.FileNameMap;
import java.net.SocketTimeoutException;
import java.net.URLConnection;
import java.net.UnknownServiceException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

import okhttp3.Cache;
import okhttp3.Response;

public final class Utils {
	private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

	// Limits the number of bitmaps decoded at the same time (memory peaks) without serializing everything
	private static final Semaphore sDecodePermits = new Semaphore(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)));

	// Unique temporary download files, even for concurrent downloads of the same file
	private static final AtomicLong sTmpFileCounter = new AtomicLong();

	private Utils() {}

	public static File getDiskCacheDir(Context context, String uniqueName) {
		return new File(context.getCacheDir(), uniqueName);
	}

	public static Cache getCache(Context context, int maxCacheSize, String uniqueName) {
		return new Cache(getDiskCacheDir(context, uniqueName), maxCacheSize);
	}

	public static String getMimeType(String path) {
		FileNameMap fileNameMap = URLConnection.getFileNameMap();
		String contentTypeFor = fileNameMap.getContentTypeFor(path);
		if (contentTypeFor == null) contentTypeFor = "application/octet-stream";
		return contentTypeFor;
	}

	/**
	 * Percent-encodes a value so it can be safely inserted as a single path segment.
	 * Only the RFC 3986 unreserved characters are kept as is.
	 */
	@NonNull
	public static String encodePathSegment(@Nullable String value) {
		final String str = String.valueOf(value);

		// Fast path: nothing to encode
		boolean needsEncoding = false;
		for (int i = 0, length = str.length(); i < length; i++) {
			if (!isUnreserved(str.charAt(i))) {
				needsEncoding = true;
				break;
			}
		}
		if (!needsEncoding) return str;

		final byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
		final StringBuilder sb = new StringBuilder(bytes.length * 3);
		for (byte b : bytes) {
			final int c = b & 0xFF;
			if (isUnreserved(c)) {
				sb.append((char) c);
			} else {
				sb.append('%').append(HEX_DIGITS[c >> 4]).append(HEX_DIGITS[c & 0xF]);
			}
		}
		return sb.toString();
	}

	private static boolean isUnreserved(int c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_' || c == '~';
	}

	public static void closeQuietly(@Nullable Response response) {
		if (response == null) return;

		try {
			response.close();
		} catch (Exception ignore) {
		}
	}

	public static void closeQuietly(@Nullable Closeable closeable) {
		if (closeable == null) return;

		try {
			closeable.close();
		} catch (Exception ignore) {
		}
	}

	@Nullable
	public static Bitmap decodeBitmap(byte[] data, int maxWidth, int maxHeight, @Nullable Bitmap.Config decodeConfig, @Nullable BitmapFactory.Options userOptions, @Nullable ImageView.ScaleType scaleType) {
		// Each decode gets its own options, the user ones may be shared between threads
		final BitmapFactory.Options options = copyDecodeOptions(userOptions);
		if (decodeConfig != null) options.inPreferredConfig = decodeConfig;

		sDecodePermits.acquireUninterruptibly();
		try {
			if (maxWidth == 0 && maxHeight == 0) return BitmapFactory.decodeByteArray(data, 0, data.length, options);

			options.inJustDecodeBounds = true;
			BitmapFactory.decodeByteArray(data, 0, data.length, options);
			final int actualWidth = options.outWidth;
			final int actualHeight = options.outHeight;
			if (actualWidth <= 0 || actualHeight <= 0) return null;

			final int desiredWidth = Math.max(1, getResizedDimension(maxWidth, maxHeight, actualWidth, actualHeight, scaleType));
			final int desiredHeight = Math.max(1, getResizedDimension(maxHeight, maxWidth, actualHeight, actualWidth, scaleType));

			options.inJustDecodeBounds = false;
			options.inSampleSize = findBestSampleSize(actualWidth, actualHeight, desiredWidth, desiredHeight);
			final Bitmap tempBitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);

			if (tempBitmap != null && (tempBitmap.getWidth() > desiredWidth || tempBitmap.getHeight() > desiredHeight)) {
				final Bitmap bitmap = Bitmap.createScaledBitmap(tempBitmap, desiredWidth, desiredHeight, true);
				if (bitmap != tempBitmap) tempBitmap.recycle();
				return bitmap;
			}

			return tempBitmap;
		} finally {
			sDecodePermits.release();
		}
	}

	@NonNull
	private static BitmapFactory.Options copyDecodeOptions(@Nullable BitmapFactory.Options src) {
		final BitmapFactory.Options options = new BitmapFactory.Options();
		if (src == null) return options;

		// inBitmap and inTempStorage are per-decode buffers and are never shared between requests
		options.inPreferredConfig = src.inPreferredConfig;
		options.inPremultiplied = src.inPremultiplied;
		options.inMutable = src.inMutable;
		options.inScaled = src.inScaled;
		options.inDensity = src.inDensity;
		options.inTargetDensity = src.inTargetDensity;
		options.inScreenDensity = src.inScreenDensity;
		options.inSampleSize = src.inSampleSize;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) options.inPreferredColorSpace = src.inPreferredColorSpace;
		return options;
	}

	private static int getResizedDimension(int maxPrimary, int maxSecondary, int actualPrimary, int actualSecondary, @Nullable ImageView.ScaleType scaleType) {
		if ((maxPrimary == 0) && (maxSecondary == 0)) return actualPrimary;

		if (scaleType == ImageView.ScaleType.FIT_XY) {
			if (maxPrimary == 0) return actualPrimary;
			return maxPrimary;
		}

		if (maxPrimary == 0) {
			double ratio = (double) maxSecondary / (double) actualSecondary;
			return (int) (actualPrimary * ratio);
		}

		if (maxSecondary == 0) {
			return maxPrimary;
		}

		double ratio = (double) actualSecondary / (double) actualPrimary;
		int resized = maxPrimary;

		if (scaleType == ImageView.ScaleType.CENTER_CROP) {
			if ((resized * ratio) < maxSecondary) resized = (int) (maxSecondary / ratio);
			return resized;
		}

		if ((resized * ratio) > maxSecondary) resized = (int) (maxSecondary / ratio);

		return resized;
	}

	public static int findBestSampleSize(int actualWidth, int actualHeight, int desiredWidth, int desiredHeight) {
		double wr = (double) actualWidth / desiredWidth;
		double hr = (double) actualHeight / desiredHeight;
		double ratio = Math.min(wr, hr);
		int n = 1;
		while ((n * 2) <= ratio && n < (1 << 20)) n *= 2;
		return n;
	}

	/**
	 * Writes the response body into a temporary file then atomically moves it to its destination,
	 * so the destination file is never partially written.
	 * @throws IOException A network error, or a {@link StorageException} when the file can't be written
	 */
	public static void saveFile(@NonNull Response response, @NonNull String dirPath, @NonNull String fileName) throws IOException {
		// The file name may contain sub-directories
		final File file = new File(dirPath, fileName);
		final File dir = file.getParentFile();
		if (dir == null || (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory())) throw new StorageException("Unable to create the directory of " + file.getAbsolutePath(), null);

		final File tmpFile = new File(dir, "." + file.getName() + "." + sTmpFileCounter.incrementAndGet() + "." + System.nanoTime() + ".kntmp");
		boolean success = false;

		try {
			final BufferedSink sink;
			try {
				sink = Okio.buffer(Okio.sink(tmpFile));
			} catch (IOException e) {
				throw new StorageException("Unable to create " + tmpFile.getAbsolutePath(), e);
			}

			try {
				// Same as writeAll(), but the file errors are told apart from the network ones
				final BufferedSource source = response.body().source();
				final Buffer buffer = sink.getBuffer();
				while (source.read(buffer, 8192) != -1) {
					try {
						sink.emitCompleteSegments();
					} catch (IOException e) {
						throw new StorageException("Unable to write " + tmpFile.getAbsolutePath(), e);
					}
				}

				try {
					sink.close();
				} catch (IOException e) {
					throw new StorageException("Unable to write " + tmpFile.getAbsolutePath(), e);
				}
			} finally {
				closeQuietly(sink);
			}

			if (!tmpFile.renameTo(file)) {
				// Some file systems refuse to replace an existing file
				//noinspection ResultOfMethodCallIgnored
				file.delete();
				if (!tmpFile.renameTo(file)) throw new StorageException("Unable to move the downloaded file to " + file.getAbsolutePath(), null);
			}

			success = true;
		} finally {
			if (!success) {
				//noinspection ResultOfMethodCallIgnored
				tmpFile.delete();
			}
		}
	}

	/**
	 * A download file couldn't be written
	 */
	private static final class StorageException extends IOException {
		StorageException(String message, @Nullable Throwable cause) {
			super(message, cause);
		}
	}

	/**
	 * Reads at most {@link KnConstants#MAX_ERROR_BODY_SIZE} bytes of the error body.
	 */
	@Nullable
	public static String readErrorBody(@NonNull Response response) {
		try {
			return response.peekBody(KnConstants.MAX_ERROR_BODY_SIZE).string();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Error for a request which failed before getting a response (network, TLS, timeout, storage...)
	 */
	@NonNull
	public static KnError getErrorForConnection(@NonNull Throwable cause) {
		return KnError.withoutStackTrace(typeOf(cause), null, null, cause);
	}

	@NonNull
	public static KnError getErrorForCancellation() {
		return KnError.withoutStackTrace(KnError.Type.CANCELLED, "Request cancelled", null, null);
	}

	@NonNull
	public static KnError getErrorForInvalidRequest(@NonNull Throwable cause) {
		return KnError.withoutStackTrace(KnError.Type.INVALID_REQUEST, cause.getMessage(), null, cause);
	}

	@NonNull
	public static KnError getErrorForServerResponse(@NonNull Response response) {
		final String message = response.message();
		final KnError error = KnError.withoutStackTrace(KnError.Type.HTTP, "HTTP " + response.code() + (message.isEmpty() ? "" : " " + message), response, null);
		error.setErrorBody(readErrorBody(response));
		error.setErrorCode(response.code());
		return error;
	}

	@NonNull
	public static KnError getErrorForParse(@Nullable Throwable cause) {
		return KnError.withoutStackTrace(KnError.Type.PARSE, cause == null ? "Failed to parse the body" : null, null, cause);
	}

	/**
	 * Classifies a failure. OkHttp reports the failures of the other routes (IPv4 / IPv6...)
	 * as suppressed exceptions: they are inspected too.
	 */
	@NonNull
	static KnError.Type typeOf(@NonNull Throwable failure) {
		if (failure instanceof StorageException) return KnError.Type.STORAGE;
		if (!(failure instanceof IOException)) return KnError.Type.UNKNOWN;
		if (failure instanceof UnknownServiceException) return KnError.Type.INVALID_REQUEST; // Cleartext not permitted
		if (contains(failure, true, 0)) return KnError.Type.TLS;
		if (contains(failure, false, 0)) return KnError.Type.TIMEOUT;
		return KnError.Type.NETWORK;
	}

	private static boolean contains(@Nullable Throwable t, boolean tls, int depth) {
		if (t == null || depth > 8) return false;

		// A plain SSLException is usually the connection lost during a transfer: not a TLS refusal
		if (tls ? (t instanceof SSLHandshakeException || t instanceof SSLPeerUnverifiedException || t instanceof CertificateException)
				: (t instanceof SocketTimeoutException || (t instanceof InterruptedIOException && "timeout".equals(t.getMessage())))) {
			return true;
		}

		for (Throwable suppressed : t.getSuppressed()) {
			if (contains(suppressed, tls, depth + 1)) return true;
		}
		return t.getCause() != t && contains(t.getCause(), tls, depth + 1);
	}
}

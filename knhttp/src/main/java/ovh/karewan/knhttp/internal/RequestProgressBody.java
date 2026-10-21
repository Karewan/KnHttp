package ovh.karewan.knhttp.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;

public final class RequestProgressBody extends RequestBody {
	private final RequestBody mRequestBody;
	private final ProgressReporter mReporter;

	public RequestProgressBody(@NonNull RequestBody requestBody, @NonNull ProgressReporter reporter) {
		this.mRequestBody = requestBody;
		this.mReporter = reporter;
	}

	@Nullable
	@Override
	public MediaType contentType() {
		return mRequestBody.contentType();
	}

	@Override
	public long contentLength() throws IOException {
		return mRequestBody.contentLength();
	}

	@Override
	public boolean isOneShot() {
		return mRequestBody.isOneShot();
	}

	@Override
	public void writeTo(@NonNull BufferedSink sink) throws IOException {
		// A new counting sink for each call: OkHttp calls writeTo() again on retries and redirects
		final CountingSink countingSink = new CountingSink(sink, contentLength());
		final BufferedSink bufferedSink = Okio.buffer(countingSink);
		mRequestBody.writeTo(bufferedSink);
		bufferedSink.flush();
		mReporter.onProgress(countingSink.mBytesWritten, countingSink.mContentLength, true);
	}

	private final class CountingSink extends ForwardingSink {
		private final long mContentLength;
		private long mBytesWritten = 0L;

		CountingSink(BufferedSink delegate, long contentLength) {
			super(delegate);
			mContentLength = contentLength;
		}

		@Override
		public void write(@NonNull Buffer source, long byteCount) throws IOException {
			super.write(source, byteCount);
			mBytesWritten += byteCount;
			mReporter.onProgress(mBytesWritten, mContentLength, false);
		}
	}
}

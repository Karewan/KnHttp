package ovh.karewan.knhttp.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

public final class ResponseProgressBody extends ResponseBody {

	private final ResponseBody mResponseBody;
	private final ProgressReporter mReporter;
	private BufferedSource mBufferedSource;

	public ResponseProgressBody(@NonNull ResponseBody responseBody, @NonNull ProgressReporter reporter) {
		this.mResponseBody = responseBody;
		this.mReporter = reporter;
	}

	@Nullable
	@Override
	public MediaType contentType() {
		return mResponseBody.contentType();
	}

	@Override
	public long contentLength() {
		return mResponseBody.contentLength();
	}

	@Override
	@NonNull
	public BufferedSource source() {
		if (mBufferedSource == null) mBufferedSource = Okio.buffer(source(mResponseBody.source()));
		return mBufferedSource;
	}

	private Source source(Source source) {
		final long contentLength = mResponseBody.contentLength();

		return new ForwardingSource(source) {
			private long mTotalBytesRead = 0L;
			private boolean mDone = false;

			@Override
			public long read(@NonNull Buffer sink, long byteCount) throws IOException {
				final long bytesRead = super.read(sink, byteCount);

				if (bytesRead != -1) {
					mTotalBytesRead += bytesRead;
					mReporter.onProgress(mTotalBytesRead, contentLength, false);
				} else if (!mDone) {
					mDone = true;
					mReporter.onProgress(mTotalBytesRead, contentLength, true);
				}

				return bytesRead;
			}
		};
	}
}

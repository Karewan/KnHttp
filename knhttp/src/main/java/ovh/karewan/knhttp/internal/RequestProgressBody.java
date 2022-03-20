package ovh.karewan.knhttp.internal;

import androidx.annotation.NonNull;

import ovh.karewan.knhttp.common.KnConstants;
import ovh.karewan.knhttp.interfaces.UploadProgressListener;
import ovh.karewan.knhttp.model.Progress;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

public final class RequestProgressBody extends RequestBody {
	private final RequestBody requestBody;
	private BufferedSink bufferedSink;
	private UploadProgressHandler uploadProgressHandler;

	public RequestProgressBody(RequestBody requestBody, UploadProgressListener uploadProgressListener) {
		this.requestBody = requestBody;
		if (uploadProgressListener != null) this.uploadProgressHandler = new UploadProgressHandler(uploadProgressListener);
	}

	public MediaType contentType() {
		return requestBody.contentType();
	}

	@Override
	public long contentLength() throws IOException {
		return requestBody.contentLength();
	}

	@Override
	public void writeTo(@NonNull BufferedSink sink) throws IOException {
		if (bufferedSink == null) bufferedSink = Okio.buffer(sink(sink));
		requestBody.writeTo(bufferedSink);
		bufferedSink.flush();
	}

	private Sink sink(Sink sink) {
		return new ForwardingSink(sink) {
			long bytesWritten = 0L;
			long contentLength = 0L;

			@Override
			public void write(@NonNull Buffer source, long byteCount) throws IOException {
				super.write(source, byteCount);
				if (contentLength == 0) contentLength = contentLength();
				bytesWritten += byteCount;
				if (uploadProgressHandler != null) uploadProgressHandler.obtainMessage(KnConstants.UPDATE, new Progress(bytesWritten, contentLength)).sendToTarget();
			}
		};
	}
}

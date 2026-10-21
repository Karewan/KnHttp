package ovh.karewan.knhttp.internal;

import androidx.annotation.NonNull;

import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnResponse;
import ovh.karewan.knhttp.common.Priority;
import ovh.karewan.knhttp.utils.Utils;

public final class InternalRunnable implements Runnable {

	private final Priority priority;
	public final int sequence;
	public final KnRequest request;
	private final KnEngine engine;

	public InternalRunnable(@NonNull KnRequest request) {
		this.request = request;
		this.sequence = request.getSequenceNumber();
		this.priority = request.getPriority();
		this.engine = request.getEngine();
	}

	@Override
	public void run() {
		// Cancelled while queued: the cancellation has already been delivered
		if (request.isCanceled()) return;

		request.setRunning(true);
		try {
			KnResponse<?> response;
			try {
				response = RequestExecutor.execute(request, engine);
			} catch (Throwable t) {
				// Never leave a listener without a callback
				response = KnResponse.failed(Utils.getErrorForConnection(t));
			}
			request.deliver(response);
		} finally {
			request.setRunning(false);
		}
	}

	public Priority getPriority() {
		return priority;
	}
}

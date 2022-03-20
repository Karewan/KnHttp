package ovh.karewan.knhttp.internal;

import androidx.annotation.NonNull;

import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.Priority;
import ovh.karewan.knhttp.core.Core;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("rawtypes")
public final class KnRequestQueue {
	private static volatile KnRequestQueue sInstance = null;
	private final Set<KnRequest> mCurrentRequests = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final AtomicInteger mSequenceGenerator = new AtomicInteger();

	private KnRequestQueue() {}

	public static KnRequestQueue gi() {
		if (sInstance == null) {
			synchronized (KnRequestQueue.class) {
				if (sInstance == null)  sInstance = new KnRequestQueue();
			}
		}

		return sInstance;
	}

	public static void shutDown() {
		if(sInstance == null) return;

		sInstance.cancelAll(true);

		synchronized (KnRequestQueue.class) {
			sInstance = null;
		}
	}

	@SuppressWarnings("rawtypes")
	public interface RequestFilter {
		boolean apply(KnRequest request);
	}

	private void cancel(RequestFilter filter, boolean forceCancel) {
		try {
			for (Iterator<KnRequest> iterator = mCurrentRequests.iterator(); iterator.hasNext(); ) {
				KnRequest request = iterator.next();
				if (filter.apply(request)) {
					request.cancel(forceCancel);
					if (request.isCanceled()) {
						request.destroy();
						iterator.remove();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void cancelAll(boolean forceCancel) {
		try {
			for (Iterator<KnRequest> iterator = mCurrentRequests.iterator(); iterator.hasNext(); ) {
				KnRequest request = iterator.next();
				request.cancel(forceCancel);
				if (request.isCanceled()) {
					request.destroy();
					iterator.remove();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void cancelRequestWithGivenTag(@NonNull final Object tag, final boolean forceCancel) {
		try {
			cancel(request -> isRequestWithTheGivenTag(request, tag), forceCancel);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int getSequenceNumber() {
		return mSequenceGenerator.incrementAndGet();
	}

	@SuppressWarnings("UnusedReturnValue")
	public KnRequest addRequest(KnRequest request) {
		try {
			mCurrentRequests.add(request);
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			request.setSequenceNumber(getSequenceNumber());
			if (request.getPriority() == Priority.IMMEDIATE) {
				request.setFuture(Core.gi()
						.getExecutorSupplier()
						.forImmediateNetworkTasks()
						.submit(new InternalRunnable(request)));
			} else {
				request.setFuture(Core.gi()
						.getExecutorSupplier()
						.forNetworkTasks()
						.submit(new InternalRunnable(request)));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return request;
	}

	public void finish(KnRequest request) {
		try {
			mCurrentRequests.remove(request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean isRequestRunning(Object tag) {
		try {
			for (KnRequest request : mCurrentRequests) {
				if (isRequestWithTheGivenTag(request, tag) && request.isRunning()) return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}

	private boolean isRequestWithTheGivenTag(KnRequest request, Object tag) {
		if (request.getTag() == null) return false;

		if (request.getTag() instanceof String && tag instanceof String) {
			final String tempRequestTag = (String) request.getTag();
			final String tempTag = (String) tag;
			return tempRequestTag.equals(tempTag);
		}

		return request.getTag().equals(tag);
	}

}

package ovh.karewan.knhttp.internal;

import androidx.annotation.NonNull;

import ovh.karewan.knhttp.common.ANRequest;
import ovh.karewan.knhttp.common.Priority;
import ovh.karewan.knhttp.core.Core;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ANRequestQueue {
	private final Set<ANRequest> mCurrentRequests = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final AtomicInteger mSequenceGenerator = new AtomicInteger();
	private static volatile ANRequestQueue sInstance = null;

	public static void init() {
		gi();
	}

	public static ANRequestQueue gi() {
		if (sInstance == null) {
			synchronized (ANRequestQueue.class) {
				if (sInstance == null)  sInstance = new ANRequestQueue();
			}
		}

		return sInstance;
	}

	public static void shutDown() {
		if(sInstance != null) {
			synchronized (ANRequestQueue.class) {
				sInstance = null;
			}
		}
	}

	public interface RequestFilter {
		boolean apply(ANRequest request);
	}

	private void cancel(RequestFilter filter, boolean forceCancel) {
		try {
			for (Iterator<ANRequest> iterator = mCurrentRequests.iterator(); iterator.hasNext(); ) {
				ANRequest request = iterator.next();
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
			for (Iterator<ANRequest> iterator = mCurrentRequests.iterator(); iterator.hasNext(); ) {
				ANRequest request = iterator.next();
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
	public ANRequest addRequest(ANRequest request) {
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

	public void finish(ANRequest request) {
		try {
			mCurrentRequests.remove(request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean isRequestRunning(Object tag) {
		try {
			for (ANRequest request : mCurrentRequests) {
				if (isRequestWithTheGivenTag(request, tag) && request.isRunning()) return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}

	private boolean isRequestWithTheGivenTag(ANRequest request, Object tag) {
		if (request.getTag() == null) return false;

		if (request.getTag() instanceof String && tag instanceof String) {
			final String tempRequestTag = (String) request.getTag();
			final String tempTag = (String) tag;
			return tempRequestTag.equals(tempTag);
		}

		return request.getTag().equals(tag);
	}

}

package ovh.karewan.knhttp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.Priority;
import ovh.karewan.knhttp.internal.KnEngine;
import ovh.karewan.knhttp.internal.RequestDispatcher;

public class DispatcherTest extends BaseTest {
	private final CountDownLatch mRelease = new CountDownLatch(1);

	@Before
	public void routeByPath() {
		// "/block" waits until the test releases it, the other paths answer at once
		server.setDispatcher(new Dispatcher() {
			@NonNull
			@Override
			public MockResponse dispatch(@NonNull RecordedRequest request) {
				if ("/block".equals(request.getTarget())) {
					try {
						mRelease.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
					} catch (InterruptedException ignore) {
					}
				}
				return new MockResponse.Builder().body(request.getTarget()).build();
			}
		});
	}

	private static RequestDispatcher dispatcher() {
		return KnEngine.get().getDispatcher();
	}

	private static void waitFor(String what, java.util.function.BooleanSupplier condition) {
		final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() > deadline) fail("Timeout while waiting for " + what);
			idleMainLooper(5);
		}
	}

	@Test
	public void theDefaultPriority_isMedium() {
		assertEquals(Priority.MEDIUM, KnHttp.get(url("/")).build().getPriority());
		assertEquals(RequestDispatcher.DEFAULT_MAX_CONCURRENT_REQUESTS, dispatcher().getMaxConcurrentRequests());
	}

	@Test
	public void aRequestAlone_isNeverQueued() {
		for (Priority priority : Priority.values()) {
			final Recorder<String> r = new Recorder<>();
			KnHttp.get(url("/" + priority)).setPriority(priority).build().getAsString(stringListener(r));
			assertEquals(0, dispatcher().getQueuedRequestsCount());
			r.await().assertSuccess();
		}
	}

	@Test
	public void aboveTheLimit_requestsAreStartedByPriorityThenInOrder() throws Exception {
		KnHttp.setMaxConcurrentRequests(1);

		final Recorder<String> blocking = new Recorder<>();
		KnHttp.get(url("/block")).setPriority(Priority.LOW).build().getAsString(stringListener(blocking));
		assertEquals("/block", server.takeRequest().getTarget());

		final List<String> paths = Arrays.asList("/low", "/medium-1", "/high", "/medium-2");
		final List<Priority> priorities = Arrays.asList(Priority.LOW, Priority.MEDIUM, Priority.HIGH, Priority.MEDIUM);
		@SuppressWarnings("unchecked") final Recorder<String>[] queued = new Recorder[paths.size()];
		for (int i = 0; i < paths.size(); i++) {
			queued[i] = new Recorder<>();
			KnHttp.get(url(paths.get(i))).setPriority(priorities.get(i)).build().getAsString(stringListener(queued[i]));
		}
		assertEquals(4, dispatcher().getQueuedRequestsCount());

		mRelease.countDown();
		for (Recorder<String> r : queued) r.await().assertSuccess();

		assertEquals("/high", server.takeRequest().getTarget());
		assertEquals("/medium-1", server.takeRequest().getTarget());
		assertEquals("/medium-2", server.takeRequest().getTarget());
		assertEquals("/low", server.takeRequest().getTarget());
		blocking.await().assertSuccess();
		waitFor("the slot release", () -> dispatcher().getRunningRequestsCount() == 0);
	}

	@Test
	public void immediateRequests_ignoreTheLimit() {
		KnHttp.setMaxConcurrentRequests(1);

		final Recorder<String> blocking = new Recorder<>();
		KnHttp.get(url("/block")).build().getAsString(stringListener(blocking));
		waitFor("the blocking request", () -> server.getRequestCount() == 1);

		final Recorder<String> immediate = new Recorder<>();
		KnHttp.get(url("/now")).setPriority(Priority.IMMEDIATE).build().getAsString(stringListener(immediate));
		immediate.await().assertSuccess();

		// Still blocked: the IMMEDIATE request didn't wait for it
		assertEquals(0, blocking.calls.get());
		mRelease.countDown();
		blocking.await().assertSuccess();
	}

	@Test
	public void raisingTheLimit_startsTheQueuedRequests() {
		KnHttp.setMaxConcurrentRequests(1);

		final Recorder<String> blocking = new Recorder<>();
		KnHttp.get(url("/block")).build().getAsString(stringListener(blocking));
		final Recorder<String> queued = new Recorder<>();
		KnHttp.get(url("/queued")).build().getAsString(stringListener(queued));
		assertEquals(1, dispatcher().getQueuedRequestsCount());

		KnHttp.setMaxConcurrentRequests(2);
		queued.await().assertSuccess();

		mRelease.countDown();
		blocking.await().assertSuccess();
	}

	@Test
	public void cancelledQueuedRequests_areSkipped_andReleaseTheirSlot() {
		KnHttp.setMaxConcurrentRequests(1);

		final Recorder<String> blocking = new Recorder<>();
		KnHttp.get(url("/block")).build().getAsString(stringListener(blocking));

		@SuppressWarnings("unchecked") final Recorder<String>[] cancelled = new Recorder[3];
		for (int i = 0; i < cancelled.length; i++) {
			cancelled[i] = new Recorder<>();
			final KnRequest request = KnHttp.get(url("/cancelled/" + i)).build();
			request.getAsString(stringListener(cancelled[i]));
			request.cancel(true);
			assertTrue(cancelled[i].error.isCancellation());
		}

		mRelease.countDown();
		blocking.await().assertSuccess();

		final Recorder<String> after = new Recorder<>();
		KnHttp.get(url("/after")).build().getAsString(stringListener(after));
		after.await().assertSuccess();

		// Only /block and /after reached the server
		assertEquals(2, server.getRequestCount());
		waitFor("the slot release", () -> dispatcher().getRunningRequestsCount() == 0 && dispatcher().getQueuedRequestsCount() == 0);
		for (Recorder<String> r : cancelled) assertEquals(1, r.calls.get());
	}

	@Test(expected = IllegalArgumentException.class)
	public void theLimit_mustBePositive() {
		KnHttp.setMaxConcurrentRequests(0);
	}
}

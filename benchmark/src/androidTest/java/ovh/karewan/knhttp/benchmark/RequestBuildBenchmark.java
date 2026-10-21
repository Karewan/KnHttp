package ovh.karewan.knhttp.benchmark;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import ovh.karewan.knhttp.KnHttp;
import ovh.karewan.knhttp.common.KnRequest;

/**
 * Cost of building a request on the calling thread (often the main thread)
 */
@RunWith(AndroidJUnit4.class)
public class RequestBuildBenchmark {

	@Rule
	public BenchmarkRule benchmarkRule = new BenchmarkRule();

	@Test
	public void buildGetRequest() {
		final BenchmarkState state = benchmarkRule.getState();

		while (state.keepRunning()) {
			KnHttp.get("https://api.example.com/users/{id}/posts").build();
		}
	}

	@Test
	public void buildGetRequest_withParametersAndHeaders_andResolveTheUrl() {
		final BenchmarkState state = benchmarkRule.getState();

		while (state.keepRunning()) {
			final KnRequest request = KnHttp.get("https://api.example.com/users/{id}/posts")
					.addPathParameter("id", "42 é")
					.addQueryParameter("page", "1")
					.addQueryParameter("q", "hello world")
					.addHeaders("Authorization", "Bearer 0123456789")
					.addHeaders("Accept-Language", "fr-FR")
					.build();
			request.getUrl();
		}
	}
}

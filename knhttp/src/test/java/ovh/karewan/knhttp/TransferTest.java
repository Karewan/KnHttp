package ovh.karewan.knhttp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okio.Buffer;
import ovh.karewan.knhttp.common.KnConstants;
import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnResponse;
import ovh.karewan.knhttp.common.KnSettings;

public class TransferTest extends BaseTest {

	private static byte[] randomBytes(int size) {
		final byte[] bytes = new byte[size];
		new Random(42).nextBytes(bytes);
		return bytes;
	}

	@Test
	public void download_writesTheFileAtomically_andReportsTheFinalProgress() throws Exception {
		final byte[] content = randomBytes(512 * 1024);
		server.enqueue(new MockResponse.Builder().body(new Buffer().write(content)).build());
		final File dir = tmp.newFolder("downloads");

		final Recorder<String> r = new Recorder<>();
		KnHttp.download(url("/file"), dir.getAbsolutePath(), "file.bin")
				.build()
				.setDownloadProgressListener(r::onProgress)
				.startDownload(downloadListener(r));
		r.await().assertSuccess();

		assertArrayEquals(content, Files.readAllBytes(new File(dir, "file.bin").toPath()));
		assertArrayEquals(new String[] { "file.bin" }, dir.list());

		// Throttled: far less events than the 8 KiB segments, the last one is complete
		assertFalse(r.progress.isEmpty());
		assertTrue(r.progress.size() < 10);
		final long[] last = r.progress.get(r.progress.size() - 1);
		assertEquals(content.length, last[0]);
		assertEquals(content.length, last[1]);
	}

	@Test
	public void download_serverError_doesNotCreateTheFile() throws Exception {
		server.enqueue(new MockResponse.Builder().code(404).body("missing").build());
		final File dir = tmp.newFolder("downloads");

		final Recorder<String> r = new Recorder<>();
		KnHttp.download(url("/missing"), dir.getAbsolutePath(), "file.bin").build().startDownload(downloadListener(r));
		r.await().assertError();

		assertEquals(404, r.error.getErrorCode());
		assertEquals("missing", r.error.getErrorBody());
		assertEquals(0, dir.list().length);
	}

	@Test
	public void download_fileNameWithSubDirectories_createsThem() throws Exception {
		server.enqueue(new MockResponse.Builder().body("nested").build());
		final File dir = tmp.newFolder("downloads");

		final KnResponse<String> res = KnHttp.download(url("/file"), dir.getAbsolutePath(), "a/b/file.txt").build().executeForDownload();

		assertTrue(String.valueOf(res.getError()), res.isSuccess());
		assertEquals("nested", new String(Files.readAllBytes(new File(dir, "a/b/file.txt").toPath()), StandardCharsets.UTF_8));
		assertArrayEquals(new String[] { "file.txt" }, new File(dir, "a/b").list());
	}

	@Test
	public void download_replacesAnExistingFile() throws Exception {
		server.enqueue(new MockResponse.Builder().body("new content").build());
		final File dir = tmp.newFolder("downloads");
		Files.write(new File(dir, "file.txt").toPath(), "old content which is longer".getBytes(StandardCharsets.UTF_8));

		final KnResponse<String> res = KnHttp.download(url("/file"), dir.getAbsolutePath(), "file.txt").build().executeForDownload();

		assertTrue(res.isSuccess());
		assertEquals(KnConstants.SUCCESS, res.getResult());
		assertEquals("new content", new String(Files.readAllBytes(new File(dir, "file.txt").toPath()), StandardCharsets.UTF_8));
	}

	@Test
	public void download_cancelled_leavesNoPartialFile() throws Exception {
		server.enqueue(new MockResponse.Builder().body(new Buffer().write(randomBytes(1024 * 1024))).throttleBody(32 * 1024, 50, TimeUnit.MILLISECONDS).build());
		final File dir = tmp.newFolder("downloads");

		final Recorder<String> r = new Recorder<>();
		final KnRequest request = KnHttp.download(url("/big"), dir.getAbsolutePath(), "big.bin").setTag("download").build();
		request.startDownload(downloadListener(r));

		// Wait for the first bytes
		final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
		while (dir.list().length == 0) {
			if (System.currentTimeMillis() > deadline) fail("The download did not start");
			idleMainLooper(5);
		}

		KnHttp.forceCancel("download");
		r.await().assertError();
		assertTrue(r.error.isCancellation());

		// The worker removes the temporary file once the IO is aborted
		while (dir.list().length != 0) {
			if (System.currentTimeMillis() > deadline) fail("Partial files left: " + Arrays.toString(dir.list()));
			idleMainLooper(5);
		}
	}

	@Test
	public void upload_sendsThePartsAndReportsTheFinalProgress() throws Exception {
		server.enqueue(new MockResponse.Builder().body("{\"ok\":true}").build());
		final File file = tmp.newFile("avatar.txt");
		Files.write(file.toPath(), "file content".getBytes(StandardCharsets.UTF_8));

		final Recorder<String> r = new Recorder<>();
		KnHttp.upload(url("/upload"))
				.addMultipartParameter("name", "Karewan")
				.addMultipartFile("avatar", file, "text/plain")
				.build()
				.setUploadProgressListener(r::onProgress)
				.getAsString(stringListener(r));
		r.await().assertSuccess();

		final String body = server.takeRequest().getBody().utf8();
		assertTrue(body.contains("Content-Disposition: form-data; name=\"name\""));
		assertTrue(body.contains("Karewan"));
		assertTrue(body.contains("Content-Disposition: form-data; name=\"avatar\"; filename=\"avatar.txt\""));
		assertTrue(body.contains("file content"));

		final long[] last = r.progress.get(r.progress.size() - 1);
		assertEquals(last[1], last[0]);
		assertTrue(last[0] > 0);
	}

	@Test
	public void upload_bodyIsResentCorrectlyAfterARedirect() throws Exception {
		KnHttp.shutDown();
		initKnHttp(new KnSettings.Builder().setAllowCleartextTraffic(true).setFollowRedirect(true));

		server.enqueue(new MockResponse.Builder().code(307).addHeader("Location", "/second").build());
		server.enqueue(new MockResponse.Builder().body("ok").build());
		final File file = tmp.newFile("data.bin");
		final byte[] content = randomBytes(64 * 1024);
		Files.write(file.toPath(), content);

		final Recorder<String> r = new Recorder<>();
		KnHttp.upload(url("/first"))
				.addMultipartFile("data", file)
				.build()
				.setUploadProgressListener(r::onProgress)
				.getAsString(stringListener(r));
		r.await().assertSuccess();

		final RecordedRequest first = server.takeRequest();
		final RecordedRequest second = server.takeRequest();
		assertEquals("/second", second.getTarget());
		// v4 reused the sink of the first attempt: the second body was lost
		assertEquals(first.getBodySize(), second.getBodySize());
		assertTrue(second.getBodySize() > content.length);
	}
}

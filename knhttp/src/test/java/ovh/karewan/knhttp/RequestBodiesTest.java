package ovh.karewan.knhttp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okio.ByteString;
import ovh.karewan.knhttp.common.KnResponse;
import ovh.karewan.knhttp.common.Method;

/**
 * HTTP methods, raw bodies and multipart bodies as received by the server
 */
public class RequestBodiesTest extends BaseTest {

	private RecordedRequest takeRequest(String method, String body) throws Exception {
		final RecordedRequest recorded = server.takeRequest();
		assertEquals(method, recorded.getMethod());
		final ByteString recordedBody = recorded.getBody();
		assertEquals(body, recordedBody == null ? "" : recordedBody.utf8());
		return recorded;
	}

	@Test
	public void eachMethod_isSentWithItsBody() throws Exception {
		server.enqueue(new MockResponse.Builder().build()); // HEAD: no body
		for (int i = 0; i < 6; i++) server.enqueue(new MockResponse.Builder().body("ok").build());

		final KnResponse<String> head = KnHttp.head(url("/r")).build().executeForString();
		assertTrue(head.isSuccess());
		assertEquals("", head.getResult());
		assertTrue(KnHttp.options(url("/r")).build().executeForString().isSuccess());
		assertTrue(KnHttp.put(url("/r")).addStringBody("put").build().executeForString().isSuccess());
		assertTrue(KnHttp.patch(url("/r")).addStringBody("patch").build().executeForString().isSuccess());
		assertTrue(KnHttp.delete(url("/r")).build().executeForString().isSuccess());
		assertTrue(KnHttp.delete(url("/r")).addStringBody("delete").build().executeForString().isSuccess());
		assertTrue(KnHttp.request(url("/r"), Method.PUT).addStringBody("dynamic").build().executeForString().isSuccess());

		takeRequest("HEAD", "");
		takeRequest("OPTIONS", "");
		takeRequest("PUT", "put");
		takeRequest("PATCH", "patch");
		takeRequest("DELETE", "");
		takeRequest("DELETE", "delete");
		takeRequest("PUT", "dynamic");
	}

	@Test
	public void rawBodies_haveTheirDefaultOrCustomContentType() throws Exception {
		for (int i = 0; i < 4; i++) server.enqueue(new MockResponse.Builder().build());
		final File file = tmp.newFile("data.bin");
		Files.write(file.toPath(), new byte[] { 1, 2, 3 });

		KnHttp.post(url("/string")).addStringBody("text").build().executeForString();
		KnHttp.post(url("/bytes")).addByteBody(new byte[] { 4, 5 }).build().executeForString();
		KnHttp.post(url("/file")).addFileBody(file).build().executeForString();
		KnHttp.post(url("/custom")).addStringBody("<a/>").setContentType("application/xml").build().executeForString();

		assertEquals("text/plain; charset=utf-8", takeRequest("POST", "text").getHeaders().get("Content-Type"));

		final RecordedRequest bytes = server.takeRequest();
		assertEquals("application/octet-stream", bytes.getHeaders().get("Content-Type"));
		assertEquals(ByteString.of((byte) 4, (byte) 5), bytes.getBody());

		final RecordedRequest fileRequest = server.takeRequest();
		assertEquals("application/octet-stream", fileRequest.getHeaders().get("Content-Type"));
		assertEquals(ByteString.of((byte) 1, (byte) 2, (byte) 3), fileRequest.getBody());

		assertTrue(takeRequest("POST", "<a/>").getHeaders().get("Content-Type").startsWith("application/xml"));
	}

	@Test
	public void multipart_sendsTheFieldsAndTheFilesWithTheirTypes() throws Exception {
		server.enqueue(new MockResponse.Builder().build());
		final File text = tmp.newFile("a.txt");
		Files.write(text.toPath(), "AAA".getBytes(StandardCharsets.UTF_8));
		final File image = tmp.newFile("b.png");
		Files.write(image.toPath(), new byte[] { 1, 2, 3 });

		final KnResponse<String> res = KnHttp.upload(url("/upload"))
				.addMultipartParameter("meta", "{\"k\":1}", "application/json")
				.addMultipartParameter("name", "été")
				.addMultipartFileList("files", Arrays.asList(text, image))
				.build()
				.executeForString();
		assertTrue(res.isSuccess());

		final RecordedRequest recorded = server.takeRequest();
		assertEquals("POST", recorded.getMethod());
		assertTrue(recorded.getHeaders().get("Content-Type").startsWith("multipart/form-data; boundary="));

		final String body = recorded.getBody().utf8();
		assertTrue(body, body.contains("Content-Disposition: form-data; name=\"meta\"\r\nContent-Type: application/json"));
		assertTrue(body, body.contains("Content-Disposition: form-data; name=\"name\""));
		assertTrue(body, body.contains("été"));
		assertTrue(body, body.contains("Content-Disposition: form-data; name=\"files\"; filename=\"a.txt\"\r\nContent-Type: text/plain"));
		assertTrue(body, body.contains("AAA"));
		assertTrue(body, body.contains("Content-Disposition: form-data; name=\"files\"; filename=\"b.png\"\r\nContent-Type: image/png"));
	}
}

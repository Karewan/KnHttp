package ovh.karewan.knhttp.model;

import java.io.File;

public final class MultipartFileBody {

	public final File file;
	public final String contentType;

	public MultipartFileBody(File file, String contentType) {
		this.file = file;
		this.contentType = contentType;
	}
}

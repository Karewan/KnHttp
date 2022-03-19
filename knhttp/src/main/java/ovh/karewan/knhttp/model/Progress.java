package ovh.karewan.knhttp.model;

import java.io.Serializable;

public final class Progress implements Serializable {

	public final long currentBytes;
	public final long totalBytes;

	public Progress(long currentBytes, long totalBytes) {
		this.currentBytes = currentBytes;
		this.totalBytes = totalBytes;
	}

}

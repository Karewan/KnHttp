package ovh.karewan.knhttp.core;

public final class Core {
	private static volatile Core sInstance = null;
	private final ExecutorSupplier mExecutorSupplier;

	private Core() {
		this.mExecutorSupplier = new DefaultExecutorSupplier();
	}

	public static Core gi() {
		if (sInstance == null) {
			synchronized (Core.class) {
				if (sInstance == null) sInstance = new Core();
			}
		}

		return sInstance;
	}

	public static void shutDown() {
		if (sInstance == null) return;

		synchronized (Core.class) {
			sInstance = null;
		}
	}

	public ExecutorSupplier getExecutorSupplier() {
		return mExecutorSupplier;
	}
}

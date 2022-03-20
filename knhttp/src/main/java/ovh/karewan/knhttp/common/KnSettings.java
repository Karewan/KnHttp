package ovh.karewan.knhttp.common;

import androidx.annotation.NonNull;

public final class KnSettings {
	private final long mConnectTimeout;
	private final long mReadTimeout;
	private final long mWriteTimeout;
	private final long mCallTimeout;
	private final boolean mAllowObsoleteTls;
	private final boolean mEnableCache;
	private final boolean mEnableBrotli;
	private final boolean mFollowRedirect;

	private KnSettings(long connectTimeout, long readTimeout, long writeTimeout, long callTimeout, boolean allowObsoleteTls, boolean enableCache, boolean enableBrotli, boolean followRedirect) {
		this.mConnectTimeout = connectTimeout;
		this.mReadTimeout = readTimeout;
		this.mWriteTimeout = writeTimeout;
		this.mCallTimeout = callTimeout;
		this.mAllowObsoleteTls = allowObsoleteTls;
		this.mEnableCache = enableCache;
		this.mEnableBrotli = enableBrotli;
		this.mFollowRedirect = followRedirect;
	}

	public long getConnectTimeout() {
		return mConnectTimeout;
	}

	public long getReadTimeout() {
		return mReadTimeout;
	}

	public long getWriteTimeout() {
		return mWriteTimeout;
	}

	public long getCallTimeout() {
		return mCallTimeout;
	}

	public boolean isAllowObsoleteTls() {
		return mAllowObsoleteTls;
	}

	public boolean isCacheEnabled() {
		return mEnableCache;
	}

	public boolean isBrotliEnabled() {
		return mEnableBrotli;
	}

	public boolean isFollowRedirect() {
		return mFollowRedirect;
	}

	@SuppressWarnings("unused")
	public static final class Builder {
		private long mConnectTimeout = 15000; // 15 seconds
		private long mReadTimeout = 30000; // 30 seconds
		private long mWriteTimeout = 30000; // 30 seconds
		private long mCallTimeout = 0; // Inf
		private boolean mAllowObsoleteTls = false; // TLS 1.3 and 1.2 Only
		private boolean mEnableCache = false; // Cache disabled
		private boolean mEnableBrotli = true; // Brotli enabled
		private boolean mFollowRedirect = false; // Follow redirect disabled

		public Builder setConnectTimeout(long timeout) {
			this.mConnectTimeout = timeout;
			return this;
		}

		public Builder setReadTimeout(long timeout) {
			this.mReadTimeout = timeout;
			return this;
		}

		public Builder setWriteTimeout(long timeout) {
			this.mWriteTimeout = timeout;
			return this;
		}

		public Builder setCallTimeout(long timeout) {
			this.mCallTimeout = timeout;
			return this;
		}

		public Builder setAllowObsoleteTls(boolean allow) {
			this.mAllowObsoleteTls = allow;
			return this;
		}

		public Builder setEnableCache(boolean enable) {
			this.mEnableCache = enable;
			return this;
		}

		public Builder setEnableBrotli(boolean enable) {
			this.mEnableBrotli = enable;
			return this;
		}

		public Builder setFollowRedirect(boolean enable) {
			this.mFollowRedirect = enable;
			return this;
		}
		@NonNull
		public KnSettings build() {
			return new KnSettings(mConnectTimeout, mReadTimeout, mWriteTimeout, mCallTimeout, mAllowObsoleteTls, mEnableCache, mEnableBrotli, mFollowRedirect);
		}
	}
}

package ovh.karewan.knhttp.common;

import androidx.annotation.NonNull;

public final class KnSettings {
	private final long mConnectTimeout;
	private final long mReadTimeout;
	private final long mWriteTimeout;
	private final long mCallTimeout;
	private final boolean mAllowObsoleteTls;
	private final boolean mAllowInvalidTlsCertificates;
	private final boolean mAllowCleartextTraffic;
	private final boolean mEnablePostQuantumKeyExchange;
	private final boolean mEnableCache;
	private final boolean mEnableBrotli;
	private final boolean mFollowRedirect;
	private final int mMaxIdleConnections;

	private KnSettings(Builder builder) {
		this.mConnectTimeout = builder.mConnectTimeout;
		this.mReadTimeout = builder.mReadTimeout;
		this.mWriteTimeout = builder.mWriteTimeout;
		this.mCallTimeout = builder.mCallTimeout;
		this.mAllowObsoleteTls = builder.mAllowObsoleteTls;
		this.mAllowInvalidTlsCertificates = builder.mAllowInvalidTlsCertificates;
		this.mAllowCleartextTraffic = builder.mAllowCleartextTraffic;
		this.mEnablePostQuantumKeyExchange = builder.mEnablePostQuantumKeyExchange;
		this.mEnableCache = builder.mEnableCache;
		this.mEnableBrotli = builder.mEnableBrotli;
		this.mFollowRedirect = builder.mFollowRedirect;
		this.mMaxIdleConnections = builder.mMaxIdleConnections;
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

	public boolean isObsoleteTlsAllowed() {
		return mAllowObsoleteTls;
	}

	public boolean isInvalidCertificatesAllowed() {
		return mAllowInvalidTlsCertificates;
	}

	public boolean isCleartextTrafficAllowed() {
		return mAllowCleartextTraffic;
	}

	public boolean isPostQuantumKeyExchangeEnabled() {
		return mEnablePostQuantumKeyExchange;
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

	public int getMaxIdleConnections() {
		return mMaxIdleConnections;
	}

	@SuppressWarnings("unused")
	public static final class Builder {
		private long mConnectTimeout = 15000; // 15 seconds
		private long mReadTimeout = 30000; // 30 seconds
		private long mWriteTimeout = 30000; // 30 seconds
		private long mCallTimeout = 0; // Inf
		private boolean mAllowObsoleteTls = false; // TLS 1.3 and 1.2 Only
		private boolean mAllowInvalidTlsCertificates = false; // Allow bad TLS Certificates
		private boolean mAllowCleartextTraffic = false; // HTTPS only
		private boolean mEnablePostQuantumKeyExchange = false; // X25519, P-256, P-384 only
		private boolean mEnableCache = false; // Cache disabled
		private boolean mEnableBrotli = true; // Brotli enabled
		private boolean mFollowRedirect = false; // Follow redirect disabled
		private int mMaxIdleConnections = 16; // Idle connections kept for reuse (OkHttp: 5)

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

		public Builder setAllowInvalidTlsCertificates(boolean allow) {
			this.mAllowInvalidTlsCertificates = allow;
			return this;
		}

		/**
		 * Allows plain HTTP (http://) requests. The Android network security config of the app
		 * must also permit cleartext traffic for the targeted hosts (see README).
		 */
		public Builder setAllowCleartextTraffic(boolean allow) {
			this.mAllowCleartextTraffic = allow;
			return this;
		}

		/**
		 * Offers the hybrid post-quantum key exchange X25519MLKEM768 first, then X25519, P-256 and P-384.
		 * Disabled by default: it adds ~1.2 KB to every TLS ClientHello (2 TCP packets instead of 1),
		 * which some middleboxes and old servers still fail on.
		 */
		public Builder setEnablePostQuantumKeyExchange(boolean enable) {
			this.mEnablePostQuantumKeyExchange = enable;
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

		/**
		 * Max number of idle connections kept for reuse, all hosts together (16 by default, OkHttp's default is 5).
		 * An HTTP/1.1 server needs one connection per parallel request: the connections above this limit
		 * are closed after use, and the next burst of requests pays new handshakes (TCP + TLS).
		 * HTTP/2 servers use a single connection.
		 */
		public Builder setMaxIdleConnections(int maxIdleConnections) {
			if (maxIdleConnections < 0) throw new IllegalArgumentException("maxIdleConnections < 0: " + maxIdleConnections);
			this.mMaxIdleConnections = maxIdleConnections;
			return this;
		}

		@NonNull
		public KnSettings build() {
			return new KnSettings(this);
		}
	}
}

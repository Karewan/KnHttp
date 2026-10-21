package ovh.karewan.knhttp.sample;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.alibaba.fastjson2.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.cert.Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Handshake;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ovh.karewan.knhttp.KnHttp;
import ovh.karewan.knhttp.common.KnRequest;
import ovh.karewan.knhttp.common.KnResponse;
import ovh.karewan.knhttp.common.Priority;
import ovh.karewan.knhttp.error.KnError;
import ovh.karewan.knhttp.interfaces.BitmapRequestListener;
import ovh.karewan.knhttp.interfaces.DownloadListener;
import ovh.karewan.knhttp.interfaces.JSONObjectRequestListener;
import ovh.karewan.knhttp.interfaces.OkHttpResponseListener;
import ovh.karewan.knhttp.interfaces.ParsedRequestListener;
import ovh.karewan.knhttp.interfaces.StringRequestListener;
import ovh.karewan.knhttp.internal.KnImageLoader;

/**
 * One button per feature. The results are written in the log area and in Logcat (tag KnHttpSample),
 * the HTTP calls are logged by KnHttp in Logcat (tag KnHttp).
 * A feature can also be started from adb, by a part of its label:
 * adb shell am start --activity-single-top -n ovh.karewan.knhttp.sample/.MainActivity --es run upload
 */
public class MainActivity extends Activity {
	private static final String TAG = "sample";
	private static final String LOG_TAG = "KnHttpSample";
	private static final String JSON_API = "https://jsonplaceholder.typicode.com";
	// Echo server, request bodies limited to ~512 KB
	private static final String HTTPBIN = "https://httpbingo.org";
	private static final String TLS_TRACE = "https://www.cloudflare.com/cdn-cgi/trace";
	// Cloudflare speed test endpoints: no size limit, nothing echoed
	private static final String DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=10000000";
	private static final String UPLOAD_URL = "https://speed.cloudflare.com/__up";
	private static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 64;
	private static final int REQUEST_LOCAL_NETWORK = 1;

	private final ExecutorService mBackground = Executors.newSingleThreadExecutor();
	private final Map<String, Runnable> mActions = new LinkedHashMap<>();
	private final SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

	private TextView mLog;
	private ScrollView mLogScroll;
	private ProgressBar mProgress;
	private ImageView mImage;
	private EditText mLanUrl;
	private LinearLayout mButtons;

	// Error body of a JSON API, e.g. {"message": "Not Found"}
	public static class ApiError {
		public String message;
	}

	public static class Post {
		public int userId;
		public int id;
		public String title;
		public String body;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mLog = findViewById(R.id.log);
		mLogScroll = findViewById(R.id.log_scroll);
		mProgress = findViewById(R.id.progress);
		mImage = findViewById(R.id.image);
		mLanUrl = findViewById(R.id.lan_url);
		mButtons = findViewById(R.id.buttons);

		final TextView info = findViewById(R.id.info);
		info.setText("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") · " + Build.MANUFACTURER + " " + Build.MODEL
				+ " · " + Build.SUPPORTED_ABIS[0] + " · KnHttp " + ovh.karewan.knhttp.BuildConfig.VERSION_NAME);

		addButton("Clear log", () -> {
			mLog.setText("");
			mProgress.setProgress(0);
			mImage.setImageDrawable(null);
		});
		addButton("TLS: version, cipher, key exchange, certificate", this::tlsCheck);
		addButton("GET JSON object", this::getJsonObject);
		addButton("GET object list (POJO mapping)", this::getObjectList);
		addButton("POST an object as JSON", this::postObject);
		addButton("Path & query parameters (encoded)", this::pathAndQuery);
		addButton("HTTP error 404", this::httpError);
		addButton("Invalid URL", this::invalidUrl);
		addButton("Error types and user messages", this::errorTypes);
		addButton("50 parallel requests", this::parallelRequests);
		addButton("Priorities (limit set to 1)", this::priorities);
		addButton("Per-request timeouts", this::timeouts);
		addButton("Cancel a request", this::cancelRequest);
		addButton("Download 10 MB with progress", this::download);
		addButton("Upload 2 MB with progress", this::upload);
		addButton("Bitmap (resized to the screen)", this::bitmap);
		addButton("KnImageLoader (memory cache, click twice)", this::imageLoader);
		addButton("Cleartext HTTP request", this::cleartext);
		addButton("LAN request (Android 17+ permission)", this::lanRequest);
		addButton("Synchronous request (background thread)", this::synchronousRequest);
		addButton("Shared OkHttpClient (Socket.IO style)", this::sharedClient);

		runFromIntent(getIntent());
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		runFromIntent(intent);
	}

	@Override
	protected void onDestroy() {
		KnHttp.cancel(TAG);
		mBackground.shutdownNow();
		super.onDestroy();
	}

	// region Features

	private void tlsCheck() {
		final long start = SystemClock.elapsedRealtime();

		KnHttp.get(TLS_TRACE).setTag(TAG).build().getAsOkHttpResponse(new OkHttpResponseListener() {
			@Override
			public void onResponse(@NonNull Response okHttpRes) {
				// Main thread delivery: the body is already in memory, reading it here is safe
				try (Response res = okHttpRes) {
					final Handshake handshake = res.handshake();
					log("HTTP protocol: " + res.protocol());

					if (handshake != null) {
						log("TLS: " + handshake.tlsVersion().javaName() + " / " + handshake.cipherSuite().javaName());
						final Certificate certificate = handshake.peerCertificates().get(0);
						log("Server certificate key: " + certificate.getPublicKey().getAlgorithm());
					}

					for (String line : res.body().string().split("\n")) {
						if (line.startsWith("kex=") || line.startsWith("tls=") || line.startsWith("http=")) log("Cloudflare sees " + line);
					}

					log("Done in " + elapsed(start));
				} catch (IOException e) {
					log("Error: " + e);
				}
			}

			@Override
			public void onError(@NonNull KnError err) {
				logError(err);
			}
		});
	}

	private void getJsonObject() {
		final long start = SystemClock.elapsedRealtime();

		KnHttp.get(JSON_API + "/posts/{id}").addPathParameter("id", "1").setTag(TAG).build().getAsJSONObject(new JSONObjectRequestListener() {
			@Override
			public void onResponse(@NonNull JSONObject obj, @NonNull Response okHttpRes) {
				log("Title: " + obj.getString("title") + " (" + elapsed(start) + ", " + okHttpRes.protocol() + ")");
			}

			@Override
			public void onError(@NonNull KnError err) {
				logError(err);
			}
		});
	}

	private void getObjectList() {
		final long start = SystemClock.elapsedRealtime();

		KnHttp.get(JSON_API + "/posts").setTag(TAG).build().getAsObjectList(Post.class, new ParsedRequestListener<List<Post>>() {
			@Override
			public void onResponse(@NonNull List<Post> posts, @NonNull Response okHttpRes) {
				log(posts.size() + " posts mapped, first: \"" + posts.get(0).title + "\" (" + elapsed(start) + ")");
			}

			@Override
			public void onError(@NonNull KnError err) {
				logError(err);
			}
		});
	}

	private void postObject() {
		final Post post = new Post();
		post.userId = 1;
		post.title = "KnHttp é ✓";
		post.body = "Sent with addObjectBody()";

		KnHttp.post(JSON_API + "/posts").addObjectBody(post).setTag(TAG).build().getAsObject(Post.class, new ParsedRequestListener<Post>() {
			@Override
			public void onResponse(@NonNull Post created, @NonNull Response okHttpRes) {
				log("Created post #" + created.id + ": \"" + created.title + "\" (HTTP " + okHttpRes.code() + ")");
			}

			@Override
			public void onError(@NonNull KnError err) {
				logError(err);
			}
		});
	}

	private void pathAndQuery() {
		KnHttp.get(HTTPBIN + "/anything/{name}")
				.addPathParameter("name", "a b/c?é")
				.addQueryParameter("q", "x y&z")
				.setTag(TAG)
				.build()
				.getAsJSONObject(new JSONObjectRequestListener() {
					@Override
					public void onResponse(@NonNull JSONObject obj, @NonNull Response okHttpRes) {
						log("Requested URL: " + okHttpRes.request().url());
						log("Server saw: " + obj.getString("url"));
					}

					@Override
					public void onError(@NonNull KnError err) {
						logError(err);
					}
				});
	}

	private void httpError() {
		KnHttp.get(HTTPBIN + "/status/404").setTag(TAG).build().getAsString(stringListener("404"));
	}

	private void invalidUrl() {
		KnHttp.get("htp//not a valid url").setTag(TAG).build().getAsString(stringListener("Invalid URL"));
	}

	/**
	 * One failing request per error type: the log shows the message an app would display
	 */
	private void errorTypes() {
		final StringRequestListener listener = stringListener("Unexpected success");
		KnHttp.get("https://nonexistent.invalid/").setTag(TAG).build().getAsString(listener); // NETWORK
		KnHttp.get(HTTPBIN + "/delay/3").setReadTimeout(1, TimeUnit.SECONDS).setTag(TAG).build().getAsString(listener); // TIMEOUT
		KnHttp.get("https://expired.badssl.com/").setTag(TAG).build().getAsString(listener); // TLS
		KnHttp.get(HTTPBIN + "/status/503").setTag(TAG).build().getAsString(listener); // HTTP, retryable
		KnHttp.get("https://api.github.com/repos/Karewan/does-not-exist").setTag(TAG).build().getAsString(listener); // HTTP, JSON error body
		KnHttp.get(HTTPBIN + "/html").setTag(TAG).build().getAsJSONObject(new JSONObjectRequestListener() { // PARSE
			@Override
			public void onResponse(@NonNull JSONObject obj, @NonNull Response okHttpRes) {
				log("Unexpected success");
			}

			@Override
			public void onError(@NonNull KnError err) {
				logError(err);
			}
		});

		mBackground.execute(() -> {
			final File notADirectory = new File(getCacheDir(), "not-a-directory");
			try {
				//noinspection ResultOfMethodCallIgnored
				notADirectory.createNewFile();
			} catch (IOException ignore) {
			}
			final KnResponse<String> res = KnHttp.download(DOWNLOAD_URL, notADirectory.getAbsolutePath(), "file.bin").build().executeForDownload(); // STORAGE
			if (!res.isSuccess()) logError(res.getError());
		});
	}

	private void parallelRequests() {
		final int count = 50;
		final long start = SystemClock.elapsedRealtime();
		final int[] done = { 0, 0 };

		for (int i = 1; i <= count; i++) {
			KnHttp.get(JSON_API + "/posts/{id}").addPathParameter("id", String.valueOf(i)).setTag(TAG).build().getAsString(new StringRequestListener() {
				@Override
				public void onResponse(@NonNull String str, @NonNull Response okHttpRes) {
					onDone(false);
				}

				@Override
				public void onError(@NonNull KnError err) {
					onDone(true);
				}

				private void onDone(boolean error) {
					done[0]++;
					if (error) done[1]++;
					if (done[0] == count) log(count + " requests done in " + elapsed(start) + " (" + done[1] + " errors)");
				}
			});
		}
	}

	private void priorities() {
		KnHttp.setMaxConcurrentRequests(1);
		log("Limit = 1: a slow LOW request takes the slot, LOW / MEDIUM / HIGH wait, IMMEDIATE ignores the limit");
		log("Expected order: immediate, slow, high, medium, low");

		final long start = SystemClock.elapsedRealtime();
		final List<String> finished = new ArrayList<>();
		final String[] names = { "slow", "low", "medium", "high", "immediate" };
		final Priority[] priorities = { Priority.LOW, Priority.LOW, Priority.MEDIUM, Priority.HIGH, Priority.IMMEDIATE };

		for (int i = 0; i < names.length; i++) {
			final String name = names[i];
			final String url = name.equals("slow") ? HTTPBIN + "/delay/2" : HTTPBIN + "/get?p=" + name;

			KnHttp.get(url).setPriority(priorities[i]).setTag(TAG).build().getAsString(new StringRequestListener() {
				@Override
				public void onResponse(@NonNull String str, @NonNull Response okHttpRes) {
					onDone();
				}

				@Override
				public void onError(@NonNull KnError err) {
					logError(err);
					onDone();
				}

				private void onDone() {
					finished.add(name);
					log("Finished: " + name + " (+" + elapsed(start) + ")");

					if (finished.size() == names.length) {
						KnHttp.setMaxConcurrentRequests(DEFAULT_MAX_CONCURRENT_REQUESTS);
						log("Order: " + finished + ", limit restored to " + DEFAULT_MAX_CONCURRENT_REQUESTS);
					}
				}
			});
		}
	}

	private void timeouts() {
		final long start = SystemClock.elapsedRealtime();
		log("Server answers after 3 s, read timeout of this request: 1 s");

		KnHttp.get(HTTPBIN + "/delay/3").setReadTimeout(1, TimeUnit.SECONDS).setTag(TAG).build().getAsString(new StringRequestListener() {
			@Override
			public void onResponse(@NonNull String str, @NonNull Response okHttpRes) {
				log("Unexpected success after " + elapsed(start));
			}

			@Override
			public void onError(@NonNull KnError err) {
				log("Timeout as expected after " + elapsed(start) + ": " + err.getCause());
				log("Same request with a read timeout of 10 s...");

				final long start2 = SystemClock.elapsedRealtime();
				KnHttp.get(HTTPBIN + "/delay/3").setReadTimeout(10, TimeUnit.SECONDS).setTag(TAG).build().getAsString(new StringRequestListener() {
					@Override
					public void onResponse(@NonNull String str, @NonNull Response okHttpRes) {
						log("Success after " + elapsed(start2));
					}

					@Override
					public void onError(@NonNull KnError err) {
						logError(err);
					}
				});
			}
		});
	}

	private void cancelRequest() {
		final long start = SystemClock.elapsedRealtime();

		KnHttp.get(HTTPBIN + "/delay/5").setTag("cancel-demo").build().getAsString(new StringRequestListener() {
			@Override
			public void onResponse(@NonNull String str, @NonNull Response okHttpRes) {
				log("Unexpected success");
			}

			@Override
			public void onError(@NonNull KnError err) {
				log("onError() after " + elapsed(start) + ", cancellation = " + err.isCancellation());
			}
		});

		mLog.postDelayed(() -> {
			log("KnHttp.cancel(\"cancel-demo\")");
			KnHttp.cancel("cancel-demo");
		}, 500);
	}

	private void download() {
		final long start = SystemClock.elapsedRealtime();
		final int[] progressEvents = { 0 };
		mProgress.setProgress(0);

		KnHttp.download(DOWNLOAD_URL, getCacheDir().getAbsolutePath(), "download.bin")
				.setTag(TAG)
				.build()
				.setDownloadProgressListener((downloaded, total) -> {
					progressEvents[0]++;
					if (total > 0) mProgress.setProgress((int) (downloaded * 1000 / total));
				})
				.startDownload(new DownloadListener() {
					@Override
					public void onDownloadComplete(@NonNull Response okHttpRes) {
						final long ms = Math.max(1, SystemClock.elapsedRealtime() - start);
						final long size = new File(getCacheDir(), "download.bin").length();
						log(String.format(Locale.US, "%,d bytes in %d ms (%.1f MB/s), %d progress callbacks", size, ms, size / 1000f / ms, progressEvents[0]));
					}

					@Override
					public void onError(@NonNull KnError err) {
						logError(err);
					}
				});
	}

	private void upload() {
		mProgress.setProgress(0);

		mBackground.execute(() -> {
			final File file = new File(getCacheDir(), "upload.bin");
			try (FileOutputStream out = new FileOutputStream(file)) {
				final byte[] bytes = new byte[2 * 1024 * 1024];
				new Random().nextBytes(bytes);
				out.write(bytes);
			} catch (IOException e) {
				log("Unable to create the file: " + e);
				return;
			}

			runOnUiThread(() -> {
				final long start = SystemClock.elapsedRealtime();
				KnHttp.upload(UPLOAD_URL)
						.addMultipartParameter("name", "KnHttp")
						.addMultipartFile("file", file, "application/octet-stream")
						.setTag(TAG)
						.build()
						.setUploadProgressListener((uploaded, total) -> {
							if (total > 0) mProgress.setProgress((int) (uploaded * 1000 / total));
						})
						.getAsString(new StringRequestListener() {
							@Override
							public void onResponse(@NonNull String str, @NonNull Response okHttpRes) {
								final long ms = Math.max(1, SystemClock.elapsedRealtime() - start);
								log(String.format(Locale.US, "%,d bytes uploaded in %d ms (%.1f MB/s, HTTP %d)", file.length(), ms, file.length() / 1000f / ms, okHttpRes.code()));
							}

							@Override
							public void onError(@NonNull KnError err) {
								logError(err);
							}
						});
			});
		});
	}

	private void bitmap() {
		final long start = SystemClock.elapsedRealtime();

		KnHttp.get("https://picsum.photos/1600/1200")
				.setBitmapMaxWidth(getResources().getDisplayMetrics().widthPixels)
				.setBitmapMaxHeight(mImage.getHeight())
				.setBitmapConfig(Bitmap.Config.RGB_565)
				.setTag(TAG)
				.build()
				.getAsBitmap(new BitmapRequestListener() {
					@Override
					public void onResponse(@NonNull Bitmap bitmap, @NonNull Response okHttpRes) {
						mImage.setImageBitmap(bitmap);
						log("Bitmap " + bitmap.getWidth() + "x" + bitmap.getHeight() + " " + bitmap.getConfig() + ", " + bitmap.getAllocationByteCount() / 1024 + " KB (" + elapsed(start) + ")");
					}

					@Override
					public void onError(@NonNull KnError err) {
						logError(err);
					}
				});
	}

	private void imageLoader() {
		final long start = SystemClock.elapsedRealtime();
		log("Image cache created: " + (KnImageLoader.peekImageCache() != null));

		KnImageLoader.gi().get("https://picsum.photos/id/237/1200/900", new KnImageLoader.ImageListener() {
			@Override
			public void onResponse(KnImageLoader.ImageContainer response, boolean isImmediate) {
				if (response.getBitmap() == null) return; // Not cached: called again once downloaded

				mImage.setImageBitmap(response.getBitmap());
				log((isImmediate ? "From the memory cache" : "Downloaded") + " in " + elapsed(start));
			}

			@Override
			public void onError(KnError knError) {
				logError(knError);
			}
		}, getResources().getDisplayMetrics().widthPixels, mImage.getHeight());
	}

	private void cleartext() {
		// From the network: a cached response would not test the cleartext connection
		KnHttp.get("http://neverssl.com/").getResponseOnlyFromNetwork().setTag(TAG).build().getAsString(stringListener("Cleartext"));
	}

	private void lanRequest() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
				&& getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.CINNAMON_BUN
				&& checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
			log("Requesting ACCESS_LOCAL_NETWORK...");
			requestPermissions(new String[] { Manifest.permission.ACCESS_LOCAL_NETWORK }, REQUEST_LOCAL_NETWORK);
			return;
		}

		doLanRequest();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode != REQUEST_LOCAL_NETWORK) return;

		final boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
		log("ACCESS_LOCAL_NETWORK " + (granted ? "granted" : "denied: the request will be blocked by the system"));
		doLanRequest();
	}

	private void doLanRequest() {
		final String url = mLanUrl.getText().toString().trim();
		final long start = SystemClock.elapsedRealtime();

		KnHttp.get(url).setConnectTimeout(3, TimeUnit.SECONDS).setTag(TAG).build().getAsString(new StringRequestListener() {
			@Override
			public void onResponse(@NonNull String str, @NonNull Response okHttpRes) {
				log("LAN: HTTP " + okHttpRes.code() + ", " + str.length() + " chars (" + elapsed(start) + ")");
			}

			@Override
			public void onError(@NonNull KnError err) {
				logError(err);
			}
		});
	}

	private void synchronousRequest() {
		mBackground.execute(() -> {
			final long start = SystemClock.elapsedRealtime();
			final KnResponse<Post> res = KnHttp.get(JSON_API + "/posts/1").build().executeForObject(Post.class);

			if (res.isSuccess()) log("Sync on " + Thread.currentThread().getName() + ": \"" + res.getResult().title + "\" (" + elapsed(start) + ")");
			else logError(res.getError());
		});
	}

	private void sharedClient() {
		mBackground.execute(() -> {
			final OkHttpClient client = KnHttp.getOkHttpClient();

			try (Response res = client.newCall(new Request.Builder().url(TLS_TRACE).build()).execute()) {
				final Handshake handshake = res.handshake();
				log("Plain OkHttp call with KnHttp's client: " + res.protocol() + (handshake != null ? ", " + handshake.tlsVersion().javaName() : ""));
			} catch (IOException e) {
				log("Error: " + e);
			}
		});
	}

	// endregion

	// region Helpers

	private void addButton(String label, Runnable action) {
		final Runnable logged = () -> {
			log("▶ " + label);
			action.run();
		};
		mActions.put(label, logged);

		final Button button = new Button(this);
		button.setText(label);
		button.setAllCaps(false);
		button.setOnClickListener(v -> logged.run());
		mButtons.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	/**
	 * Runs the first feature whose label contains the "run" extra (case insensitive)
	 */
	private void runFromIntent(Intent intent) {
		final String run = intent != null ? intent.getStringExtra("run") : null;
		if (run == null) return;

		for (Map.Entry<String, Runnable> entry : mActions.entrySet()) {
			if (entry.getKey().toLowerCase(Locale.ROOT).contains(run.toLowerCase(Locale.ROOT))) {
				entry.getValue().run();
				return;
			}
		}
		log("No feature matching \"" + run + "\"");
	}

	private StringRequestListener stringListener(String label) {
		final long start = SystemClock.elapsedRealtime();

		return new StringRequestListener() {
			@Override
			public void onResponse(@NonNull String str, @NonNull Response okHttpRes) {
				final String source = okHttpRes.networkResponse() == null ? ", from the HTTP cache" : "";
				log(label + ": HTTP " + okHttpRes.code() + ", " + str.length() + " chars (" + elapsed(start) + source + ")");
			}

			@Override
			public void onError(@NonNull KnError err) {
				logError(err);
			}
		};
	}

	private void logError(KnError err) {
		final StringBuilder sb = new StringBuilder("Error ").append(err.getType());
		if (err.getErrorCode() != 0) sb.append(' ').append(err.getErrorCode());
		if (err.isRetryable()) sb.append(" (retryable)");
		sb.append(" → \"").append(userMessage(err)).append('"');
		if (err.getCause() != null) sb.append(", cause: ").append(err.getCause());
		log(sb.toString());
	}

	/**
	 * What an app would show to the user, by error type (use string resources in a real app)
	 */
	private static String userMessage(KnError err) {
		switch (err.getType()) {
			case NETWORK:
				return "No connection, check your network";
			case TIMEOUT:
				return "The server is taking too long to respond";
			case TLS:
				return "Secure connection failed, check the date of the device or the Wi-Fi network";
			case HTTP: {
				final ApiError apiError = err.getErrorBodyAs(ApiError.class);
				if (apiError != null && apiError.message != null) return apiError.message; // The server message
				return err.getErrorCode() >= 500 ? "The service is unavailable, try again later" : "The request was refused";
			}
			case STORAGE:
				return "Unable to save the file, check the storage space";
			case CANCELLED:
				return "Cancelled";
			default: // PARSE, INVALID_REQUEST, UNKNOWN: bugs, a generic message
				return "Something went wrong";
		}
	}

	private void log(String message) {
		Log.i(LOG_TAG, message);
		if (Looper.myLooper() != Looper.getMainLooper()) {
			runOnUiThread(() -> append(message));
			return;
		}
		append(message);
	}

	private void append(String message) {
		mLog.append(mTimeFormat.format(new Date()) + "  " + message + "\n");
		mLogScroll.post(() -> mLogScroll.fullScroll(View.FOCUS_DOWN));
	}

	private static String elapsed(long start) {
		return (SystemClock.elapsedRealtime() - start) + " ms";
	}

	// endregion
}

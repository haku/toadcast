package com.vaguehope.toadcast;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import su.litvak.chromecast.api.v2.Application;
import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.MediaStatus;
import su.litvak.chromecast.api.v2.Status;

public class GoalSeeker implements Runnable {

	private static final String CHROME_CAST_DEFAULT_APP_ID = "CC1AD845";
	private static final Logger LOG = LoggerFactory.getLogger(GoalSeeker.class);

	private final AtomicReference<ChromeCast> chromecastHolder;

	private volatile Timestamped<MediaStatus> mediaStatus;

	public GoalSeeker (final AtomicReference<ChromeCast> chromecastHolder) {
		this.chromecastHolder = chromecastHolder;
		this.mediaStatus = new Timestamped<MediaStatus>(null);
	}

	@Override
	public void run () {
		try {
			callUnsafe();
		}
		catch (final Exception e) {
			LOG.warn("Unhandled error while reading / writing Chromecast state.", e);
			try {
				Thread.sleep(TimeUnit.SECONDS.toMillis(10));// Rate limit errors.
			}
			catch (final InterruptedException e1) {/* Unused. */}
		}
	}

	private void callUnsafe () throws IOException {
		final ChromeCast c = this.chromecastHolder.get();
		if (c == null || !c.isConnected()) return;

		// TODO If persistent connecting issues, forget and restart discovery.

		final Status status = c.getStatus();
		final Application runningApp = status != null ? status.getRunningApp() : null;
		final String runningAppId = runningApp != null ? runningApp.id : null;

		if (!CHROME_CAST_DEFAULT_APP_ID.equals(runningAppId)) {
			if (runningAppId != null) {
				LOG.info("Running app not default, stopping: {}", runningApp);
				c.stopApp();
			}
			LOG.info("Launching default app...");
			c.launchApp(CHROME_CAST_DEFAULT_APP_ID);
		}

		final MediaStatus mediaStatus = c.getMediaStatus();
		this.mediaStatus = new Timestamped<>(mediaStatus);
	}

	/**
	 * Will not return null.
	 */
	public Timestamped<MediaStatus> getMediaStatus () {
		return this.mediaStatus;
	}

}

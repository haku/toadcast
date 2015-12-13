package com.vaguehope.toadcast;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import su.litvak.chromecast.api.v2.Application;
import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.Media;
import su.litvak.chromecast.api.v2.MediaStatus;
import su.litvak.chromecast.api.v2.MediaStatus.PlayerState;
import su.litvak.chromecast.api.v2.Status;

public class GoalSeeker implements Runnable {

	private static final String CHROME_CAST_DEFAULT_APP_ID = "CC1AD845";
	private static final Logger LOG = LoggerFactory.getLogger(GoalSeeker.class);

	private final AtomicReference<ChromeCast> chromecastHolder;

	// Where we are.
	private volatile Timestamped<MediaStatus> currentMediaStatus;

	// Where we want to be.
	private volatile PlayingState targetPlayingState;
	private volatile boolean targetPaused;

	public GoalSeeker (final AtomicReference<ChromeCast> chromecastHolder) {
		this.chromecastHolder = chromecastHolder;
		this.currentMediaStatus = new Timestamped<MediaStatus>(null);
		this.targetPlayingState = null;
		this.targetPaused = false;
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

		readyChromeCast(c);
		final MediaStatus cStatus = readCurrent(c);
		seekGoal(c, cStatus);
	}

	private void readyChromeCast (final ChromeCast c) throws IOException {
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
	}

	private MediaStatus readCurrent (final ChromeCast c) throws IOException {
		final MediaStatus cStatus = c.getMediaStatus();
		this.currentMediaStatus = new Timestamped<>(cStatus);
		return cStatus;
	}

	private void seekGoal (final ChromeCast c, final MediaStatus cStatus) throws IOException {
		// Capture target.
		final PlayingState tState = this.targetPlayingState;
		final boolean tPaused = this.targetPaused;

		// Get things ready to compare.
		final Media cMedia = cStatus != null ? cStatus.media : null;
		final String cUrl = cMedia != null ? StringUtils.trimToNull(cMedia.url) : null;
		final String tUri = tState != null ? StringUtils.trimToNull(tState.getMediaInfo().getCurrentURI()) : null;
		final PlayerState cState = cStatus != null ? cStatus.playerState : null;

		// Should stop?
		if (tState == null || tUri == null) {
			if (cState != null) {
				switch (cState) {
					case BUFFERING:
					case PLAYING:
						c.pause();
						LOG.info("Stopped.");
					default:
				}
			}
			return;// Target state reached.  Stop.
		}

		// Got right URI?
		if (!Objects.equals(cUrl, tUri)) {
			c.load(tState.getTitle(), tState.getRelativeArtUri(), tState.getMediaInfo().getCurrentURI(), tState.getContentType());
			LOG.info("Loaded {}.", tState.getMediaInfo().getCurrentURI());
			return;
		}

		// Should resume / pause?
		if (tPaused) {
			if (cState == PlayerState.PLAYING) {
				c.pause();
				LOG.info("Paused.");
				return;
			}
		}
		else {
			if (cState == PlayerState.PAUSED) {
				c.play();
				LOG.info("Resumed.");
				return;
			}
		}
	}

	/**
	 * Will not return null.
	 */
	public Timestamped<MediaStatus> getCurrentMediaStatus () {
		return this.currentMediaStatus;
	}

	public PlayingState getTargetPlayingState () {
		return this.targetPlayingState;
	}

	public void gotoPlaying (final PlayingState playingState) {
		this.targetPlayingState = playingState;
		this.targetPaused = false;
	}

	public void gotoPaused () {
		this.targetPaused = true;
	}

	public void gotoResumed () {
		this.targetPaused = false;
	}

	public void gotoStopped () {
		this.targetPlayingState = null;
	}

}

package com.vaguehope.toadcast;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import su.litvak.chromecast.api.v2.Application;
import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.ChromeCastEventListener;
import su.litvak.chromecast.api.v2.Media;
import su.litvak.chromecast.api.v2.MediaStatus;
import su.litvak.chromecast.api.v2.MediaStatus.IdleReason;
import su.litvak.chromecast.api.v2.MediaStatus.PlayerState;
import su.litvak.chromecast.api.v2.Status;

public class GoalSeeker implements Runnable, ChromeCastEventListener {

	private static final Set<IdleReason> GOAL_REACHED_IF_IDLE_REASONS = EnumSet.of(IdleReason.CANCELLED, IdleReason.INTERRUPTED, IdleReason.FINISHED, IdleReason.ERROR, IdleReason.COMPLETED);

	private static final String CHROME_CAST_DEFAULT_APP_ID = "CC1AD845";
	private static final Logger LOG = LoggerFactory.getLogger(GoalSeeker.class);

	private final AtomicReference<ChromeCast> chromecastHolder;

	// Where we are.
	private volatile Timestamped<MediaStatus> currentMediaStatus;
	private volatile long ourMediaSessionId;
	private final Queue<MediaStatus> pushedStatus = new LinkedBlockingQueue<MediaStatus>();

	// Where we want to be.
	private volatile PlayingState targetPlayingState;
	private volatile boolean targetPaused;

	public GoalSeeker (final AtomicReference<ChromeCast> chromecastHolder) {
		this.chromecastHolder = chromecastHolder;
		setCurrentMediaStatus(null);
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
		readPushedStatus(); // Specifically after readCurrent().
		seekGoal(c, cStatus);
	}

	private static void readyChromeCast (final ChromeCast c) throws IOException {
		final Status status = c.getStatus();
		final Application runningApp = status != null ? status.getRunningApp() : null;
		final String runningAppId = runningApp != null ? runningApp.id : null;

		// TODO do not start app if no target track to play?
		if (!CHROME_CAST_DEFAULT_APP_ID.equals(runningAppId)) {
			if (runningAppId != null) {
				LOG.info("Running app not default, stopping: {}", runningApp);
				c.stopApp();
			}
			LOG.info("Launching default app...");
			c.launchApp(CHROME_CAST_DEFAULT_APP_ID);
		}
	}

	/**
	 * NOTE: Pushed MEDIA_STATUS objects are incomplete and not suitable for general goal seeking.
	 */
	private void readPushedStatus () {
		MediaStatus status;
		while ((status = this.pushedStatus.poll()) != null) {
			if (status.mediaSessionId == this.ourMediaSessionId && GOAL_REACHED_IF_IDLE_REASONS.contains(status.idleReason)) {
				LOG.info("Session {} goal reached by idle reason: {}", this.ourMediaSessionId, status.idleReason);
				this.targetPlayingState = null;
			}
		}
	}

	private MediaStatus readCurrent (final ChromeCast c) throws IOException {
		final MediaStatus cStatus = c.getMediaStatus();
		setCurrentMediaStatus(cStatus);
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
			final MediaStatus afterLoad = c.load(tState.getTitle(), tState.getRelativeArtUri(), tState.getMediaInfo().getCurrentURI(), tState.getContentType());
			if (afterLoad != null) {
				this.ourMediaSessionId = afterLoad.mediaSessionId;
				setCurrentMediaStatus(afterLoad);
			}
			else {
				this.ourMediaSessionId = -2;
			}
			LOG.info("Loaded {} (session={}).", tState.getMediaInfo().getCurrentURI(), this.ourMediaSessionId);
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
	 * @param newStatus
	 *            May be null.
	 */
	private void setCurrentMediaStatus (final MediaStatus newStatus) {
		this.currentMediaStatus = new Timestamped<>(newStatus);
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

	@Override
	public void onSpontaneousMediaStatus (final MediaStatus mediaStatus) {
		LOG.info("Spontaneous media status: mediaSessionId={} playerState={} idleReason={}",
				mediaStatus.mediaSessionId, mediaStatus.playerState, mediaStatus.idleReason);
		this.pushedStatus.add(mediaStatus);
	}

	@Override
	public void onSpontaneousStatus (final Status status) {
		LOG.debug("Spontaneous status: {}", status);
	}

	@Override
	public void onUnidentifiedSpontaneousEvent (final JsonNode event) {
		LOG.debug("Spontaneous event: {}", event);
	}

}

package com.vaguehope.toadcast;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.ChromeCastEventListener;
import su.litvak.chromecast.api.v2.ChromeCasts;
import su.litvak.chromecast.api.v2.Media;
import su.litvak.chromecast.api.v2.MediaStatus;
import su.litvak.chromecast.api.v2.MediaStatus.IdleReason;
import su.litvak.chromecast.api.v2.MediaStatus.PlayerState;
import su.litvak.chromecast.api.v2.Status;

public class GoalSeeker implements Runnable, ChromeCastEventListener {

	private static final long POLL_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(1);

	/**
	 * If nothing happy back from ChromeCast in the time, start everything over.
	 * TODO Is this too long?
	 */
	private static final long GIVEUP_AND_REDISCOVER_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(1);

	/**
	 * Only try and restore position if played at least this far though.
	 */
	private static final int MIN_POSITION_TO_RESTORE_SECONDS = 1;

	private static final Set<IdleReason> GOAL_REACHED_IF_IDLE_REASONS = EnumSet.of(IdleReason.CANCELLED, IdleReason.INTERRUPTED, IdleReason.FINISHED, IdleReason.ERROR, IdleReason.COMPLETED);

	private static final Logger LOG = LoggerFactory.getLogger(GoalSeeker.class);

	private final AtomicReference<ChromeCast> chromecastHolder;

	// Reliability tracking.
	private volatile long lastSuccessTime;

	// Where we are.
	private volatile Timestamped<MediaStatus> currentMediaStatus;
	private volatile long ourMediaSessionId;
	private final BlockingQueue<Object> eventQueue = new LinkedBlockingQueue<>();

	// Where we want to be.
	private volatile PlayingState targetPlayingState = null;
	private volatile boolean targetPaused = false;
	private volatile long seekToSeconds = -1;

	// Recovery info.
	private volatile double lastObservedPosition = 0;

	public GoalSeeker (final AtomicReference<ChromeCast> chromecastHolder) {
		this.chromecastHolder = chromecastHolder;
		setCurrentMediaStatus(null);
	}

	@Override
	public void run () {
		try {
			while (true) {
				poll();
			}
		}
		finally {
			LOG.warn("Ended.");
		}
	}

	private void poll () {
		try {
			readEventQueue(); // Blocks / rate limits.
			connectAndReadStateAndSeekGoal();
		}
		catch (final Exception e) {
			LOG.warn("Unhandled error while reading / writing ChromeCast state.", e);
			try {
				Thread.sleep(TimeUnit.SECONDS.toMillis(10));// Rate limit errors.
			}
			catch (final InterruptedException e1) {/* Unused. */}
		}
	}

	private void readEventQueue () {
		try {
			long timeoutMillis = POLL_INTERVAL_MILLIS;
			Object obj;
			while ((obj = this.eventQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS)) != null) {
				timeoutMillis = 0L; // If something happened, stop sleeping.
				if (obj instanceof Boolean) {
					// For short-circuiting timeout after goal change.
				}
				else if (obj instanceof Long) {
					this.seekToSeconds = (Long) obj;
				}
				else if (obj instanceof MediaStatus) {
					onEventMediaStatus((MediaStatus) obj);
				}
				else {
					LOG.warn("Unexpected {} type on event queue: {}", obj.getClass(), obj);
				}
			}
		}
		catch (final InterruptedException e) {/* Unused. */ }
	}

	/**
	 * NOTE: Pushed MEDIA_STATUS objects are incomplete and not suitable for general goal seeking.
	 */
	private void onEventMediaStatus (final MediaStatus status) {
		if (status.mediaSessionId == this.ourMediaSessionId && GOAL_REACHED_IF_IDLE_REASONS.contains(status.idleReason)) {
			LOG.info("Session {} goal reached by idle reason: {}", this.ourMediaSessionId, status.idleReason);
			this.targetPlayingState = null;
			this.lastObservedPosition = 0;
		}
	}

	private void connectAndReadStateAndSeekGoal () throws IOException {
		final ChromeCast c = this.chromecastHolder.get();
		if (c == null) {
			markLastSuccess(); // Did nothing successfully.
			return;
		}

		if (!c.isConnected()) {
			try {
				c.connect();
				LOG.info("Connected to ChromeCast {}.", c.getAddress());
				markLastSuccess();
			}
			catch (final GeneralSecurityException e) {
				LOG.warn("Failed to connect: ", e);
				checkNoSuccessTimeout(c);
				return;
			}
		};

		try {
			readStatusAndSeekGoal(c);
			markLastSuccess();
		}
		catch (NotConnectedExecption | NoResponseException e) {
			LOG.info("ChromeCast connection error: {}", e.toString());
			checkNoSuccessTimeout(c);
		}
	}

	private void markLastSuccess () {
		this.lastSuccessTime = System.currentTimeMillis();
	}

	private void checkNoSuccessTimeout (final ChromeCast c) throws IOException {
		final long millisSinceLastSuccess = System.currentTimeMillis() - this.lastSuccessTime;
		if (millisSinceLastSuccess > GIVEUP_AND_REDISCOVER_TIMEOUT_MILLIS) {
			if (this.chromecastHolder.compareAndSet(c, null)) {
				LOG.info("Abandoning non-responsive ChromeCast {} after {}s, re-discovering...",
						c.getAddress(), TimeUnit.MILLISECONDS.toSeconds(millisSinceLastSuccess));
				try {
					c.disconnect();
				}
				finally {
					ChromeCasts.startDiscovery(); // Listener in Main should still be registered.
				}
			}
		}
	}

	private void readStatusAndSeekGoal (final ChromeCast c) throws IOException {
		final Status status = CastHelper.readStatus(c);

		final MediaStatus mStatus;
		if (CastHelper.isRunningDefaultApp(status)) {
			mStatus = CastHelper.readMediaStatus(c);
		}
		else {
			mStatus = null;
		}
		setCurrentMediaStatus(mStatus);

		seekGoal(c, status, mStatus);
	}

	private void seekGoal (final ChromeCast c, final Status cStatus, final MediaStatus cMStatus) throws IOException {
		// Capture target.
		final PlayingState tState = this.targetPlayingState;
		final boolean tPaused = this.targetPaused;

		if (tState == null && !CastHelper.isRunningDefaultApp(cStatus)) {
			return; // If we do not have a target and default app is not running, do not mess with anything.
		}

		// Get things ready to compare.
		final Media cMedia = cMStatus != null ? cMStatus.media : null;
		final String cUrl = cMedia != null ? StringUtils.trimToNull(cMedia.url) : null;
		final String tUri = tState != null ? StringUtils.trimToNull(tState.getMediaInfo().getCurrentURI()) : null;
		final PlayerState cState = cMStatus != null ? cMStatus.playerState : null;

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
			return; // Target state reached.  Stop.
		}

		// Got right URI?
		if (!Objects.equals(cUrl, tUri)) {
			if (tPaused) return; // We would load, but will wait until not paused before doing so.

			CastHelper.readyChromeCast(c, cStatus);
			final MediaStatus afterLoad = c.load(tState.getTitle(), tState.getRelativeArtUri(), tState.getMediaInfo().getCurrentURI(), tState.getContentType());
			if (this.lastObservedPosition > MIN_POSITION_TO_RESTORE_SECONDS) {
				c.seek(this.lastObservedPosition);
				LOG.info("Restored position to {}s.", this.lastObservedPosition);
			}
			if (afterLoad != null) {
				this.ourMediaSessionId = afterLoad.mediaSessionId;
				setCurrentMediaStatus(afterLoad);
			}
			else {
				this.ourMediaSessionId = -2;
			}
			LOG.info("Loaded {} (session={}).", tState.getMediaInfo().getCurrentURI(), this.ourMediaSessionId);
			return; // Made a change, so return.
		}

		// Should resume / pause?
		if (tPaused) {
			if (cState == PlayerState.PLAYING) {
				c.pause();
				LOG.info("Paused.");
				return; // Made a change, so return.
			}
		}
		else {
			if (cState == PlayerState.PAUSED) {
				c.play();
				LOG.info("Resumed.");
				return; // Made a change, so return.
			}
		}

		// Check and set should be safe as only our thread should be updating it.
		if (this.seekToSeconds >= 0) {
			c.seek(this.seekToSeconds);
			LOG.info("Set position to {}s.", this.seekToSeconds);
			this.seekToSeconds = -1;
		}

		this.lastObservedPosition = cMStatus.currentTime;
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
		this.lastObservedPosition = 0; // Set before state.
		this.targetPlayingState = playingState;
		this.targetPaused = false;
		this.eventQueue.offer(Boolean.TRUE);
	}

	public void gotoPaused () {
		this.targetPaused = true;
		this.eventQueue.offer(Boolean.TRUE);
	}

	public void gotoResumed () {
		this.targetPaused = false;
		this.eventQueue.offer(Boolean.TRUE);
	}

	public void gotoStopped () {
		this.targetPlayingState = null;
		this.lastObservedPosition = 0; // Set after state.
		this.eventQueue.offer(Boolean.TRUE);
	}

	public void seek (final long targetSeconds) {
		this.eventQueue.offer(Long.valueOf(targetSeconds));
	}

	@Override
	public void onSpontaneousMediaStatus (final MediaStatus mediaStatus) {
		LOG.info("Spontaneous media status: mediaSessionId={} playerState={} idleReason={}",
				mediaStatus.mediaSessionId, mediaStatus.playerState, mediaStatus.idleReason);
		try {
			this.eventQueue.put(mediaStatus);
		}
		catch (final InterruptedException e) {
			LOG.warn("Interupted while trying to enqueue spontaneous media status: {}", mediaStatus);
		}
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

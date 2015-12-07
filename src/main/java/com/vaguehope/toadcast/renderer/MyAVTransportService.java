package com.vaguehope.toadcast.renderer;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

import org.fourthline.cling.model.ModelUtil;
import org.fourthline.cling.model.types.ErrorCode;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.support.avtransport.AVTransportErrorCode;
import org.fourthline.cling.support.avtransport.AVTransportException;
import org.fourthline.cling.support.avtransport.AbstractAVTransportService;
import org.fourthline.cling.support.lastchange.LastChange;
import org.fourthline.cling.support.model.DeviceCapabilities;
import org.fourthline.cling.support.model.MediaInfo;
import org.fourthline.cling.support.model.PlayMode;
import org.fourthline.cling.support.model.PositionInfo;
import org.fourthline.cling.support.model.StorageMedium;
import org.fourthline.cling.support.model.TransportAction;
import org.fourthline.cling.support.model.TransportInfo;
import org.fourthline.cling.support.model.TransportSettings;
import org.fourthline.cling.support.model.TransportState;
import org.seamless.http.HttpFetch;
import org.seamless.util.URIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.Media;
import su.litvak.chromecast.api.v2.MediaStatus;
import su.litvak.chromecast.api.v2.MediaStatus.PlayerState;

public class MyAVTransportService extends AbstractAVTransportService {

	private static final Logger LOG = LoggerFactory.getLogger(MyAVTransportService.class);

	private final ChromeCast chromecast;

	private volatile MediaInfo currentMediaInfo = new MediaInfo();

	public MyAVTransportService (final LastChange lastChange, final ChromeCast chromecast) {
		super(lastChange);
		this.chromecast = chromecast;
	}

	@Override
	public UnsignedIntegerFourBytes[] getCurrentInstanceIds () {
		return new UnsignedIntegerFourBytes[] { new UnsignedIntegerFourBytes(0) };
	}

	@Override
	public void setAVTransportURI (final UnsignedIntegerFourBytes instanceId, final String currentURI, final String currentURIMetaData) throws AVTransportException {
		LOG.info("setAVTransportURI({}, {}, {})", instanceId, currentURI, currentURIMetaData);

		final URI uri;
		try {
			uri = new URI(currentURI);
		}
		catch (final Exception ex) {
			throw new AVTransportException(ErrorCode.INVALID_ARGS, "CurrentURI can not be null or malformed");
		}

		if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
			try {
				HttpFetch.validate(URIUtil.toURL(uri));
			}
			catch (final Exception ex) {
				throw new AVTransportException(AVTransportErrorCode.RESOURCE_NOT_FOUND, ex.getMessage());
			}
		}
		else {
			throw new AVTransportException(ErrorCode.INVALID_ARGS, "Only HTTP and HTTPS: resource identifiers are supported, not '" + uri.getScheme() + "'.");
		}

		this.currentMediaInfo = new MediaInfo(uri.toString(), currentURIMetaData);
	}

	@Override
	public void setNextAVTransportURI (final UnsignedIntegerFourBytes instanceId, final String nextURI, final String nextURIMetaData) throws AVTransportException {
		LOG.debug("TODO setNextAVTransportURI({}, {}, {})", instanceId, nextURI, nextURIMetaData);
	}

	@Override
	public MediaInfo getMediaInfo (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		return this.currentMediaInfo;
	}

	@Override
	public TransportInfo getTransportInfo (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		try {
			final MediaStatus mediaStatus = this.chromecast.getMediaStatus(); // TODO cache this?

			final TransportState transportState;
			if (mediaStatus != null) {
				switch (mediaStatus.playerState) {
					case BUFFERING:
						transportState = TransportState.TRANSITIONING;
						break;
					case PLAYING:
						transportState = TransportState.PLAYING;
						break;
					case PAUSED:
						transportState = TransportState.PAUSED_PLAYBACK;
						break;
					case IDLE:
					default:
						transportState = TransportState.NO_MEDIA_PRESENT;
				}
			}
			else {
				transportState = TransportState.NO_MEDIA_PRESENT;
			}

			return new TransportInfo(transportState);
		}
		catch (final IOException e) {
			throw new AVTransportException(ErrorCode.ACTION_FAILED.getCode(), e.toString(), e);
		}
	}

	@Override
	public PositionInfo getPositionInfo (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		try {
			final MediaStatus mediaStatus = this.chromecast.getMediaStatus(); // TODO cache this?

			String duration = "00:00:00";
			String position = "00:00:00";
			if (mediaStatus != null) {
				if (mediaStatus.media != null) {
					if (mediaStatus.media.duration != null) {
						duration = ModelUtil.toTimeString(mediaStatus.media.duration.longValue());
					}
				}
				position = ModelUtil.toTimeString((long) mediaStatus.currentTime);
			}

			final String trackUri;
			final String trackMetaData;
			if (this.currentMediaInfo != null) {
				trackUri = this.currentMediaInfo.getCurrentURI();
				trackMetaData = this.currentMediaInfo.getCurrentURIMetaData();
			}
			else {
				trackUri = "";
				trackMetaData = "NOT_IMPLEMENTED";
			}

			return new PositionInfo(
					1, duration,
					trackMetaData, trackUri,
					position, position,
					Integer.MAX_VALUE, Integer.MAX_VALUE);
		}
		catch (final IOException e) {
			throw new AVTransportException(ErrorCode.ACTION_FAILED.getCode(), e.toString(), e);
		}
	}

	@Override
	public DeviceCapabilities getDeviceCapabilities (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		return new DeviceCapabilities(new StorageMedium[] { StorageMedium.NETWORK });
	}

	@Override
	public TransportSettings getTransportSettings (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		return new TransportSettings(PlayMode.NORMAL);
	}

	@Override
	public void stop (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		LOG.info("stop({})", instanceId);
		try {
			this.currentMediaInfo = new MediaInfo();

			// FIXME this is not ideal.
			final MediaStatus mediaStatus = this.chromecast.getMediaStatus(); // TODO cache this?
			if (mediaStatus != null) {
				switch (mediaStatus.playerState) {
					case BUFFERING:
					case PLAYING:
						this.chromecast.pause();
					default:
				}
			}
		}
		catch (final IOException e) {
			throw new AVTransportException(ErrorCode.ACTION_FAILED.getCode(), e.toString(), e);
		}
	}

	@Override
	public void play (final UnsignedIntegerFourBytes instanceId, final String speed) throws AVTransportException {
		LOG.info("play({})", instanceId);
		if (this.currentMediaInfo == null) throw new AVTransportException(ErrorCode.ACTION_FAILED, "currentMediaInfo not set.");
		try {
			CastUtils.ensureReady(this.chromecast); // FIXME lazy.

			final MediaStatus mediaStatus = this.chromecast.getMediaStatus(); // TODO cache this?

			final Media media = mediaStatus != null ? mediaStatus.media : null;
			final String mediaUrl = media != null ? media.url : null;
			final PlayerState playerState = mediaStatus != null ? mediaStatus.playerState : null;

			// TODO better way to tell different between load and resume?
			if (Objects.equals(mediaUrl, this.currentMediaInfo.getCurrentURI()) && playerState == PlayerState.PAUSED) {
				this.chromecast.play();
			}
			else {
				// TODO identify MIME type.
				this.chromecast.load("Toad Cast", null, this.currentMediaInfo.getCurrentURI(), "audio/mpeg");
			}
		}
		catch (final IOException e) {
			throw new AVTransportException(ErrorCode.ACTION_FAILED.getCode(), e.toString(), e);
		}
		catch (final Exception e) {
			LOG.error("Failed to play.", e);
			throw e;
		}
	}

	@Override
	public void pause (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		LOG.info("pause({})", instanceId);
		try {
			this.chromecast.pause();
		}
		catch (final IOException e) {
			throw new AVTransportException(ErrorCode.ACTION_FAILED.getCode(), e.toString(), e);
		}
	}

	@Override
	public void record (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		LOG.info("TODO record({})", instanceId);
	}

	@Override
	public void seek (final UnsignedIntegerFourBytes instanceId, final String unit, final String target) throws AVTransportException {
		LOG.info("TODO seek({}, {}, {})", instanceId, unit, target);
	}

	@Override
	public void next (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		LOG.info("TODO next({})", instanceId);
	}

	@Override
	public void previous (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		LOG.info("TODO prev({})", instanceId);
	}

	@Override
	public void setPlayMode (final UnsignedIntegerFourBytes instanceId, final String newPlayMode) throws AVTransportException {
		LOG.info("TODO setPlayMode({}, {})", instanceId, newPlayMode);
	}

	@Override
	public void setRecordQualityMode (final UnsignedIntegerFourBytes instanceId, final String newRecordQualityMode) throws AVTransportException {
		LOG.info("TODO setRecordQualityMode({})", instanceId, newRecordQualityMode);
	}

	@Override
	protected TransportAction[] getCurrentTransportActions (final UnsignedIntegerFourBytes instanceId) throws Exception {
		return new TransportAction[] { TransportAction.Play };
	}

}

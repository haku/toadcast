package com.vaguehope.toadcast;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.fourthline.cling.model.ModelUtil;
import org.fourthline.cling.model.types.ErrorCode;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.support.avtransport.AVTransportErrorCode;
import org.fourthline.cling.support.avtransport.AVTransportException;
import org.fourthline.cling.support.avtransport.AbstractAVTransportService;
import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.fourthline.cling.support.lastchange.LastChange;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.DeviceCapabilities;
import org.fourthline.cling.support.model.MediaInfo;
import org.fourthline.cling.support.model.PlayMode;
import org.fourthline.cling.support.model.PositionInfo;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.StorageMedium;
import org.fourthline.cling.support.model.TransportAction;
import org.fourthline.cling.support.model.TransportInfo;
import org.fourthline.cling.support.model.TransportSettings;
import org.fourthline.cling.support.model.TransportState;
import org.fourthline.cling.support.model.item.Item;
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

	private final AtomicReference<ChromeCast> chromecastHolder;
	private final GoalSeeker goalSeeker;

	private volatile MediaInfo currentMediaInfo = new MediaInfo();
	private volatile Item currentMediaItem;

	public MyAVTransportService (final LastChange lastChange, final AtomicReference<ChromeCast> chromecastHolder, final GoalSeeker goalSeeker) {
		super(lastChange);
		this.chromecastHolder = chromecastHolder;
		this.goalSeeker = goalSeeker;
	}

	private ChromeCast getChromeCast () throws AVTransportException {
		final ChromeCast c = this.chromecastHolder.get();
		if (c == null) throw new AVTransportException(ErrorCode.ACTION_FAILED, "ChromeCast not found.");
		if (!c.isConnected()) throw new AVTransportException(ErrorCode.ACTION_FAILED, "ChromeCast not connected.");
		return c;
	}

	@Override
	public UnsignedIntegerFourBytes[] getCurrentInstanceIds () {
		return new UnsignedIntegerFourBytes[] { new UnsignedIntegerFourBytes(0) };
	}

	@Override
	public void setAVTransportURI (final UnsignedIntegerFourBytes instanceId, final String currentURI, final String currentURIMetaData) throws AVTransportException {
		LOG.info("setAVTransportURI({}, {}, [{}])", instanceId, currentURI,
				currentURIMetaData != null ? currentURI.length() : null);

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

		final Item item;
		if (currentURIMetaData != null) {
			final DIDLContent metadata;
			try {
				metadata = new DIDLParser().parse(currentURIMetaData);
			}
			catch (final Exception e) {
				throw new AVTransportException(ErrorCode.INVALID_ARGS, "Invalid DIDL metadata: " + e);
			}

			if (metadata.getItems().size() == 1) {
				item = metadata.getItems().get(0);
			}
			else {
				throw new AVTransportException(ErrorCode.INVALID_ARGS, "DIDL metadata should contain only one item, found " + metadata.getItems().size() + ".");
			}
		}
		else {
			item = null;
		}

		this.currentMediaInfo = new MediaInfo(uri.toString(), currentURIMetaData);
		this.currentMediaItem = item;
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
		final MediaStatus mediaStatus = this.goalSeeker.getMediaStatus().get(); // TODO check freshness?

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

	@Override
	public PositionInfo getPositionInfo (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		final MediaStatus mediaStatus = this.goalSeeker.getMediaStatus().get(); // TODO check freshness?

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

		String trackUri = this.currentMediaInfo.getCurrentURI();
		if (trackUri == null) trackUri = "";

		String trackMetaData = this.currentMediaInfo.getCurrentURIMetaData();
		if (trackMetaData == null) trackMetaData = "NOT_IMPLEMENTED";

		return new PositionInfo(1, duration, trackMetaData, trackUri, position, position, Integer.MAX_VALUE, Integer.MAX_VALUE);
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

			final MediaStatus mediaStatus = this.goalSeeker.getMediaStatus().get(); // TODO check freshness?
			if (mediaStatus != null) {
				switch (mediaStatus.playerState) {
					case BUFFERING:
					case PLAYING:
						getChromeCast().pause();
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

		// FIXME these 2 lines not atomic / thread safe.
		final MediaInfo mediaInfo = this.currentMediaInfo;
		final Item item = this.currentMediaItem;

		if (mediaInfo == null || mediaInfo.getCurrentURI() == null) throw new AVTransportException(ErrorCode.ACTION_FAILED, "currentMediaInfo not set.");

		final Timestamped<MediaStatus> mediaStatusTs = this.goalSeeker.getMediaStatus(); // TODO check freshness?
		if (mediaStatusTs.isMissingOrOlderThan(10, TimeUnit.SECONDS)) throw new AVTransportException(ErrorCode.ACTION_FAILED, "ChromeCast state not known.");
		final MediaStatus mediaStatus = mediaStatusTs.get();

		try {
			final Media media = mediaStatus != null ? mediaStatus.media : null;
			final String mediaUrl = media != null ? media.url : null;
			final PlayerState playerState = mediaStatus != null ? mediaStatus.playerState : null;

			// TODO better way to tell different between load and resume?
			if (Objects.equals(mediaUrl, mediaInfo.getCurrentURI()) && playerState == PlayerState.PAUSED) {
				getChromeCast().play();
			}
			else {
				String title = "Toad Cast";
				String thumb = null;
				String contentType = "audio/mpeg";
				if (item != null) {
					if (item.getTitle() != null) title = item.getTitle();

					final List<URI> arts = item.getPropertyValues(DIDLObject.Property.UPNP.ALBUM_ART_URI.class);
					if (arts != null && arts.size() > 0) {
						final URI artUri = arts.get(0);
						final URI mediaUri = URI.create(mediaInfo.getCurrentURI());
						thumb = mediaUri.relativize(artUri).toString();
					}

					Res res = null;
					if (item.getResources().size() > 1) {
						for (final Res r : item.getResources()) {
							if ("audio".equalsIgnoreCase(r.getProtocolInfo().getContentFormatMimeType().getType())) {
								res = r;
							}
						}
					}
					if (res == null && item.getResources().size() > 0) {
						res = item.getResources().get(0);
					}
					if (res != null) {
						contentType = res.getProtocolInfo().getContentFormatMimeType().toStringNoParameters();
					}
				}
				getChromeCast().load(title, thumb, mediaInfo.getCurrentURI(), contentType);
			}
		}
		catch (final IOException e) {
			LOG.error("Failed to play: {}", e.toString());
			throw new AVTransportException(ErrorCode.ACTION_FAILED.getCode(), e.toString(), e);
		}
	}

	@Override
	public void pause (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		LOG.info("pause({})", instanceId);
		try {
			getChromeCast().pause();
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

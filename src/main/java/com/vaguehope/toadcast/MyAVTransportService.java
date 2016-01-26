package com.vaguehope.toadcast;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.fourthline.cling.model.ModelUtil;
import org.fourthline.cling.model.types.ErrorCode;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.support.avtransport.AVTransportException;
import org.fourthline.cling.support.avtransport.AbstractAVTransportService;
import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.fourthline.cling.support.lastchange.LastChange;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.DeviceCapabilities;
import org.fourthline.cling.support.model.MediaInfo;
import org.fourthline.cling.support.model.PlayMode;
import org.fourthline.cling.support.model.PositionInfo;
import org.fourthline.cling.support.model.SeekMode;
import org.fourthline.cling.support.model.StorageMedium;
import org.fourthline.cling.support.model.TransportAction;
import org.fourthline.cling.support.model.TransportInfo;
import org.fourthline.cling.support.model.TransportSettings;
import org.fourthline.cling.support.model.TransportState;
import org.fourthline.cling.support.model.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import su.litvak.chromecast.api.v2.Media;
import su.litvak.chromecast.api.v2.MediaStatus;
import su.litvak.chromecast.api.v2.MediaStatus.PlayerState;

public class MyAVTransportService extends AbstractAVTransportService {

	private static final Logger LOG = LoggerFactory.getLogger(MyAVTransportService.class);

	private final GoalSeeker goalSeeker;

	private volatile PlayingState stagedPlayingState;

	public MyAVTransportService (final LastChange lastChange, final GoalSeeker goalSeeker) {
		super(lastChange);
		this.goalSeeker = goalSeeker;
	}

	@Override
	public UnsignedIntegerFourBytes[] getCurrentInstanceIds () {
		return new UnsignedIntegerFourBytes[] { new UnsignedIntegerFourBytes(0) };
	}

	@Override
	public void setAVTransportURI (final UnsignedIntegerFourBytes instanceId, final String currentURI, final String currentURIMetaData) throws AVTransportException {
		LOG.info("setAVTransportURI({}, {}, [{}])", instanceId, currentURI, currentURIMetaData != null ? currentURI.length() : null);

		final URI uri;
		try {
			uri = new URI(currentURI);
		}
		catch (final URISyntaxException ex) {
			throw new AVTransportException(ErrorCode.INVALID_ARGS, "CurrentURI can not be null or malformed");
		}

		if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
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

		this.stagedPlayingState = new PlayingState(new MediaInfo(currentURI, currentURIMetaData), item);
	}

	@Override
	public void setNextAVTransportURI (final UnsignedIntegerFourBytes instanceId, final String nextURI, final String nextURIMetaData) throws AVTransportException {
		LOG.debug("TODO setNextAVTransportURI({}, {}, {})", instanceId, nextURI, nextURIMetaData);
	}

	@Override
	public MediaInfo getMediaInfo (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		final PlayingState staged = this.stagedPlayingState;
		if (staged != null) return staged.getMediaInfo();

		final PlayingState target = this.goalSeeker.getTargetPlayingState();
		if (target != null) return target.getMediaInfo();

		return new MediaInfo();
	}

	@Override
	public TransportInfo getTransportInfo (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		final TransportState transportState;

		final PlayingState tState = this.goalSeeker.getTargetPlayingState();
		final boolean tPaused = this.goalSeeker.isTargetPaused();
		final Timestamped<MediaStatus> mediaStatusHolder = this.goalSeeker.getCurrentMediaStatus(); // TODO check freshness?
		if (tState != null) {
			final String tUrl = StringUtils.trimToNull(tState.getMediaInfo().getCurrentURI());
			final MediaStatus cStatus = mediaStatusHolder.get();
			final Media cMedia = cStatus != null ? cStatus.media : null;
			final String cUrl = cMedia != null ? StringUtils.trimToNull(cMedia.url) : null;
			if (Objects.equals(tUrl, cUrl)) {
				if (cStatus != null) {
					switch (cStatus.playerState) {
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
			}
			else if (tUrl != null && cUrl == null && tPaused) { // Paused for a while and app has timed out.
				transportState = TransportState.PAUSED_PLAYBACK;
			}
			else {
				transportState = TransportState.TRANSITIONING;
			}
		}
		else if (this.goalSeeker.isChromeCastFound()) { // No target but ChomeCast is found.
			transportState = TransportState.NO_MEDIA_PRESENT;
		}
		else { // No target and no ChromeCast.
			transportState = TransportState.CUSTOM;
		}

		return new TransportInfo(transportState);
	}

	@Override
	public PositionInfo getPositionInfo (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		final PlayingState staged = this.stagedPlayingState;
		if (staged != null) {
			return mediaStatusToPositionInfo(staged.getMediaInfo());
		}

		final PlayingState target = this.goalSeeker.getTargetPlayingState();
		if (target != null) {
			final MediaStatus mediaStatus = this.goalSeeker.getCurrentMediaStatus().get();// TODO check freshness?
			return mediaStatusToPositionInfo(mediaStatus, target.getMediaInfo());
		}

		return new PositionInfo();
	}

	private static PositionInfo mediaStatusToPositionInfo (final MediaInfo mediaInfo) {
		return mediaStatusToPositionInfo(null, mediaInfo);
	}

	private static PositionInfo mediaStatusToPositionInfo (final MediaStatus mediaStatus, final MediaInfo mediaInfo) {
		if (mediaInfo == null) throw new IllegalArgumentException("mediaInfo must not be null.");

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

		String trackUri = mediaInfo.getCurrentURI();
		if (trackUri == null) trackUri = "";

		String trackMetaData = mediaInfo.getCurrentURIMetaData();
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
	protected TransportAction[] getCurrentTransportActions (final UnsignedIntegerFourBytes instanceId) throws Exception {
		final MediaStatus cStatus = this.goalSeeker.getCurrentMediaStatus().get(); // TODO check freshness?
		final PlayerState cState = cStatus != null ? cStatus.playerState : null;

		final Set<TransportAction> actions = EnumSet.noneOf(TransportAction.class);

		if (this.stagedPlayingState != null) {
			actions.add(TransportAction.Play);
		}

		if (cState != null) {
			switch (cState) {
				case PLAYING:
					actions.add(TransportAction.Pause);
					actions.add(TransportAction.Stop);
					break;
				case PAUSED:
					actions.add(TransportAction.Play);
					actions.add(TransportAction.Stop);
					break;
				case BUFFERING:
					actions.add(TransportAction.Stop);
				default:
			}
		}

		return actions.toArray(new TransportAction[actions.size()]);
	}

	@Override
	public void stop (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		LOG.info("stop({})", instanceId);
		this.goalSeeker.gotoStopped();
	}

	@Override
	public void play (final UnsignedIntegerFourBytes instanceId, final String speed) throws AVTransportException {
		LOG.info("play({})", instanceId);

		if (this.stagedPlayingState != null) {
			this.goalSeeker.gotoPlaying(this.stagedPlayingState);
			this.stagedPlayingState = null;// Important that this does not get nulled until after goalSeeker has it.
		}
		else {
			this.goalSeeker.gotoResumed();
		}
	}

	@Override
	public void pause (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		LOG.info("pause({})", instanceId);
		this.goalSeeker.gotoPaused();
	}

	@Override
	public void seek (final UnsignedIntegerFourBytes instanceId, final String unit, final String target) throws AVTransportException {
		LOG.info("seek({}, {}, {})", instanceId, unit, target);

		final SeekMode seekMode = SeekMode.valueOrExceptionOf(unit);
		if (!seekMode.equals(SeekMode.REL_TIME)) throw new AVTransportException(ErrorCode.INVALID_ARGS, "Unsupported SeekMode: " + unit);

		final long targetSeconds = ModelUtil.fromTimeString(target);
		this.goalSeeker.seek(targetSeconds);
	}

	@Override
	public void record (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		LOG.info("TODO record({})", instanceId);
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

}

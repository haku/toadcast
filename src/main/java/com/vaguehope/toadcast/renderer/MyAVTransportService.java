package com.vaguehope.toadcast.renderer;

import java.net.URI;

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
import org.seamless.http.HttpFetch;
import org.seamless.util.URIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyAVTransportService extends AbstractAVTransportService {

	private static final Logger LOG = LoggerFactory.getLogger(MyAVTransportService.class);

	private MediaInfo currentMediaInfo = new MediaInfo();
	private final TransportInfo currentTransportInfo = new TransportInfo();
	private final PositionInfo currentPositionInfo = new PositionInfo();

	public MyAVTransportService (final LastChange lastChange) {
		super(lastChange);
	}

	@Override
	public UnsignedIntegerFourBytes[] getCurrentInstanceIds () {
		return new UnsignedIntegerFourBytes[] { new UnsignedIntegerFourBytes(0) };
	}

	@Override
	public void setAVTransportURI (final UnsignedIntegerFourBytes instanceId, final String currentURI, final String currentURIMetaData) throws AVTransportException {
		LOG.info("TODO setAVTransportURI({}, {}, {})", instanceId, currentURI, currentURIMetaData);

		final URI uri;
		try {
			uri = new URI(currentURI);
		}
		catch (Exception ex) {
			throw new AVTransportException(ErrorCode.INVALID_ARGS, "CurrentURI can not be null or malformed");
		}

		if (currentURI.startsWith("http:")) {
			try {
				HttpFetch.validate(URIUtil.toURL(uri));
			}
			catch (Exception ex) {
				throw new AVTransportException(AVTransportErrorCode.RESOURCE_NOT_FOUND, ex.getMessage());
			}
		}
		else if (!currentURI.startsWith("file:")) {
			throw new AVTransportException(ErrorCode.INVALID_ARGS, "Only HTTP and file: resource identifiers are supported");
		}

		this.currentMediaInfo = new MediaInfo(uri.toString(), "");
	}

	@Override
	public void setNextAVTransportURI (final UnsignedIntegerFourBytes instanceId, final String nextURI, final String nextURIMetaData) throws AVTransportException {
		LOG.info("TODO setNextAVTransportURI({}, {}, {})", instanceId, nextURI, nextURIMetaData);
	}

	@Override
	public MediaInfo getMediaInfo (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		return this.currentMediaInfo;
	}

	@Override
	public TransportInfo getTransportInfo (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		return this.currentTransportInfo;
	}

	@Override
	public PositionInfo getPositionInfo (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		return this.currentPositionInfo;
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
		LOG.info("TODO stop({})", instanceId);
	}

	@Override
	public void play (final UnsignedIntegerFourBytes instanceId, final String speed) throws AVTransportException {
		LOG.info("TODO play({})", instanceId);
	}

	@Override
	public void pause (final UnsignedIntegerFourBytes instanceId) throws AVTransportException {
		LOG.info("TODO pause({})", instanceId);
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

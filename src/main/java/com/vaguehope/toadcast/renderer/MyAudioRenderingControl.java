package com.vaguehope.toadcast.renderer;

import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.model.types.UnsignedIntegerTwoBytes;
import org.fourthline.cling.support.lastchange.LastChange;
import org.fourthline.cling.support.model.Channel;
import org.fourthline.cling.support.renderingcontrol.AbstractAudioRenderingControl;
import org.fourthline.cling.support.renderingcontrol.RenderingControlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyAudioRenderingControl extends AbstractAudioRenderingControl {

	private static final Logger LOG = LoggerFactory.getLogger(MyAudioRenderingControl.class);

	private final MyAVTransportService avTransportService;

	public MyAudioRenderingControl (final LastChange lastChange, final MyAVTransportService avTransportService) {
		super(lastChange);
		this.avTransportService = avTransportService;
	}

	@Override
	public boolean getMute (final UnsignedIntegerFourBytes instanceId, final String channelName) throws RenderingControlException {
		return false;
	}

	@Override
	public void setMute (final UnsignedIntegerFourBytes instanceId, final String channelName, final boolean desiredMute) throws RenderingControlException {
		LOG.info("TODO setMute({}, {}, {})", instanceId, channelName, desiredMute);
	}

	@Override
	public UnsignedIntegerTwoBytes getVolume (final UnsignedIntegerFourBytes instanceId, final String channelName) throws RenderingControlException {
		return new UnsignedIntegerTwoBytes(100);
	}

	@Override
	public void setVolume (final UnsignedIntegerFourBytes instanceId, final String channelName, final UnsignedIntegerTwoBytes desiredVolume) throws RenderingControlException {
		LOG.info("TODO setVolume({}, {}, {})", instanceId, channelName, desiredVolume);
	}

	@Override
	protected Channel[] getCurrentChannels () {
		return new Channel[] { Channel.Master };
	}

	@Override
	public UnsignedIntegerFourBytes[] getCurrentInstanceIds () {
		return this.avTransportService.getCurrentInstanceIds();
	}

}

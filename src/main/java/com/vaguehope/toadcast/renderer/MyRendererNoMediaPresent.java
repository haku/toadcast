package com.vaguehope.toadcast.renderer;

import java.net.URI;

import org.fourthline.cling.support.avtransport.impl.state.AbstractState;
import org.fourthline.cling.support.avtransport.impl.state.NoMediaPresent;
import org.fourthline.cling.support.avtransport.lastchange.AVTransportVariable;
import org.fourthline.cling.support.model.AVTransport;
import org.fourthline.cling.support.model.MediaInfo;
import org.fourthline.cling.support.model.PositionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyRendererNoMediaPresent extends NoMediaPresent {

	private static final Logger LOG = LoggerFactory.getLogger(MyRendererNoMediaPresent.class);

	public MyRendererNoMediaPresent (final AVTransport transport) {
		super(transport);
	}

	@Override
	public Class<? extends AbstractState> setTransportURI (final URI uri, final String metaData) {
		LOG.info("TODO set: {} {}", uri, metaData);
		getTransport().setMediaInfo(new MediaInfo(uri.toString(), metaData));

		// If you can, you should find and set the duration of the track here!
		getTransport().setPositionInfo(new PositionInfo(1, metaData, uri.toString()));

		// It's up to you what "last changes" you want to announce to event listeners
		getTransport().getLastChange().setEventedValue(getTransport().getInstanceId(), new AVTransportVariable.AVTransportURI(uri), new AVTransportVariable.CurrentTrackURI(uri));

		return MyRendererStopped.class;
	}
}

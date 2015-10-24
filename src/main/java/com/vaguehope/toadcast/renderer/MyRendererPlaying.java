package com.vaguehope.toadcast.renderer;

import java.net.URI;

import org.fourthline.cling.support.avtransport.impl.state.AbstractState;
import org.fourthline.cling.support.avtransport.impl.state.Playing;
import org.fourthline.cling.support.model.AVTransport;
import org.fourthline.cling.support.model.SeekMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyRendererPlaying extends Playing {

	private static final Logger LOG = LoggerFactory.getLogger(MyRendererPlaying.class);

	public MyRendererPlaying (final AVTransport transport) {
		super(transport);
	}

	@Override
	public void onEntry () {
		super.onEntry();
		LOG.info("TODO start playing: {}", getTransport().getMediaInfo().getCurrentURI());
		// TODO Start playing now.
	}

	@Override
	public Class<? extends AbstractState> setTransportURI (final URI uri, final String metaData) {
		return MyRendererStopped.class;
	}

	@Override
	public Class<? extends AbstractState> stop () {
		LOG.info("TODO stop.");
		// TODO Stop playing.
		return MyRendererStopped.class;
	}

	@Override
	public Class play (final String speed) {
		return MyRendererPlaying.class;
	}

	@Override
	public Class pause () {
		LOG.info("TODO pause.");
		// TODO Pause playing.
		return MyRendererPlaying.class;
	}

	@Override
	public Class next () {
		return MyRendererPlaying.class;
	}

	@Override
	public Class previous () {
		return MyRendererStopped.class;
	}

	@Override
	public Class seek (final SeekMode unit, final String target) {
		LOG.info("TODO seek.");
		// TODO Seek.
		return MyRendererPlaying.class;
	}

}

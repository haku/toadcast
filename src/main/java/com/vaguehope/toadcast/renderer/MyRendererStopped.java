package com.vaguehope.toadcast.renderer;

import java.net.URI;

import org.fourthline.cling.support.avtransport.impl.state.AbstractState;
import org.fourthline.cling.support.avtransport.impl.state.Stopped;
import org.fourthline.cling.support.model.AVTransport;
import org.fourthline.cling.support.model.SeekMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyRendererStopped extends Stopped {

	private static final Logger LOG = LoggerFactory.getLogger(MyRendererStopped.class);

	public MyRendererStopped (final AVTransport transport) {
		super(transport);
	}

	@Override
	public void onEntry () {
		super.onEntry();
		LOG.info("TODO stop.");
		// TODO stop.
	}

	public void onExit () {
		// Optional: Cleanup etc.
	}

	@Override
	public Class<? extends AbstractState> setTransportURI (final URI uri, final String metaData) {
		// This operation can be triggered in any state, you should think
		// about how you'd want your player to react. If we are in Stopped
		// state nothing much will happen, except that you have to set
		// the media and position info, just like in MyRendererNoMediaPresent.
		// However, if this would be the MyRendererPlaying state, would you
		// prefer stopping first?

		LOG.info("TODO set: {} {}", uri, metaData);
//		getTransport().setMediaInfo(new MediaInfo(...));

		return MyRendererStopped.class;
	}

	@Override
	public Class<? extends AbstractState> stop () {
		/// Same here, if you are stopped already and someone calls STOP, well...
		return MyRendererStopped.class;
	}

	@Override
	public Class<? extends AbstractState> play (final String speed) {
		// It's easier to let this classes' onEntry() method do the work
		return MyRendererPlaying.class;
	}

	@Override
	public Class<? extends AbstractState> next () {
		return MyRendererStopped.class;
	}

	@Override
	public Class<? extends AbstractState> previous () {
		return MyRendererStopped.class;
	}

	@Override
	public Class<? extends AbstractState> seek (final SeekMode unit, final String target) {
		// Implement seeking with the stream in stopped state!
		return MyRendererStopped.class;
	}
}

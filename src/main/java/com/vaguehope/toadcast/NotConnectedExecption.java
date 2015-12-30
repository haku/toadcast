package com.vaguehope.toadcast;

import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.ChromeCastException;

public class NotConnectedExecption extends ChromeCastException {

	private static final long serialVersionUID = -6772785980952807985L;

	public NotConnectedExecption (final ChromeCast c) {
		super(String.format("%s (%s) is not connected.", c.getName(), c.getAddress()));
	}

}

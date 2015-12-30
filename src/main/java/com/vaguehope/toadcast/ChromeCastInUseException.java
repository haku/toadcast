package com.vaguehope.toadcast;

import su.litvak.chromecast.api.v2.Application;
import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.ChromeCastException;

public class ChromeCastInUseException extends ChromeCastException {

	private static final long serialVersionUID = 3826633762619541468L;

	public ChromeCastInUseException (final ChromeCast c, final Application app) {
		super(String.format("%s (%s) is in use: appId=%s, name=%s, status=%s.",
				c.getName(), c.getAddress(), app.id, app.name, app.statusText));
	}

}

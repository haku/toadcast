package com.vaguehope.toadcast;

import su.litvak.chromecast.api.v2.ChromeCastException;

public class NoResponseException extends ChromeCastException {

	private static final long serialVersionUID = -6358103267321311608L;

	public NoResponseException (final String message) {
		super(message);
	}

}

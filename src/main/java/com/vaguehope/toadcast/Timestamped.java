package com.vaguehope.toadcast;

import java.util.concurrent.TimeUnit;

public class Timestamped<T> {

	private final long time;
	private final T v;

	public Timestamped (final T v) {
		this.time = System.currentTimeMillis();
		this.v = v;
	}

	public long age (final TimeUnit units) {
		return units.convert(System.currentTimeMillis() - this.time, TimeUnit.MILLISECONDS);
	}

	public boolean isMissingOrOlderThan (final int duration, final TimeUnit unit) {
		if (this.v == null) return false;
		return age(unit) > duration;
	}

	public T get () {
		return this.v;
	}

}

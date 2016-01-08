package com.vaguehope.toadcast;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.ChromeCastSpontaneousEventListener;

public class ChromeCastHolder {

	private final Set<ChromeCastSpontaneousEventListener> eventListeners = new CopyOnWriteArraySet<ChromeCastSpontaneousEventListener>();
	private final AtomicReference<ChromeCast> holder = new AtomicReference<ChromeCast>();

	public ChromeCastHolder () {}

	public void addEventListener (final ChromeCastSpontaneousEventListener eventListener) {
		this.eventListeners.add(eventListener);
	}

	public ChromeCast getAndSet (final ChromeCast chromeCast) {
		return this.holder.getAndSet(chromeCast);
	}

	public boolean compareAndSet (final ChromeCast expect, final ChromeCast update) {
		final boolean ret = this.holder.compareAndSet(expect, update);
		if (ret) {
			if (update != null) {
				for (final ChromeCastSpontaneousEventListener l : this.eventListeners) {
					update.registerListener(l);
				}
			}
		}
		return ret;
	}

	public ChromeCast get () {
		return this.holder.get();
	}

}

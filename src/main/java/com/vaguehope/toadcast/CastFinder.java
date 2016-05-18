package com.vaguehope.toadcast;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Locale;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.ChromeCasts;
import su.litvak.chromecast.api.v2.ChromeCastsListener;

public class CastFinder {

	private static final Logger LOG = LoggerFactory.getLogger(CastFinder.class);

	private final InetAddress bindAddress;
	private final String chromecastName;
	private final ChromeCastHolder holder;
	private final UpnpService upnpService;

	public CastFinder (final InetAddress bindAddress, final String chromecastName, final ChromeCastHolder holder, final UpnpService upnpService) {
		this.bindAddress = bindAddress;
		this.chromecastName = chromecastName;
		this.holder = holder;
		this.upnpService = upnpService;
	}

	public void start () throws IOException {
		startMdnsChromecastDiscovery();
		startUpnpChromecastDiscovery();
	}

	public void rediscover () throws IOException {
		ChromeCasts.startDiscovery();
		this.upnpService.getRegistry().removeAllRemoteDevices();
	}

	private void startMdnsChromecastDiscovery () throws IOException {
		ChromeCasts.registerListener(new ChromeCastsListener() {
			@Override
			public void newChromeCastDiscovered (final ChromeCast chromecast) {
				chromecastFound(chromecast, "mDNS");
			}

			@Override
			public void chromeCastRemoved (final ChromeCast chromecast) {/* Unused. */}
		});
		ChromeCasts.startDiscovery(this.bindAddress);
		LOG.info("Watching for ChromeCast {} ...", this.chromecastName);
	}

	private void startUpnpChromecastDiscovery () {
		this.upnpService.getRegistry().addListener(new DefaultRegistryListener(){
			@Override
			public void remoteDeviceAdded (final Registry registry, final RemoteDevice device) {
				try {
					final DeviceDetails details = device.getDetails();
					final String name = details != null ? details.getFriendlyName() : null;
					final String host = details != null && details.getBaseURL() != null ? details.getBaseURL().getHost() : null;
					final String modelName = details != null && details.getModelDetails() != null ? details.getModelDetails().getModelName() : null;
					LOG.debug("Found UPNP device: name={}, host={}, model={}.", name, host, modelName);

					if (CastHelper.isChromecast(device)) {
						final ChromeCast chromecast = new ChromeCast(host);
						chromecast.setName(name);
						chromecastFound(chromecast, "UPnP");
					}
				}
				catch (final Exception e) {
					LOG.warn("Error handling UPNP device {}: {}", device, e);
				}
			}
		});
	}

	private void chromecastFound (final ChromeCast chromecast, final String discoveryMethod) {
		final String name = chromecast.getName();
		if (name != null && name.toLowerCase(Locale.ENGLISH).contains(this.chromecastName.toLowerCase(Locale.ENGLISH))) {
			if (this.holder.compareAndSet(null, chromecast)) {
				LOG.info("ChromeCast found via {}: {} ({}:{})", discoveryMethod, name, chromecast.getAddress(), chromecast.getPort());
				try {
					ChromeCasts.stopDiscovery();
				}
				catch (final IOException e) {
					LOG.warn("Failed to stop discovery.", e);
				}
			}
			else {
				LOG.info("ChromeCast found via {} (but we already have one): {} ({}:{})", discoveryMethod, name, chromecast.getAddress(), chromecast.getPort());
			}
		}
		else {
			LOG.info("Not the ChromeCast we are looking for via {}: {} ({}:{})", discoveryMethod, name, chromecast.getAddress(), chromecast.getPort());
		}
	}

}

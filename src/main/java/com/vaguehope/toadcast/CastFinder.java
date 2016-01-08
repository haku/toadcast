package com.vaguehope.toadcast;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.ChromeCastSpontaneousEventListener;
import su.litvak.chromecast.api.v2.ChromeCasts;
import su.litvak.chromecast.api.v2.ChromeCastsListener;

public class CastFinder {

	private static final Logger LOG = LoggerFactory.getLogger(CastFinder.class);

	public static void startChromecastDiscovery (final Args args, final AtomicReference<ChromeCast> holder, final GoalSeeker goalSeeker, final UpnpService upnpService) throws IOException {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run () {
				final ChromeCast c = holder.getAndSet(null);
				if (c != null) {
					try {
						CastHelper.tidyChromeCast(c);
						c.disconnect();
					}
					catch (final IOException e) {
						LOG.warn("Failed to disconnect ChromeCast.", e);
					}
				}
			}
		});

		startMdnsChromecastDiscovery(args, holder, goalSeeker);
		startUpnpChromecastDiscovery(args, holder, goalSeeker, upnpService);
	}

	private static void startMdnsChromecastDiscovery (final Args args, final AtomicReference<ChromeCast> holder, final ChromeCastSpontaneousEventListener eventListener) throws IOException {
		ChromeCasts.registerListener(new ChromeCastsListener() {
			@Override
			public void newChromeCastDiscovered (final ChromeCast chromecast) {
				chromecastFound(args, holder, eventListener, chromecast, "mDNS");
			}

			@Override
			public void chromeCastRemoved (final ChromeCast chromecast) {
				LOG.info("mDNS unfound: {} (probably not a problem)", chromecast.getName());
			}
		});
		ChromeCasts.startDiscovery();
		LOG.info("Watching for ChromeCast {} ...", args.getChromecast());
	}

	private static void startUpnpChromecastDiscovery (final Args args, final AtomicReference<ChromeCast> holder, final ChromeCastSpontaneousEventListener eventListener, final UpnpService upnpService) {
		upnpService.getRegistry().addListener(new DefaultRegistryListener(){
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
						chromecastFound(args, holder, eventListener, chromecast, "UPnP");
					}
				}
				catch (final Exception e) {
					LOG.warn("Error handling UPNP device {}: {}", device, e);
				}
			}
		});
	}

	private static void chromecastFound (final Args args, final AtomicReference<ChromeCast> holder, final ChromeCastSpontaneousEventListener eventListener, final ChromeCast chromecast, final String discoveryMethod) {
		final String name = chromecast.getName();
		if (name != null && name.toLowerCase(Locale.ENGLISH).contains(args.getChromecast().toLowerCase(Locale.ENGLISH))) {
			if (holder.compareAndSet(null, chromecast)) {
				chromecast.registerListener(eventListener);
				LOG.info("ChromeCast found via {}: {} ({}:{})", discoveryMethod, name, chromecast.getAddress(), chromecast.getPort());

				try {
					ChromeCasts.stopDiscovery();
				}
				catch (final IOException e) {
					LOG.warn("Failed to stop discovery.", e);
				}
			}
			else {
				LOG.info("ChromeCast found via {}, but we already have one: {} ({}:{})", discoveryMethod, name, chromecast.getAddress(), chromecast.getPort());
			}
		}
		else {
			LOG.info("Not the ChromeCast we are looking for via {}: {} ({}:{})", discoveryMethod, name, chromecast.getAddress(), chromecast.getPort());
		}
	}

}

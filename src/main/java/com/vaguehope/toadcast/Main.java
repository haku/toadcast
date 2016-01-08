package com.vaguehope.toadcast;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.fourthline.cling.model.Command;
import org.fourthline.cling.model.DefaultServiceManager;
import org.fourthline.cling.model.ValidationException;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.cling.model.meta.DeviceIdentity;
import org.fourthline.cling.model.meta.Icon;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.LocalService;
import org.fourthline.cling.model.meta.ManufacturerDetails;
import org.fourthline.cling.model.meta.ModelDetails;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.resource.IconResource;
import org.fourthline.cling.model.resource.Resource;
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;
import org.fourthline.cling.protocol.ProtocolFactory;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.support.avtransport.lastchange.AVTransportLastChangeParser;
import org.fourthline.cling.support.connectionmanager.ConnectionManagerService;
import org.fourthline.cling.support.lastchange.LastChange;
import org.fourthline.cling.support.lastchange.LastChangeAwareServiceManager;
import org.fourthline.cling.support.renderingcontrol.lastchange.RenderingControlLastChangeParser;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.akuma.Daemon;

import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.ChromeCastSpontaneousEventListener;
import su.litvak.chromecast.api.v2.ChromeCasts;
import su.litvak.chromecast.api.v2.ChromeCastsListener;

public class Main {

	private static final Logger LOG = LoggerFactory.getLogger(Main.class);

	private Main () {
		throw new AssertionError();
	}

	public static void main (final String[] rawArgs) throws Exception {// NOSONAR
		LogHelper.bridgeJul();

		final PrintStream err = System.err;
		final Args args = new Args();
		final CmdLineParser parser = new CmdLineParser(args);
		try {
			parser.parseArgument(rawArgs);
			daemonise(args);
			start(args);
			new CountDownLatch(1).await();
			System.exit(0);
		}
		catch (final CmdLineException e) {
			err.println(e.getMessage());
			help(parser, err);
			return;
		}
		catch (final Exception e) {
			err.println("An unhandled error occured.");
			e.printStackTrace(err);
			System.exit(1);
		}
	}

	private static void help (final CmdLineParser parser, final PrintStream ps) {
		ps.print("Usage: ");
		ps.print(C.APPNAME);
		parser.printSingleLineUsage(ps);
		ps.println();
		parser.printUsage(ps);
		ps.println();
	}

	private static void daemonise (final Args args) throws Exception {
		final Daemon d = new Daemon.WithoutChdir();
		if (d.isDaemonized()) {
			d.init(null);// No PID file for now.
		}
		else if (args.isDaemonise()) {
			d.daemonize();
			LOG.info("Daemon started.");
			System.exit(0);
		}
	}

	private static void start (final Args args) throws Exception {// NOSONAR
		final ExecutorService caEs = Executors.newCachedThreadPool();
		final ScheduledExecutorService schEs = Executors.newScheduledThreadPool(1);

		final AtomicReference<ChromeCast> holder = new AtomicReference<ChromeCast>();
		final GoalSeeker goalSeeker = new GoalSeeker(holder);
		caEs.execute(goalSeeker);

		startMdnsChromecastDiscovery(args, holder, goalSeeker);
		final UpnpService upnpService = startUpnpService(args, goalSeeker, schEs);
		startUpnpChromecastDiscovery(args, holder, goalSeeker, upnpService);
	}

	private static void startMdnsChromecastDiscovery (final Args args, final AtomicReference<ChromeCast> holder, final ChromeCastSpontaneousEventListener eventListener) throws Exception {// NOSONAR
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

	private static UpnpService startUpnpService (final Args args, final GoalSeeker goalSeeker, final ScheduledExecutorService schEs) throws IOException, UnknownHostException, ValidationException {
		final String hostName = InetAddress.getLocalHost().getHostName();
		LOG.info("hostName: {}", hostName);

		final String friendlyName = args.getDisplayName(String.format(
				"%s \"%s\" (%s)", C.METADATA_MODEL_NAME,
				args.getChromecast(), hostName));

		final UDN usi = UDN.uniqueSystemIdentifier("ToadCast-ChromeCastRenderer-" + args.getChromecast());
		LOG.info("uniqueSystemIdentifier: {}", usi);

		final UpnpService upnpService = makeUpnpServer();
		upnpService.getRegistry().addDevice(makeMediaRendererDevice(friendlyName, usi, goalSeeker, schEs));

		schEs.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run () {
				upnpService.getControlPoint().search();
			}
		}, 0, C.UPNP_SEARCH_INTERVAL_SECONDS, TimeUnit.SECONDS);

		return upnpService;
	}

	private static UpnpService makeUpnpServer () throws IOException {
		final Map<String, Resource<?>> pathToRes = new HashMap<>();

		final Icon icon = createDeviceIcon();
		final IconResource iconResource = new IconResource(icon.getUri(), icon);
		pathToRes.put("/icon.png", iconResource);

		final UpnpServiceImpl upnpService = new UpnpServiceImpl() {
			@Override
			protected Registry createRegistry (final ProtocolFactory protocolFactory) {
				return new RegistryImplWithOverrides(this, pathToRes);
			}
		};

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run () {
				upnpService.shutdown();
			}
		});

		return upnpService;
	}

	private static Icon createDeviceIcon () throws IOException {
		final InputStream res = Main.class.getResourceAsStream("/icon.png");
		if (res == null) throw new IllegalStateException("Icon not found.");
		try {
			final Icon icon = new Icon("image/png", 48, 48, 8, "icon.png", res);
			icon.validate();
			return icon;
		}
		finally {
			res.close();
		}
	}

	private static LocalDevice makeMediaRendererDevice (final String friendlyName, final UDN usi, final GoalSeeker goalSeeker, final ScheduledExecutorService schEs) throws IOException, ValidationException {
		final DeviceType type = new UDADeviceType("MediaRenderer", 1);
		final DeviceDetails details = new DeviceDetails(friendlyName, new ManufacturerDetails(C.METADATA_MANUFACTURER), new ModelDetails(C.METADATA_MODEL_NAME, C.METADATA_MODEL_DESCRIPTION, C.METADATA_MODEL_NUMBER));
		final Icon icon = createDeviceIcon();

		// http://4thline.org/projects/cling/support/manual/cling-support-manual.html

		final AnnotationLocalServiceBinder binder = new AnnotationLocalServiceBinder();

		final LocalService<ConnectionManagerService> connManSrv = binder.read(ConnectionManagerService.class);
		connManSrv.setManager(new DefaultServiceManager<ConnectionManagerService>(connManSrv, ConnectionManagerService.class));

		final LocalService<MyAVTransportService> avtSrv = binder.read(MyAVTransportService.class);
		final LastChange avTransportLastChange = new LastChange(new AVTransportLastChangeParser());
		final MyAVTransportService avTransportService = new MyAVTransportService(avTransportLastChange, goalSeeker);
		avtSrv.setManager(new LastChangeAwareServiceManager<MyAVTransportService>(avtSrv, new AVTransportLastChangeParser()) {
			@Override
			protected MyAVTransportService createServiceInstance () throws Exception {
				return avTransportService;
			}

			@Override
			public void execute (final Command<MyAVTransportService> cmd) throws Exception {
				try {
					super.execute(cmd);
				}
				catch (final Exception e) {
					LOG.warn("Action failed: " + cmd, e);
					throw e;
				}
			}
		});

		final LocalService<MyAudioRenderingControl> rendCtlSrv = binder.read(MyAudioRenderingControl.class);
		final LastChange renderingControlLastChange = new LastChange(new RenderingControlLastChangeParser());
		final MyAudioRenderingControl audioRenderingControl = new MyAudioRenderingControl(renderingControlLastChange, avTransportService);
		rendCtlSrv.setManager(new LastChangeAwareServiceManager<MyAudioRenderingControl>(rendCtlSrv, new RenderingControlLastChangeParser()) {
			@Override
			protected MyAudioRenderingControl createServiceInstance () throws Exception {
				return audioRenderingControl;
			}

			@Override
			public void execute (final Command<MyAudioRenderingControl> cmd) throws Exception {
				try {
					super.execute(cmd);
				}
				catch (final Exception e) {
					LOG.warn("Action failed: " + cmd, e);
					throw e;
				}
			}
		});

		schEs.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run () {
				((LastChangeAwareServiceManager) avtSrv.getManager()).fireLastChange();
				((LastChangeAwareServiceManager) rendCtlSrv.getManager()).fireLastChange();
			}
		}, 5, 5, TimeUnit.SECONDS);

		final LocalDevice device = new LocalDevice(new DeviceIdentity(usi, C.MIN_ADVERTISEMENT_AGE_SECONDS), type, details, icon, new LocalService[] { avtSrv, rendCtlSrv, connManSrv });
		return device;
	}

}

package com.vaguehope.toadcast;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;
import org.fourthline.cling.support.avtransport.lastchange.AVTransportLastChangeParser;
import org.fourthline.cling.support.connectionmanager.ConnectionManagerService;
import org.fourthline.cling.support.lastchange.LastChange;
import org.fourthline.cling.support.lastchange.LastChangeAwareServiceManager;
import org.fourthline.cling.support.renderingcontrol.lastchange.RenderingControlLastChangeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpnpRenderer {

	private static final Logger LOG = LoggerFactory.getLogger(UpnpRenderer.class);

	public static LocalDevice makeMediaRendererDevice (final String friendlyName, final UDN usi, final GoalSeeker goalSeeker, final ScheduledExecutorService schEs) throws IOException, ValidationException {
		final DeviceType type = new UDADeviceType("MediaRenderer", 1);
		final DeviceDetails details = new DeviceDetails(friendlyName, new ManufacturerDetails(C.METADATA_MANUFACTURER), new ModelDetails(C.METADATA_MODEL_NAME, C.METADATA_MODEL_DESCRIPTION, C.METADATA_MODEL_NUMBER));
		final Icon icon = Upnp.createDeviceIcon();

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
				((LastChangeAwareServiceManager<?>) avtSrv.getManager()).fireLastChange();
				((LastChangeAwareServiceManager<?>) rendCtlSrv.getManager()).fireLastChange();
			}
		}, 5, 5, TimeUnit.SECONDS);

		final LocalDevice device = new LocalDevice(new DeviceIdentity(usi, C.MIN_ADVERTISEMENT_AGE_SECONDS), type, details, icon, new LocalService[] { avtSrv, rendCtlSrv, connManSrv });
		return device;
	}

}

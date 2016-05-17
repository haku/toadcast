package com.vaguehope.toadcast;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.model.meta.Icon;
import org.fourthline.cling.model.resource.IconResource;
import org.fourthline.cling.model.resource.Resource;
import org.fourthline.cling.protocol.ProtocolFactory;
import org.fourthline.cling.registry.Registry;

public class Upnp {

	public static UpnpService makeUpnpServer () throws IOException {
		final Map<String, Resource<?>> pathToRes = new HashMap<>();

		final Icon icon = createDeviceIcon();
		final IconResource iconResource = new IconResource(icon.getUri(), icon);
		pathToRes.put("/icon.png", iconResource);

		final UpnpServiceImpl upnpService = new UpnpServiceImpl() {
			@Override
			protected Registry createRegistry (final ProtocolFactory unused) {
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

	public static Icon createDeviceIcon () throws IOException {
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

}

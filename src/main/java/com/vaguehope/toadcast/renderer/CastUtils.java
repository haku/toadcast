package com.vaguehope.toadcast.renderer;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import su.litvak.chromecast.api.v2.Application;
import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.Status;

public class CastUtils {

	private final static String CHROME_CAST_DEFAULT_APP_ID = "CC1AD845";
	private static final Logger LOG = LoggerFactory.getLogger(CastUtils.class);

	public static void ensureReady (final ChromeCast chromecast) throws IOException {
		final Status status = chromecast.getStatus();
		final Application runningApp = status != null ? status.getRunningApp() : null;
		final String runningAppId = runningApp != null ? runningApp.id : null;

		if (!CHROME_CAST_DEFAULT_APP_ID.equals(runningAppId)) {
			if (runningAppId != null) {
				LOG.info("Running app not default, stopping: {}", runningApp);
				chromecast.stopApp();
			}
			LOG.info("Launching default app...");
			chromecast.launchApp(CHROME_CAST_DEFAULT_APP_ID);
		}
	}

}

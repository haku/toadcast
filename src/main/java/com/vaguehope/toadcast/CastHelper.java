package com.vaguehope.toadcast;

import java.io.IOException;

import org.fourthline.cling.model.meta.RemoteDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import su.litvak.chromecast.api.v2.Application;
import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.MediaStatus;
import su.litvak.chromecast.api.v2.Status;

public class CastHelper {

	private static final Logger LOG = LoggerFactory.getLogger(CastHelper.class);

	public static boolean isChromecast (final RemoteDevice device) {
		if (device.getDetails() == null) return false;
		if (device.getDetails().getModelDetails() == null) return false;
		return C.CHROMECAST_MODEL_NAMES.contains(device.getDetails().getModelDetails().getModelName());
	}

	/**
	 * May not return null.
	 */
	public static Status readStatus (final ChromeCast c) throws IOException {
		if (!c.isConnected()) throw new NotConnectedExecption(c);

		// TODO retry loop.
		final Status s = c.getStatus();

		if (s == null) throw new NoResponseException(String.format("(%s).getStatus() did not return a response.", c.getAddress()));
		return s;
	}

	public static boolean isRunningDefaultApp (final Status status) {
		return C.CHROME_CAST_DEFAULT_APP_ID.equals(readRunningAppId(status));
	}

	private static String readRunningAppId (final Status status) {
		final Application runningApp = status != null ? status.getRunningApp() : null;
		final String runningAppId = runningApp != null ? runningApp.id : null;
		return runningAppId;
	}

	public static void readyChromeCast (final ChromeCast c, final Status status) throws IOException {
		final String runningAppId = readRunningAppId(status);
		if (!C.CHROME_CAST_DEFAULT_APP_ID.equals(runningAppId)) {
			if (runningAppId != null) {
				final Application app = status.getRunningApp();
				if (!C.CAN_INTERUPT_APP_NAMES.contains(app.name)) throw new ChromeCastInUseException(c, app);
				LOG.info("Interupting running app: appId={}, name={}, status={}.",
						app.id, app.name, app.statusText);
				c.stopApp();
			}
			LOG.info("Launching default app...");
			c.launchApp(C.CHROME_CAST_DEFAULT_APP_ID);
		}
	}

	/**
	 * May return null, e.g.:
	 *
	 * <pre>
	 * --> {"type":"GET_STATUS","requestId":186}
	 * <-- {"type":"MEDIA_STATUS","status":[],"requestId":186}
	 * </pre>
	 */
	public static MediaStatus readMediaStatus (final ChromeCast c) throws IOException {
		if (!c.isConnected()) throw new NotConnectedExecption(c);

		// TODO retry loop.
		return c.getMediaStatus();
	}

	public static void tidyChromeCast (final ChromeCast c) throws IOException {
		if (isRunningDefaultApp(readStatus(c))) c.stopApp();
	}

}

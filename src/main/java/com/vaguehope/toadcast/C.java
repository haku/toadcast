package com.vaguehope.toadcast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public interface C {

	String APPNAME = "ToadCast";

	String METADATA_MANUFACTURER = "VagueHope";

	String METADATA_MODEL_NAME = "ToadCast";
	String METADATA_MODEL_DESCRIPTION = "ToadCast DLNA --> ChromeCast Bridge Service";
	String METADATA_MODEL_NUMBER = "v1";

	Set<String> CHROMECAST_MODEL_NAMES = new HashSet<>(Arrays.asList(
			"Eureka Dongle", "Chromecast Audio"));

	Set<String> CAN_INTERUPT_APP_NAMES = new HashSet<>(Arrays.asList(
			"Backdrop"));

	String CHROME_CAST_DEFAULT_APP_ID = "CC1AD845";

	/**
	 * How often to search for new UPNP devices that have not announced correctly.
	 * Like Chromecasts.
	 */
	int UPNP_SEARCH_INTERVAL_SECONDS = 30;

	/**
	 * Shorter version of org.teleal.cling.model.Constants.MIN_ADVERTISEMENT_AGE_SECONDS.
	 * Remove when Cling 2.0 has a stable release.
	 * http://4thline.org/projects/mailinglists.html#nabble-td2183974
	 * http://4thline.org/projects/mailinglists.html#nabble-td2183974
	 * https://github.com/4thline/cling/issues/41
	 */
	int MIN_ADVERTISEMENT_AGE_SECONDS = 300;

}

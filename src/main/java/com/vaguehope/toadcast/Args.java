package com.vaguehope.toadcast;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.Option;

public class Args {

	@Option(name = "-d", aliases = { "--daemon" }, usage = "detach form terminal and run in bakground.") private boolean daemonise;
	@Option(name = "-c", aliases = { "--chromecast" }, required = true, usage = "Part of the ChromeCast's name.") private String chromecast;
	@Option(name = "-n", aliases = { "--displayname" }, usage = "DLNA display name.") private String displayName;

	public boolean isDaemonise () {
		return this.daemonise;
	}

	public String getChromecast () {
		return this.chromecast;
	}

	public String getDisplayName (final String defVal) {
		if (StringUtils.isBlank(this.displayName)) return defVal;
		return this.displayName;
	}

}

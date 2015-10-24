package com.vaguehope.toadcast;

import java.util.Collections;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

public class Args {

	@Option(name = "-d", aliases = { "--daemon" }, usage = "detach form terminal and run in bakground.") private boolean daemonise;
	@Option(name = "-c", aliases = { "--chromecast" }, required = true, usage = "Hostname or IP address of chromecast.") private String chromecast;
	@Argument(multiValued = true, metaVar = "PATH") private List<String> paths;

	public boolean isDaemonise () {
		return this.daemonise;
	}

	public String getChromecast () {
		return this.chromecast;
	}

	public List<String> getPaths () {
		if (this.paths == null) return Collections.emptyList();
		return this.paths;
	}
}

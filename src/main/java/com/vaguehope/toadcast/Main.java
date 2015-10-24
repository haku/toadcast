package com.vaguehope.toadcast;

import java.io.PrintStream;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import su.litvak.chromecast.api.v2.Application;
import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.MediaStatus;
import su.litvak.chromecast.api.v2.MediaStatus.PlayerState;

import com.sun.akuma.Daemon;

public class Main {

	private final static String CHROME_CAST_DEFAULT_APP_ID = "CC1AD845";
	private static final Logger LOG = LoggerFactory.getLogger(Main.class);

	private Main () {
		throw new AssertionError();
	}

	public static void main (final String[] rawArgs) throws Exception { // NOSONAR
		final PrintStream err = System.err;
		final Args args = new Args();
		final CmdLineParser parser = new CmdLineParser(args);
		try {
			parser.parseArgument(rawArgs);
			daemonise(args);
			run(args);
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

	private static void daemonise (final Args args) throws Exception {
		final Daemon d = new Daemon.WithoutChdir();
		if (d.isDaemonized()) {
			d.init(null); // No PID file for now.
		}
		else if (args.isDaemonise()) {
			d.daemonize();
			LOG.info("Daemon started.");
			System.exit(0);
		}
	}

	private static void run (final Args args) throws Exception { // NOSONAR
		final ChromeCast chromecast = new ChromeCast(args.getChromecast());
		chromecast.connect();
		final Application runningApp = chromecast.getRunningApp();
		if (runningApp != null && !runningApp.id.equals(CHROME_CAST_DEFAULT_APP_ID)) {
			chromecast.stopApp();
		}
		chromecast.launchApp(CHROME_CAST_DEFAULT_APP_ID);
		for (final String path : args.getPaths()) {
			LOG.info("Playing: {}", path);
			chromecast.load("Toad Cast", null, path, "audio/mpeg");
			MediaStatus mediaStatus = chromecast.getMediaStatus();
			while (mediaStatus != null && (mediaStatus.playerState == PlayerState.BUFFERING
					|| mediaStatus.playerState == PlayerState.PLAYING
					|| mediaStatus.playerState == PlayerState.PAUSED)) {
				LOG.info("Playing: playerState={} currentTime={}", mediaStatus.playerState, mediaStatus.currentTime);
				Thread.sleep(5000);
				mediaStatus = chromecast.getMediaStatus();
			}
			LOG.info("mediaStatus={}", mediaStatus);
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

}

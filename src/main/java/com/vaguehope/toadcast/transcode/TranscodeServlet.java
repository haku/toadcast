package com.vaguehope.toadcast.transcode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TranscodeServlet extends HttpServlet {

	static final String CONTENT_TYPE_MP3 = "audio/mp3";

	private static final Logger LOG = LoggerFactory.getLogger(TranscodeServlet.class);
	private static final long serialVersionUID = -8907692259463610363L;

	@Override
	protected void doGet (final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String rawUrl = req.getParameter("url");
		if (StringUtils.isBlank(rawUrl)) {
			resp.sendError(HttpStatus.BAD_REQUEST_400, "Missing param: url.");
			return;
		}

		final URL url;
		try {
			url = new URL(rawUrl);
		}
		catch (final MalformedURLException e) {
			resp.sendError(HttpStatus.BAD_REQUEST_400, "Malformed URL.");
			return;
		}

		final ProcessBuilder pb = new ProcessBuilder(
				"ffmpeg",
				"-hide_banner",
				"-nostats",
				"-seekable", "1",
				"-fflags", "+genpts",
				"-threads", "0",
				"-i", url.toString(),
				"-vn",
				"-b:a", "320k",
				"-f", "mp3",
				"-");
		LOG.info("cmd: {}", pb.command());

		final Process p = pb.start();
		try {
			new ErrorToLog(p).start();

			resp.setContentType(CONTENT_TYPE_MP3);
			IOUtils.copyLarge(p.getInputStream(), resp.getOutputStream());
			waitFor(p, 5, TimeUnit.SECONDS);
		}
		finally {
			try {
				final int result = p.exitValue();
				LOG.info("ffmpeg result: {}", result);
			}
			catch (final IllegalThreadStateException e) {
				p.destroy();
				LOG.info("ffmpeg force terminated.");
			}
		}
	}

	private static void waitFor (final Process p, final int timeout, final TimeUnit unit) {
		final long startNanos = System.nanoTime();
		try {
			p.exitValue();
			return;
		}
		catch (final IllegalThreadStateException e) {
			if (TimeUnit.NANOSECONDS.convert(timeout, unit) > System.nanoTime() - startNanos) {
				return; // Timed out.
			}
			try {
				Thread.sleep(1000L);
			}
			catch (InterruptedException e1) {}
		}
	}

	private static class ErrorToLog extends Thread {
		private final Process p;

		public ErrorToLog (final Process p) {
			this.p = p;
		}

		@Override
		public void run () {
			try {
				copyErrToLog();
			}
			catch (final Exception e) {
				if (e instanceof IOException && "Stream closed".equals(e.getMessage())) return;
				LOG.error("Err reading thread died.", e);
			}
		}

		private void copyErrToLog () throws IOException {
			final BufferedReader reader = new BufferedReader(new InputStreamReader(this.p.getErrorStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				LOG.info(line);
			}
		}
	}

}

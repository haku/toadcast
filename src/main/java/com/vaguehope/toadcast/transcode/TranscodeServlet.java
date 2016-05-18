package com.vaguehope.toadcast.transcode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.toadcast.util.ExceptionHelper;

public class TranscodeServlet extends HttpServlet {

	private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;
	private static final int ERR_HISTORY_LINES = 100;
	private static final int MAX_IN_PROGRESS_TRANSCODES = 3;

	static final String CONTENT_TYPE_MP3 = "audio/mp3";

	private static final Logger LOG = LoggerFactory.getLogger(TranscodeServlet.class);
	private static final long serialVersionUID = -8907692259463610363L;

	private final AtomicInteger inProgress = new AtomicInteger(0);
	private final ExecutorService es;

	public TranscodeServlet () {
		this.es = Executors.newCachedThreadPool();
	}

	@Override
	protected void doGet (final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final String rawUrl = req.getParameter("url");
		if (StringUtils.isBlank(rawUrl)) {
			resp.sendError(HttpStatus.BAD_REQUEST_400, "Missing param: url.");
			return;
		}

		try {
			new URL(rawUrl).getHost(); // To trigger validation.
		}
		catch (final MalformedURLException e) {
			resp.sendError(HttpStatus.BAD_REQUEST_400, "Malformed URL.");
			return;
		}

		while (true) {
			final int n = this.inProgress.get();
			if (n > MAX_IN_PROGRESS_TRANSCODES) {
				resp.sendError(HttpStatus.SERVICE_UNAVAILABLE_503, "Overloaded.");
				LOG.warn("Rejected transcode as overloaded: {}", rawUrl);
				return;
			}
			if (this.inProgress.compareAndSet(n, n + 1)) break;
		}
		try {
			transcode(resp, rawUrl);
		}
		catch (final Exception e) {
			LOG.warn("Transcode failed.", e);
			throw e;
		}
		finally {
			this.inProgress.decrementAndGet();
		}
	}

	private void transcode (final HttpServletResponse resp, final String url) throws IOException {
		Future<List<String>> errFuture = null;
		boolean procShouldBeRunning = true;
		final Process p = makeProcess(url).start();
		try {
			try {
				errFuture = this.es.submit(new ErrReader(p));
				resp.setContentType(CONTENT_TYPE_MP3);
				final long bytesSend = IOUtils.copyLarge(p.getInputStream(), resp.getOutputStream());
				LOG.info("Transcode complete, served {} bytes.", bytesSend);
			}
			catch (final IOException e) {
				if (ExceptionHelper.causedBy(e, IOException.class, "Connection reset by peer")) {
					procShouldBeRunning = false;
				}
				else {
					throw e;
				}
			}
		}
		finally {
			p.destroy();
			try {
				final int result = waitFor(p, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
				if (procShouldBeRunning && result != 0 && errFuture != null) {
					LOG.info("ffmpeg result: {}", result);
					logErr(errFuture);
				}
			}
			catch (final IllegalThreadStateException e) {
				LOG.warn("ffmpeg did not stop when requested.");
				if (errFuture != null) logErr(errFuture);
			}
		}
	}

	private static ProcessBuilder makeProcess (final String url) {
		final ProcessBuilder pb = new ProcessBuilder(
				"ffmpeg",
				"-hide_banner",
				"-nostats",
				"-seekable", "1",
				"-fflags", "+genpts",
				"-threads", "0",
				"-i", url,
				"-vn",
				"-b:a", "320k",
				"-f", "mp3",
				"-");
		LOG.info("cmd: {}", pb.command());
		return pb;
	}

	private static int waitFor (final Process p, final int timeout, final TimeUnit unit) {
		final long startNanos = System.nanoTime();
		while (true) {
			try {
				return p.exitValue();
			}
			catch (final IllegalThreadStateException e) {
				if (TimeUnit.NANOSECONDS.convert(timeout, unit) < System.nanoTime() - startNanos) {
					throw e; // Timed out.
				}
				try {
					Thread.sleep(1000L);
				}
				catch (final InterruptedException e1) {/* Ignore. */}
			}
		}
	}


	private static void logErr (final Future<List<String>> errFuture) {
		try {
			for (final String line : errFuture.get()) {
				LOG.info("ffmpeg: {}", line);
			}
		}
		catch (InterruptedException | ExecutionException e) {
			LOG.error("Err reader failed.", e);
		}
	}

	private static class ErrReader implements Callable<List<String>> {

		private final Process p;

		public ErrReader (final Process p) {
			this.p = p;
		}

		@Override
		public List<String> call () throws Exception {
			final LinkedList<String> err = new LinkedList<String>();
			try {
				readErr(err);
			}
			catch (final Exception e) {
				if (!ignoreException(e)) LOG.error("Err reader died.", e);
			}
			return err;
		}

		private static boolean ignoreException (final Exception e) {
			return e instanceof IOException && "Stream closed".equals(e.getMessage());
		}

		private void readErr (final LinkedList<String> err) throws IOException {
			final BufferedReader reader = new BufferedReader(new InputStreamReader(this.p.getErrorStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				err.add(line);
				if (err.size() > ERR_HISTORY_LINES) err.poll();
			}
		}
	}

}

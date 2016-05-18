package com.vaguehope.toadcast.transcode;

import java.net.BindException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.toadcast.PlayingState;
import com.vaguehope.toadcast.util.NetHelper;

public class Transcoder {

	private static final int HTTP_PORT = 8182;
	private static final int MAX_IDLE_TIME_MILLIS = 30000;
	private static final Logger LOG = LoggerFactory.getLogger(Transcoder.class);

	private final String externalHttp;

	public Transcoder (final String iface) throws Exception {
		final InetAddress address;
		if (iface != null) {
			address = InetAddress.getByName(iface);
			LOG.info("using address: {}", address);
		}
		else {
			final List<InetAddress> addresses = NetHelper.getIpAddresses();
			address = addresses.iterator().next();
			LOG.info("addresses: {} using address: {}", addresses, address);
		}

		final Server server = startServer(address.getHostAddress());
		this.externalHttp = "http://" + address.getHostAddress() + ":" + findConnectorPort(server);
		LOG.info("externalHttp: {}", this.externalHttp);
	}

	public boolean transcodeRequired (final String contentType) {
		return StringUtils.startsWithIgnoreCase(contentType, "video");
	}

	public PlayingState transcode (final PlayingState tState) {
		return tState.withAltMedia(String.format("%s/transcode?url=%s", this.externalHttp, tState.getMediaUri()), TranscodeServlet.CONTENT_TYPE_MP3);
	}

	private static Server startServer (final String iface) throws Exception {
		final HandlerList handler = makeHandler();

		int port = HTTP_PORT;
		while (true) {
			final Server server = new Server();
			server.setHandler(handler);
			server.addConnector(createHttpConnector(iface, port));
			try {
				server.start();
				return server;
			}
			catch (final BindException e) {
				if ("Address already in use".equals(e.getMessage())) {
					port += 1;
				}
				else {
					throw e;
				}
			}
		}
	}

	private static HandlerList makeHandler () {
		final ServletContextHandler servletHandler = new ServletContextHandler();
		servletHandler.setContextPath("/");
		servletHandler.addServlet(new ServletHolder(new TranscodeServlet()), "/transcode");

		final HandlerList handler = new HandlerList();
		handler.setHandlers(new Handler[] { servletHandler });
		return handler;
	}

	private static SelectChannelConnector createHttpConnector (final String iface, final int port) {
		final SelectChannelConnector connector = new SelectChannelConnector();
		connector.setStatsOn(false);
		connector.setHost(iface);
		connector.setPort(port);
		connector.setMaxIdleTime(MAX_IDLE_TIME_MILLIS);
		return connector;
	}

	private static int findConnectorPort (final Server server) {
		final Connector[] connectors = server.getConnectors();
		if (connectors.length != 1) throw new IllegalArgumentException("Expected just one connector: " + Arrays.toString(connectors));
		return connectors[0].getPort();
	}

}

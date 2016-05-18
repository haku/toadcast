package com.vaguehope.toadcast;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.fourthline.cling.model.ModelUtil;
import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.MediaInfo;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.item.Item;

import su.litvak.chromecast.api.v2.Media;
import su.litvak.chromecast.api.v2.Media.StreamType;

public class PlayingState {

	private static final String DEFAULT_TITLE = "Toad Cast";
	private static final String DEFAULT_CONTENT_TYPE = "audio/mpeg";

	private final MediaInfo mediaInfo;
	private final String mediaUri;
	private final String title;
	private final String artUri;
	private final String contentType;
	private final long durationSeconds;

	public PlayingState (final MediaInfo mediaInfo, final Item item) {
		if (mediaInfo == null) throw new IllegalArgumentException("mediaInfo must not be null.");
		if (mediaInfo.getCurrentURI() == null) throw new IllegalArgumentException("mediaInfo.currentUri must not be null.");
		this.mediaInfo = mediaInfo;
		this.mediaUri = mediaInfo.getCurrentURI();

		if (item != null) {
			this.title = item.getTitle() != null ? item.getTitle() : DEFAULT_TITLE;

			final List<URI> arts = item.getPropertyValues(DIDLObject.Property.UPNP.ALBUM_ART_URI.class);
			if (arts != null && arts.size() > 0) {
				this.artUri = arts.get(0).toString();
			}
			else {
				this.artUri = null;
			}

			Res res = null;
			if (item.getResources().size() > 1) {
				for (final Res r : item.getResources()) {
					if ("audio".equalsIgnoreCase(r.getProtocolInfo().getContentFormatMimeType().getType())) {
						res = r;
					}
				}
			}
			if (res == null && item.getResources().size() > 0) {
				res = item.getResources().get(0);
			}
			if (res != null) {
				this.contentType = res.getProtocolInfo().getContentFormatMimeType().toStringNoParameters();
				this.durationSeconds = StringUtils.isNotBlank(res.getDuration()) ? ModelUtil.fromTimeString(res.getDuration()) : -1;
			}
			else {
				this.contentType = null;
				this.durationSeconds = -1;
			}
		}
		else {
			this.title = DEFAULT_TITLE;
			this.artUri = null;
			this.contentType = DEFAULT_CONTENT_TYPE;
			this.durationSeconds = -1;
		}
	}

	public PlayingState (final MediaInfo mediaInfo, final String mediaUri, final String title, final String artUri, final String contentType, final long durationSeconds) {
		this.mediaInfo = mediaInfo;
		this.mediaUri = mediaUri;
		this.title = title;
		this.artUri = artUri;
		this.contentType = contentType;
		this.durationSeconds = durationSeconds;
	}

	public PlayingState withAltMedia (final String newUrl, final String newContentType) {
		return new PlayingState(this.mediaInfo, newUrl, this.title, this.artUri, newContentType, this.durationSeconds);
	}

	/**
	 * Never null.
	 */
	public MediaInfo getMediaInfo () {
		return this.mediaInfo;
	}

	public String getMediaUri () {
		return this.mediaUri;
	}

	public String getContentType () {
		return this.contentType;
	}

	/**
	 * https://developers.google.com/cast/docs/reference/messages#MediaInformation
	 */
	public Media toChromeCastMedia () {
		final Map<String, Object> metadata = new HashMap<>();
		metadata.put("metadataType", 0);
		metadata.put("title", this.title);
		metadata.put("images", Arrays.<Map<?, ?>>asList(Collections.<String, String>singletonMap("url", this.artUri)));
		return new Media(this.mediaUri, this.contentType,
				this.durationSeconds > 0 ? (double) this.durationSeconds : null,
				StreamType.BUFFERED, null, metadata, null, null);
	}

}

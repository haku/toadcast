package com.vaguehope.toadcast;

import java.net.URI;
import java.util.List;

import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.MediaInfo;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.item.Item;

public class PlayingState {

	private static final String DEFAULT_TITLE = "Toad Cast";
	private static final String DEFAULT_CONTENT_TYPE = "audio/mpeg";

	private final MediaInfo mediaInfo;
	private final String title;
	private final String relativeArtUri;
	private final String contentType;

	public PlayingState (final MediaInfo mediaInfo, final Item item) {
		if (mediaInfo == null) throw new IllegalArgumentException("mediaInfo must not be null.");
		if (mediaInfo.getCurrentURI() != null) throw new IllegalArgumentException("mediaInfo.currentUri must not be null.");
		this.mediaInfo = mediaInfo;

		if (item != null) {
			this.title = item.getTitle() != null ? item.getTitle() : DEFAULT_TITLE;

			final List<URI> arts = item.getPropertyValues(DIDLObject.Property.UPNP.ALBUM_ART_URI.class);
			if (arts != null && arts.size() > 0) {
				final URI artUri = arts.get(0);
				final URI mediaUri = URI.create(mediaInfo.getCurrentURI());
				this.relativeArtUri = mediaUri.relativize(artUri).toString();
			}
			else {
				this.relativeArtUri = null;
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
			}
			else {
				this.contentType = null;
			}
		}
		else {
			this.title = DEFAULT_TITLE;
			this.relativeArtUri = null;
			this.contentType = DEFAULT_CONTENT_TYPE;
		}
	}

	/**
	 * Never null.
	 */
	public MediaInfo getMediaInfo () {
		return this.mediaInfo;
	}

	public String getTitle () {
		return this.title;
	}

	public String getRelativeArtUri () {
		return this.relativeArtUri;
	}

	public String getContentType () {
		return this.contentType;
	}

}

package de.voicehired.wachak.core.util;

import de.voicehired.wachak.core.feed.Feed;

import java.util.Comparator;

/** Compares the title of two feeds for sorting. */
public class FeedtitleComparator implements Comparator<Feed> {

	@Override
	public int compare(Feed lhs, Feed rhs) {
		return lhs.getTitle().compareToIgnoreCase(rhs.getTitle());
	}

}

package de.voicehired.wachak.core.feed;

import de.voicehired.wachak.core.util.vorbiscommentreader.VorbisCommentReaderException;

import java.util.concurrent.TimeUnit;

public class VorbisCommentChapter extends Chapter {
	public static final int CHAPTERTYPE_VORBISCOMMENT_CHAPTER = 3;

	private static final int CHAPTERXXX_LENGTH = "chapterxxx".length();

	private int vorbisCommentId;

	public VorbisCommentChapter(int vorbisCommentId) {
		this.vorbisCommentId = vorbisCommentId;
	}

	public VorbisCommentChapter(long start, String title, FeedItem item,
			String link) {
		super(start, title, item, link);
	}

	@Override
	public String toString() {
		return "VorbisCommentChapter [id=" + id + ", title=" + title
				+ ", link=" + link + ", start=" + start + "]";
	}

	public static long getStartTimeFromValue(String value)
			throws VorbisCommentReaderException {
		String[] parts = value.split(":");
		if (parts.length >= 3) {
			try {
				long hours = TimeUnit.MILLISECONDS.convert(
						Long.parseLong(parts[0]), TimeUnit.HOURS);
				long minutes = TimeUnit.MILLISECONDS.convert(
						Long.parseLong(parts[1]), TimeUnit.MINUTES);
				if (parts[2].contains("-->")) {
					parts[2] = parts[2].substring(0, parts[2].indexOf("-->"));
				}
				long seconds = TimeUnit.MILLISECONDS.convert(
						((long) Float.parseFloat(parts[2])), TimeUnit.SECONDS);
				return hours + minutes + seconds;
			} catch (NumberFormatException e) {
				throw new VorbisCommentReaderException(e);
			}
		} else {
			throw new VorbisCommentReaderException("Invalid time string");
		}
	}

	/**
	 * Return the id of a vorbiscomment chapter from a string like CHAPTERxxx*
	 * 
	 * @return the id of the chapter key or -1 if the id couldn't be read.
	 * @throws VorbisCommentReaderException
	 * */
	public static int getIDFromKey(String key)
			throws VorbisCommentReaderException {
		if (key.length() >= CHAPTERXXX_LENGTH) { // >= CHAPTERxxx
			try {
				String strId = key.substring(8, 10);
				return Integer.parseInt(strId);
			} catch (NumberFormatException e) {
				throw new VorbisCommentReaderException(e);
			}
		}
		throw new VorbisCommentReaderException("key is too short (" + key + ")");
	}

	/**
	 * Get the string that comes after 'CHAPTERxxx', for example 'name' or
	 * 'url'.
	 */
	public static String getAttributeTypeFromKey(String key) {
		if (key.length() > CHAPTERXXX_LENGTH) {
			return key.substring(CHAPTERXXX_LENGTH, key.length());
		}
		return null;
	}

	@Override
	public int getChapterType() {
		return CHAPTERTYPE_VORBISCOMMENT_CHAPTER;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public void setStart(long start) {
		this.start = start;
	}

	public int getVorbisCommentId() {
		return vorbisCommentId;
	}

	public void setVorbisCommentId(int vorbisCommentId) {
		this.vorbisCommentId = vorbisCommentId;
	}
	
	

}

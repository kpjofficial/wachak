package de.voicehired.wachak.core.syndication.namespace.atom;

import android.util.Log;

import org.xml.sax.Attributes;

import de.voicehired.wachak.core.feed.FeedImage;
import de.voicehired.wachak.core.feed.FeedItem;
import de.voicehired.wachak.core.feed.FeedMedia;
import de.voicehired.wachak.core.syndication.handler.HandlerState;
import de.voicehired.wachak.core.syndication.namespace.NSITunes;
import de.voicehired.wachak.core.syndication.namespace.NSRSS20;
import de.voicehired.wachak.core.syndication.namespace.Namespace;
import de.voicehired.wachak.core.syndication.namespace.SyndElement;
import de.voicehired.wachak.core.syndication.util.SyndTypeUtils;
import de.voicehired.wachak.core.util.DateUtils;

public class NSAtom extends Namespace {
    private static final String TAG = "NSAtom";
    public static final String NSTAG = "atom";
    public static final String NSURI = "http://www.w3.org/2005/Atom";

    private static final String FEED = "feed";
    private static final String ID = "id";
    private static final String TITLE = "title";
    private static final String ENTRY = "entry";
    private static final String LINK = "link";
    private static final String UPDATED = "updated";
    private static final String AUTHOR = "author";
    private static final String CONTENT = "content";
    private static final String IMAGE = "logo";
    private static final String SUBTITLE = "subtitle";
    private static final String PUBLISHED = "published";

    private static final String TEXT_TYPE = "type";
    // Link
    private static final String LINK_HREF = "href";
    private static final String LINK_REL = "rel";
    private static final String LINK_TYPE = "type";
    private static final String LINK_TITLE = "title";
    private static final String LINK_LENGTH = "length";
    // rel-values
    private static final String LINK_REL_ALTERNATE = "alternate";
    private static final String LINK_REL_ENCLOSURE = "enclosure";
    private static final String LINK_REL_PAYMENT = "payment";
    private static final String LINK_REL_RELATED = "related";
    private static final String LINK_REL_SELF = "self";
    private static final String LINK_REL_NEXT = "next";
    // type-values
    private static final String LINK_TYPE_ATOM = "application/atom+xml";
    private static final String LINK_TYPE_HTML = "text/html";
    private static final String LINK_TYPE_XHTML = "application/xml+xhtml";

    private static final String LINK_TYPE_RSS = "application/rss+xml";

    /**
     * Regexp to test whether an Element is a Text Element.
     */
    private static final String isText = TITLE + "|" + CONTENT + "|" + "|"
            + SUBTITLE;

    public static final String isFeed = FEED + "|" + NSRSS20.CHANNEL;
    public static final String isFeedItem = ENTRY + "|" + NSRSS20.ITEM;

    @Override
    public SyndElement handleElementStart(String localName, HandlerState state,
                                          Attributes attributes) {
        if (localName.equals(ENTRY)) {
            state.setCurrentItem(new FeedItem());
            state.getItems().add(state.getCurrentItem());
            state.getCurrentItem().setFeed(state.getFeed());
        } else if (localName.matches(isText)) {
            String type = attributes.getValue(TEXT_TYPE);
            return new AtomText(localName, this, type);
        } else if (localName.equals(LINK)) {
            String href = attributes.getValue(LINK_HREF);
            String rel = attributes.getValue(LINK_REL);
            SyndElement parent = state.getTagstack().peek();
            if (parent.getName().matches(isFeedItem)) {
                if (rel == null || rel.equals(LINK_REL_ALTERNATE)) {
                    state.getCurrentItem().setLink(href);
                } else if (rel.equals(LINK_REL_ENCLOSURE)) {
                    String strSize = attributes.getValue(LINK_LENGTH);
                    long size = 0;
                    try {
                        if (strSize != null) {
                            size = Long.parseLong(strSize);
                        }
                    } catch (NumberFormatException e) {
                        Log.d(TAG, "Length attribute could not be parsed.");
                    }
                    String type = attributes.getValue(LINK_TYPE);
                    if (SyndTypeUtils.enclosureTypeValid(type)
                            || (type = SyndTypeUtils.getValidMimeTypeFromUrl(href)) != null) {
                        FeedItem currItem = state.getCurrentItem();
                        if(!currItem.hasMedia()) {
                            currItem.setMedia(new FeedMedia(currItem, href, size, type));
                        }
                    }
                } else if (rel.equals(LINK_REL_PAYMENT)) {
                    state.getCurrentItem().setPaymentLink(href);
                }
            } else if (parent.getName().matches(isFeed)) {
                if (rel == null || rel.equals(LINK_REL_ALTERNATE)) {
                    String type = attributes.getValue(LINK_TYPE);
                    /*
                     * Use as link if a) no type-attribute is given and
					 * feed-object has no link yet b) type of link is
					 * LINK_TYPE_HTML or LINK_TYPE_XHTML
					 */
                    if ((type == null && state.getFeed().getLink() == null)
                            || (type != null && (type.equals(LINK_TYPE_HTML)
                            || type.equals(LINK_TYPE_XHTML)))) {
                        state.getFeed().setLink(href);
                    } else if (type != null && (type.equals(LINK_TYPE_ATOM)
                            || type.equals(LINK_TYPE_RSS))) {
                        // treat as podlove alternate feed
                        String title = attributes.getValue(LINK_TITLE);
                        if (title == null) {
                            title = href;
                        }
                        state.addAlternateFeedUrl(title, href);
                    }
                } else if (rel.equals(LINK_REL_PAYMENT)) {
                    state.getFeed().setPaymentLink(href);
                } else if (rel.equals(LINK_REL_NEXT)) {
                    state.getFeed().setPaged(true);
                    state.getFeed().setNextPageLink(href);
                }
            }
        }
        return new SyndElement(localName, this);
    }

    @Override
    public void handleElementEnd(String localName, HandlerState state) {
        if (localName.equals(ENTRY)) {
            if (state.getCurrentItem() != null &&
                    state.getTempObjects().containsKey(NSITunes.DURATION)) {
                if (state.getCurrentItem().hasMedia()) {
                    state.getCurrentItem().getMedia().setDuration((Integer) state.getTempObjects().get(NSITunes.DURATION));
                }
                state.getTempObjects().remove(NSITunes.DURATION);
            }
            state.setCurrentItem(null);
        }

        if (state.getTagstack().size() >= 2) {
            AtomText textElement = null;
            String content;
            if (state.getContentBuf() != null) {
                content = state.getContentBuf().toString();
            } else {
                content = "";
            }
            SyndElement topElement = state.getTagstack().peek();
            String top = topElement.getName();
            SyndElement secondElement = state.getSecondTag();
            String second = secondElement.getName();

            if (top.matches(isText)) {
                textElement = (AtomText) topElement;
                textElement.setContent(content);
            }

            if (top.equals(ID)) {
                if (second.equals(FEED)) {
                    state.getFeed().setFeedIdentifier(content);
                } else if (second.equals(ENTRY)) {
                    state.getCurrentItem().setItemIdentifier(content);
                }
            } else if (top.equals(TITLE)) {

                if (second.equals(FEED)) {
                    state.getFeed().setTitle(textElement.getProcessedContent());
                } else if (second.equals(ENTRY)) {
                    state.getCurrentItem().setTitle(
                            textElement.getProcessedContent());
                }
            } else if (top.equals(SUBTITLE)) {
                if (second.equals(FEED)) {
                    state.getFeed().setDescription(
                            textElement.getProcessedContent());
                }
            } else if (top.equals(CONTENT)) {
                if (second.equals(ENTRY)) {
                    state.getCurrentItem().setDescription(
                            textElement.getProcessedContent());
                }
            } else if (top.equals(UPDATED)) {
                if (second.equals(ENTRY)
                        && state.getCurrentItem().getPubDate() == null) {
                    state.getCurrentItem().setPubDate(
                            DateUtils.parse(content));
                }
            } else if (top.equals(PUBLISHED)) {
                if (second.equals(ENTRY)) {
                    state.getCurrentItem().setPubDate(
                            DateUtils.parse(content));
                }
            } else if (top.equals(IMAGE)) {
                if(state.getFeed().getImage() == null) {
                    state.getFeed().setImage(new FeedImage(state.getFeed(), content, null));
                }
            }

        }
    }

}

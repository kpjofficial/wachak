package de.voicehired.wachak.core.syndication.namespace.atom;

import de.voicehired.wachak.core.syndication.namespace.Namespace;
import de.voicehired.wachak.core.syndication.namespace.SyndElement;
import org.apache.commons.lang3.StringEscapeUtils;

/** Represents Atom Element which contains text (content, title, summary). */
public class AtomText extends SyndElement {
	public static final String TYPE_TEXT = "text";
	public static final String TYPE_HTML = "html";
	public static final String TYPE_XHTML = "xhtml";

	private String type;
	private String content;

	public AtomText(String name, Namespace namespace, String type) {
		super(name, namespace);
		this.type = type;
	}

	/** Processes the content according to the type and returns it. */
	public String getProcessedContent() {
		if (type == null) {
			return content;
		} else if (type.equals(TYPE_HTML)) {
			return StringEscapeUtils.unescapeHtml4(content);
		} else if (type.equals(TYPE_XHTML)) {
			return content;
		} else { // Handle as text by default
			return content;
		}
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getType() {
		return type;
	}

}

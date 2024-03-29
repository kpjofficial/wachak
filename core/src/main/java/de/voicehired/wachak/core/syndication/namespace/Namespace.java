package de.voicehired.wachak.core.syndication.namespace;

import de.voicehired.wachak.core.syndication.handler.HandlerState;
import org.xml.sax.Attributes;


public abstract class Namespace {
	public static final String NSTAG = null;
	public static final String NSURI = null;
	
	/** Called by a Feedhandler when in startElement and it detects a namespace element 
	 * 	@return The SyndElement to push onto the stack
	 * */
	public abstract SyndElement handleElementStart(String localName, HandlerState state, Attributes attributes);
	
	/** Called by a Feedhandler when in endElement and it detects a namespace element 
	 * 	@return true if namespace handled the element, false if it ignored it
	 * */
	public abstract void handleElementEnd(String localName, HandlerState state);
	
}

package de.voicehired.wachak.core.syndication.handler;

import de.voicehired.wachak.core.feed.Feed;
import org.apache.commons.io.input.XmlStreamReader;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.Reader;

public class FeedHandler {

	public FeedHandlerResult parseFeed(Feed feed) throws SAXException, IOException,
			ParserConfigurationException, UnsupportedFeedtypeException {
		TypeGetter tg = new TypeGetter();
		TypeGetter.Type type = tg.getType(feed);
		SyndHandler handler = new SyndHandler(feed, type);

		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		SAXParser saxParser = factory.newSAXParser();
		File file = new File(feed.getFile_url());
		Reader inputStreamReader = new XmlStreamReader(file);
		InputSource inputSource = new InputSource(inputStreamReader);

		saxParser.parse(inputSource, handler);
		inputStreamReader.close();
		return new FeedHandlerResult(handler.state.feed, handler.state.alternateUrls);
	}
}

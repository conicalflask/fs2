package common;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.transform.TransformerException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.org.apache.xpath.internal.XPathAPI;

import common.SafeSaver.Savable;
import common.Sxml.SXMLException;


/**
 * A re-implementation of the ConfigManager, but more sane (IMHO)
 * 
 * The purpose of this class is to abstract the storage of configurable options,
 * and to provide centralised management of configuration defaults.
 * 
 * The configuration is a tree-shaped dictionary.
 * Keys are strings, values are Long or String.
 * Every entry may have unlimited children.
 * 
 * Keys are identified as they would be in a file system, separated by '/'
 * Keys may not start with a number, nor may they contain spaces. They must be representable by XML tag names.
 * 
 * @author gary
 */
public class Config implements Savable {
	
	public static class EmptyConfigDefaults implements ConfigDefaults {
		@Override
		public Map<String, String> getComments() {
			return Collections.emptyMap();
		}
		@Override
		public Map<String, String> getDefaults() {
			return Collections.emptyMap();
		}
		@Override
		public String getRootElementName() {
			return "default-config";
		}
	}
	
	private ConfigDefaults defs;
	private Sxml xml;
	private SafeSaver saver = new SafeSaver(this, FS2Constants.CONFIG_SAVE_INTERVAL);
	
	//Assumption: caching key->element mappings will be faster than traversing the XML every time.
	private HashMap<String, Element> elemCache = new HashMap<String, Element>();
	
	private boolean shuttingDown = false;
	
	//Used for derived configs.
	private String keyPrefix = "";
	
	
	/**
	 * Creates a new XML-backed configuration database based upon the file specified.
	 * @param defs
	 * @param location
	 * @throws Exception
	 */
	public Config(ConfigDefaults defs, File location) throws Exception {
		this.defs = defs;
		File configFile = location;
		try {
			xml = new Sxml(configFile);
		} catch (SXMLException e) {
			if (configFile.isFile()) {
				Logger.warn("Configuration file seems corrupt. It'll be re-initialised...");
				configFile.delete();
				try {
					xml = new Sxml(configFile);
				} catch (Exception second) {
					throw new Exception("Configuration manager couldn't be started because of Sxml.",e);
				}
			}
		}
		if (xml.createdNew()) buildDefaultConfig();
	}
	
	/**
	 * Creates a new config database based upon the provided Sxml object.
	 * @param defs
	 * @param inXml
	 */
	public Config(ConfigDefaults defs, Sxml inXml) {
		this.defs = defs;
		this.xml = inXml;
		if (xml.createdNew()) buildDefaultConfig();
	}
	
	void buildDefaultConfig() {
		synchronized (xml) {
			for (Entry<String, String> def : defs.getDefaults().entrySet()) {
				putString(def.getKey(), def.getValue(), defs.getComments().get(def.getKey()));
			}
			Logger.warn("Built a fresh configuration file for '"+defs.getRootElementName()+"' from defaults.");
			doSave();
		}
	}
	
	public void deleteKey(String key) {
		synchronized (xml) {
			try {
				Element elem = getElement(key);
				elem.getParentNode().removeChild(elem);
				
				key=keyPrefix+key; //caches uses prefixed keys.
				elemCache.remove(key);
				//remove all child keys from the cache too:
				Iterator<String> pKeys = elemCache.keySet().iterator();
				while (pKeys.hasNext()) {
					String pKey = pKeys.next();
					if (pKey.startsWith(key)) {
						pKeys.remove();
					}
				}
			} catch (DOMException e) {
				Logger.warn("Removing key '"+key+"' from config failed: "+e);
				Logger.log(e);
			}
			saver.requestSave();
		}
	}
	
	private Config() {} //used to derive configs.
	
	/**
	 * Returns a new Config that is stored entirely within the 'prefixKey' specified in this object.
	 * It effectively chroots into a key of the config.
	 * 
	 * @param defs
	 * @param prefixKey should be a key in the top-level config.
	 * @return
	 */
	public Config deriveConfig(ConfigDefaults defs, String prefixKey) {
		//1) effectively clone the config;
		Config ret = new Config();
		ret.defs = defs;
		ret.elemCache = elemCache;
		ret.saver = saver;
		ret.xml = xml;
		ret.keyPrefix = keyPrefix+prefixKey;
		return ret;
	}
	
	@Override
	public void doSave() {
		synchronized (xml) {
			try {
				if (shuttingDown) {
					xml.clean(); //remove nasty extra whitespace.
				}
				xml.save();
			} catch (SXMLException e) {
				Logger.warn("Configuration could not be saved: "+e);
				Logger.log(e);
			}
		}
	}
	
	/**
	 * Will erase the config file on JVM shutdown.
	 */
	public void eraseOnShutdown() {
		xml.getFile().deleteOnExit();
	}
	
	public boolean getBoolean(String key) {
		return Boolean.parseBoolean(getString(key));
	}
	
	/**
	 * Gets the keys of the children of this configuration key.
	 * The keys are fully qualified, absolute. (they can be passed directly into getString/getInt methods)
	 * @param key
	 * @return
	 */
	public LinkedList<String> getChildKeys(String key) {
		synchronized (xml) {
			LinkedList<String> keys = new LinkedList<String>();
			
			Node onNode = getElement(key).getFirstChild();
			while (onNode!=null) {
				try {
					if (onNode.getNodeType()!=Node.ELEMENT_NODE) continue;
					keys.add( key+"/"+((Element)onNode).getTagName() );
				} finally {
					onNode = onNode.getNextSibling();
				}
			}
			
			return keys;
		}
	}
	
	/**
	 * Gets the element in the configuration file specified by 'key'.
	 * Creates the element and all required children if necessary.
	 * @param key
	 * @return
	 */
	private Element getElement(String key) {
		key=keyPrefix+key;
		synchronized (xml) {
			if (elemCache.containsKey(key)) return elemCache.get(key);
			Document doc = xml.getDocument();
			Node onNode = doc.getFirstChild();
			if (onNode==null) {
				onNode = doc.createElement(defs.getRootElementName());
				doc.appendChild(onNode);
			}
			for (String bit : key.split("/")) {
				if (bit.length()==0) continue;
				try {
					Node nextNode = XPathAPI.selectSingleNode(onNode, bit);
					if (nextNode==null) {
						nextNode = doc.createElement(bit);
						onNode.appendChild(nextNode);
					}
					
					onNode = nextNode;
				} catch (TransformerException e) {
					Logger.severe("Exception processing configuration: "+e);
					e.printStackTrace();
					return null;
				}
			}
			elemCache.put(key, (Element)onNode);
			return (Element)onNode;
		}
	}
	
	public int getInt(String key) {
		return getLong(key).intValue();
	}
	
	public Long getLong(String key) {
		try {
			String stored = getString(key);
			if (stored.equals("")) return 0L;
			return Long.parseLong(stored);
		} catch (NumberFormatException e) {
			Logger.warn("Configuration item: '"+key+"' must be numeric, but it's not. Assuming zero.");
			return 0L;
		}
	}
	
	public String getString(String key) {
		synchronized (xml) {
			String potential = getElement(key).getTextContent();
			if (potential.equals("")) {
				if (defs.getDefaults().containsKey(key)) {
					putString(key, defs.getDefaults().get(key), defs.getComments().get(key));  //store the default in the file from now on too.
					return defs.getDefaults().get(key);
				} else return "";
		 	} else return potential;
		}
	}
	public String getXML() {
		synchronized (xml) {
			return xml.toString();
		}
	}
	
	public void putBoolean(String key, Boolean value) {
		putString(key, value.toString(), null);
	}
	
	public void putInt(String key, Integer value) {
		putString(key, value.toString(), null);
	}
	public void putLong(String key, Long value) {
		putString(key, value.toString(), null);
	}
	
	public void putString(String key, String value) {
		putString(key, value, null);
	}
	
	/**
	 * Internal 
	 * @param key
	 * @param value
	 * @param comment allows a comment to be placed after this node in the XML file.
	 */
	private void putString(String key, String value, String comment) {
		synchronized (xml) {
			Element confElem = getElement(key);
			if (comment!=null) confElem.getParentNode().appendChild(confElem.getOwnerDocument().createComment(key+": "+comment));
			
			//Now only set the text content if this node does not already contain elements (ie: ignore requests to store information in collections)
			NodeList childs = confElem.getChildNodes();
			boolean canWrite = true;
			for (int i=0; i<childs.getLength(); i++) {
				if (childs.item(i).getNodeType()==Node.ELEMENT_NODE) canWrite=false;
			}
			if (canWrite) confElem.setTextContent(value);
			saver.requestSave();
		}
	}
	
	public void shutdown() {
		shuttingDown = true;
		saver.saveShutdown();
	}
	
}

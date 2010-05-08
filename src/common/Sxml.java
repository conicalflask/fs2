package common;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.sun.org.apache.xpath.internal.XPathAPI;


/**
 * A helper class to make playing with XML using a DOM more friendly.
 * 
 * This only understands UTF-8 (for files) correctly at the moment.
 */
public class Sxml {
	
	@SuppressWarnings("serial")
	public static class SXMLException extends Exception {
		public SXMLException(Throwable cause) {
			super(cause);
		}
		
		public SXMLException(String descr) {
			super(descr);
		}
	}
	
	private File hardcopy = null;
	private Document xml;
	//Was this file new this time?
	private boolean fresh = false;
	private String doctypeSystem = "";
	private String doctypePublic = "";
	private boolean standalone = false;
	private Integer indentAmount = 4;
	
	
	public Integer getIndentAmount() {
		return indentAmount;
	}

	public void setIndentAmount(Integer indentAmount) {
		this.indentAmount = indentAmount;
	}

	public static Element getElementById(Node context, String id) {
		try {
			return (Element)XPathAPI.selectSingleNode(context, "//*[@id='"+id+"']");
		} catch (TransformerException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public Element getElementById(String id) {
		return getElementById(this.getDocument(), id);
	}
	
	/**
	 * Constucts a new empty XML document attached to no file.
	 */
	public Sxml() throws SXMLException {
		construct(null,null);
	}
	
	/**
	 * Constructs a new XML document parsed from the input stream supplied.
	 * 
	 * This will buffer the stream so that it may be used directly on socket input streams.
	 * 
	 * @param inStream The stream with XML to parse in it.
	 */
	public Sxml(InputStream inStream) throws SXMLException {
		construct(null, new BufferedInputStream(inStream));
	}
	
	/**
	 * Constructs a new XML document parsed from the file if it exists, and a blank new document otherwise.
	 * @param path The XML file to parse in and save to. Will be created on save() if this doesn't exist already.
	 */
	public Sxml(File path) throws SXMLException {
		construct(path, null);
	}
	
	
	
	private void construct(File path, InputStream fromStream) throws SXMLException {
		try {
			if (path != null) hardcopy = path.getAbsoluteFile();
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setValidating(false);
			dbf.setFeature("http://xml.org/sax/features/namespaces", false);
			dbf.setFeature("http://xml.org/sax/features/validation", false);
			dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
			dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

			DocumentBuilder db = dbf.newDocumentBuilder();
			
			//db.setEntityResolver(dtdCache);
			
			if (fromStream != null) {
				xml = db.parse(fromStream);
			} else if (path != null && path.exists()) {
				FileInputStream fis = new FileInputStream(hardcopy);
				try {
					InputSource is = new InputSource(new BufferedReader(new InputStreamReader(fis,"UTF-8")));
					xml = db.parse(is);
				} finally {
					fis.close();
				}
			} else {
				xml = db.newDocument();
				fresh = true;
			}
		} catch (Exception e) {
			throw new SXMLException(e);
		}
	}
	
	/**
	 * Get the w3c DOM Document for this SXML object.
	 * @return w3c DOM Document object for this SXML instance.
	 */
	public Document getDocument() {
		return xml;
	}
	
	/**
	 * Returns the file object that this SXML object uses as a hard-copy for save()
	 * @return the hardcopy file
	 */
	public File getFile() {
		return hardcopy;
	}
	
	/**
	 * Returns true if a new document was created and not parsed from a source. Use this to determine if a configuration file XML (for instance) requires initialisation.
	 * @return was this document created empty?
	 */
	public boolean createdNew() {
		return fresh;
	}
	
	/**
	 * Saves this SXML document back to the hardcopy file.
	 * @throws TransformerException
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public void save() throws SXMLException {
		save(true);
	}
	
	/**
	 * Saves this SXML document back to the hardcopy file.
	 * @param indent Should the output be pretty-printed?
	 * @throws TransformerException
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public void save(boolean indent) throws SXMLException {
		if (hardcopy != null) {
			save(hardcopy, indent);
		} else {
			throw new SXMLException("Sxml not created on a file");
		}
	}
	
	/**
	 * Serialises this document to an output stream. 
	 * @param outStream the destination stream.
	 * @param indent Pretty-print?
	 */
	public void save(OutputStream outStream, boolean indent) throws SXMLException {
		outputXML(null, outStream, indent);
	}
	
	/**
	 * Saves this document to a file other than the hardcopy.
	 * @param tofile the destination file.
	 * @param indent Pretty-print?
	 */
	public void save(File tofile, boolean indent) throws SXMLException {
		outputXML(tofile, null, indent);
	}
	
	private synchronized void outputXML(File file, OutputStream stream, boolean indent) throws SXMLException {
		try {
			File workingFile = null;
	        if (file != null) {
	        	workingFile = new File(file.getCanonicalPath()+".working");
	        	stream = new BufferedOutputStream(new FileOutputStream(workingFile));
	        }
	        stream.write(generateString(indent).getBytes("UTF-8"));
	        if (file != null) {
	        	//by using a temporary file then moving it to a target
	        	//we hope to get as close to atomicity as possible in a cross platform way.
	        	stream.close();
	        	file.delete();
	        	workingFile.renameTo(file);
	        }
		} catch (Exception e) {
			throw new SXMLException(e);
		}
	}

	/**
	 * Change the hard-copy associated with this SXML document.
	 * @param hardcopy the new file used as a hardcopy.
	 */
	public void setHardcopy(File hardcopy) {
		this.hardcopy = hardcopy;
	}
	
	/**
	 * Returns a string that represents this XML document.
	 * @param indent Pretty-print the document?
	 * @return the XML document as a string.
	 */
	public String generateString(boolean indent) throws TransformerException {
		TransformerFactory tf;
		Transformer transformer;
		tf = TransformerFactory.newInstance();
		tf.setAttribute("indent-number", indentAmount );
        transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, (indent ? "yes" : "no"));
		if (doctypeSystem != "") transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, doctypeSystem);
		if (doctypePublic != "") transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, doctypePublic);
        transformer.setOutputProperty(OutputKeys.STANDALONE, (standalone ? "yes" : "no"));
		
		StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);
        DOMSource source = new DOMSource(xml);
        transformer.transform(source, result);
        return sw.toString();
	}
	
	/**
	 * Returns a string representation of this XML document if possible.
	 * Returns an empty string otherwise.
	 */
	public String toString() {
		try {
			return generateString(true);
		} catch (TransformerException e) {
			return "";
		}
	}

	public String getDoctypeSystem() {
		return doctypeSystem;
	}

	public void setDoctypeSystem(String doctypeSystem) {
		this.doctypeSystem = doctypeSystem;
	}

	public String getDoctypePublic() {
		return doctypePublic;
	}

	public void setDoctypePublic(String doctypePublic) {
		this.doctypePublic = doctypePublic;
	}

	public boolean isStandalone() {
		return standalone;
	}

	public void setStandalone(boolean standalone) {
		this.standalone = standalone;
	}

	/**
	 * Iterates through the XML document removing uneeded whitespace nodes.
	 */
	public void clean() {
		clean(getDocument());
	}
	
	private boolean clean(Node cparent) {
		Node onNode = cparent.getFirstChild();
		while (onNode!=null) {
			if (clean(onNode)) { //a return of true means that this node is useless and should be erased.
				Node next = onNode.getNextSibling();
				cparent.removeChild(onNode);
				onNode = next;
			} else {
				onNode = onNode.getNextSibling();
			}
		}
		if (cparent.getNodeType()==Node.TEXT_NODE) {
			return cparent.getTextContent().trim().equals("");
		}
		return false;
	}
}

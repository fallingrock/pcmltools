/*
    This file is part of PCMLTOOLS.

    Copyright (C) 2020 David Gibbs
    
    PCMLTOOLS is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    PCMLTOOLS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with PCMLTOOLS.  If not, see <https://www.gnu.org/licenses/>.

 */

package net.fallingrock.pcmltools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400Exception;
import com.ibm.as400.access.AS400SecurityException;
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.ObjectDescription;
import com.ibm.as400.access.ObjectDoesNotExistException;
import com.ibm.as400.access.QSYSObjectPathName;
import com.ibm.as400.data.PcmlException;
import com.ibm.as400.data.ProgramCallDocument;

/**
 * Utilities to work with PCML in JT400.
 * 
 * @author David Gibbs
 *
 */
public class PcmlTools {

	private static final String QBNRPII = "qbnrpii";
	
	private final AS400 host;
	private String pcmlDoc = null;
	private final QSYSObjectPathName path;
	private Document doc = null;
	
	public static final String PGM = "PGM";
	public static final String SRVPGM = "SRVPGM";
	
	public PcmlTools(AS400 host, QSYSObjectPathName path) {
		this.host = host;
		this.path = path;

	}

	public PcmlTools(AS400 host, String lib, String obj, String type) {
		this(host, new QSYSObjectPathName(lib, obj, type));
	}

	public PcmlTools(AS400 host, String path) {
		this(host, new QSYSObjectPathName(path));
	}

	/**
	 * Loads PCML from object and returns it as a XML string

	 * @return string containing XML
	 * @throws PcmlToolsException
	 */
	public String getXML() throws PcmlToolsException {
		Document doc = getDocument();

		// convert merged PCML into ProgramCallDocument object

		// First, we need to get the XML source from the information 
		// retrieved from each module
		try {

			return printDocument(doc);

		} catch (IOException e) {
			throw new PcmlToolsException(e);
		} catch (TransformerException e) {
			throw new PcmlToolsException(e);
		}
	}

	/**
	 * Loads PCML from object and returns it as a w3c document object
	 * 
	 * @return w3c document object
	 * @throws PcmlToolsException
	 */
	public Document getDocument() throws PcmlToolsException {

		if (doc == null) {
			doc = loadDocFromObject();
		}
		
		return doc;
	}

	/**
	 * Clears any previously performed work
	 */
	public void reset() {
		doc = null;
	}
	
	/**
	 * Loads PCML from object and returns Program Call document object
	 *  
	 * @return PCML Document
	 * @throws PcmlToolsException
	 */
	public ProgramCallDocument getPcml() throws PcmlToolsException {

		try {
			String xml = getXML();

			// now, construct a new ProgramCallDocument object from the XML source
			ByteArrayInputStream bais = new ByteArrayInputStream(xml.getBytes());

			// If a PCML document name wasn't specified, use the program name
			if (pcmlDoc == null) {
				pcmlDoc = path.getObjectName();
			}

			ProgramCallDocument result = new ProgramCallDocument(host, pcmlDoc, bais, null, null, 
					ProgramCallDocument.SOURCE_PCML);

			return result;

		} catch (PcmlException e) {
			throw new PcmlToolsException(e);
		}
	}

	/**
	 * @return the doc
	 */
	public String getPcmlDoc() {
		return pcmlDoc;
	}

	/**
	 * @param doc the doc to set
	 */
	public void setPcmlDoc(String pcmlDoc) {
		this.pcmlDoc = pcmlDoc;
	}

	/**
	 * @return the host
	 */
	public AS400 getHost() {
		return host;
	}

	/**
	 * @return the path
	 */
	public QSYSObjectPathName getPath() {
		return path;
	}

	/**
	 * Retrieve ProgramcallDocument object from a named program or service program
	 * that has been compiled with PGMINFO(*PCML:[*MODULE | *ALL]).
	 * 
	 * @parm doc PCML document name (can be null)
	 * @param path QSYS path to program or service program.
	 * @return PCML document
	 * @throws PcmlToolsException
	 */
	private Document loadDocFromObject() throws PcmlToolsException {

		String objname = path.getObjectName();
		String objtype = path.getObjectType();
		String libname = path.getLibraryName();

		if (!objtype.equalsIgnoreCase(PGM) && !objtype.equalsIgnoreCase(SRVPGM)) {
			throw new PcmlToolsException("Invalid object type " + objtype);
		}

		try {

			// Make sure the object exists
			ObjectDescription od = new ObjectDescription(host, path);
			od.refresh();

			// Setup our own PCML to call the QBNRPII api
			ProgramCallDocument pcml = new ProgramCallDocument(host, 
					"net.fallingrock.pcmltools.QBNRPII");

			pcml.setStringValue(QBNRPII + ".obj.lib", libname);
			pcml.setStringValue(QBNRPII + ".obj.name", objname);
			pcml.setStringValue(QBNRPII + ".objType", "*" + objtype);
			pcml.callProgram(QBNRPII);

			int entries = pcml.getIntValue(QBNRPII + ".receiver.NumberOfEntries");

			int[] indices = new int[2]; 

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);

			DocumentBuilder builder = factory.newDocumentBuilder();

			Document merged = null;

			// cycle through all the modules in the object, retrieving PCML for each and
			// merging them together with the first set found
			for (indices[0] = 0; indices[0] < entries; indices[0]++) {
				String pcmlSrc = (String)pcml.getValue(QBNRPII + 
						".receiver.Entry.InterfaceInfo", indices);

				// embedded PCML found? No, throw an exception
				if (pcmlSrc == null) {
					throw new PcmlToolsException("Object " + path.toQualifiedObjectName() + 
							" does not have program interface information. Recompile with PGMINFO(*PCML:*MODULE).");
				}
				
				Document pcmlDoc = builder.parse(new ByteArrayInputStream(pcmlSrc.getBytes()));

				if (merged == null) {
					// first module in PGM or SRVPGM, use this as the start of our XML
					merged = pcmlDoc;
				} else {
					// Not first module, merge XML into the start of our XML document.
					// grab all the children of the PCML node
					NodeList pcmlNl = pcmlDoc.getElementsByTagName("pcml");
					Node pcmlNode = pcmlNl.item(0);
					NodeList children = pcmlNode.getChildNodes();

					// for each child of pcml, append to the first pcml retrieved
					for (int i=0;i<children.getLength();i++) {
						Node childNode = children.item(i);

						Node copiedNode = merged.importNode(childNode, true);

						Element mergedRoot = merged.getDocumentElement();

						mergedRoot.appendChild(copiedNode);
					}
				}

			}
			
			return merged;

		} catch (IOException e) {
			throw new PcmlToolsException(e);
		} catch (ParserConfigurationException e) {
			throw new PcmlToolsException(e);
		} catch (SAXException e) {
			throw new PcmlToolsException(e);
		} catch (AS400Exception e) {
			throw new PcmlToolsException(e);
		} catch (AS400SecurityException e) {
			throw new PcmlToolsException(e);
		} catch (ErrorCompletingRequestException e) {
			throw new PcmlToolsException(e);
		} catch (InterruptedException e) {
			throw new PcmlToolsException(e);
		} catch (PcmlException e) {
			throw new PcmlToolsException(e);
		} catch (ObjectDoesNotExistException e) {
			throw new PcmlToolsException(e);
		} 

	}

	private String printDocument(Document doc) throws IOException, TransformerException {
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		transformer.transform(new DOMSource(doc), 
				new StreamResult(new OutputStreamWriter(baos, "UTF-8")));
		
		String xml = baos.toString();
		
		return xml;
	}	

}


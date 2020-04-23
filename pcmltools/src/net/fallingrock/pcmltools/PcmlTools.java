package net.fallingrock.pcmltools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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

	private final AS400 host;
	
	public PcmlTools(AS400 host) {
		this.host = host;
	}

	/**
	 * Retrieve ProgramcallDocument object from a named program or service program
	 * that has been compiled with PGMINFO(*PCML:[*MODULE | *ALL]).
	 * 
	 * @parm doc PCML document name (can be null)
	 * @param lib library
	 * @param obj program or service program name
	 * @param type PGM or SRVPGM
	 * @return PCML document
	 * @throws PcmlToolsException
	 */
	public ProgramCallDocument loadFromObject(String doc, String lib, String obj, String type) throws PcmlToolsException {
		return loadFromObject(doc, QSYSObjectPathName.toPath(lib, obj, type));
	}

	/**
	 * Retrieve ProgramcallDocument object from a named program or service program
	 * that has been compiled with PGMINFO(*PCML:[*MODULE | *ALL]).
	 * 
	 * @param lib library
	 * @param obj program or service program name
	 * @param type PGM or SRVPGM
	 * @return PCML document
	 * @throws PcmlToolsException
	 */
	public ProgramCallDocument loadFromObject(String lib, String obj, String type) throws PcmlToolsException {
		return loadFromObject(null, lib, obj, type);
	}

	/**
	 * Retrieve ProgramcallDocument object from a named program or service program
	 * that has been compiled with PGMINFO(*PCML:[*MODULE | *ALL]).
	 * 
	 * @param path QSYSObjecPathName object to program or service program.
	 * @return PCML document
	 * @throws PcmlToolsException
	 */
	public ProgramCallDocument loadFromObject(String doc, QSYSObjectPathName path) throws PcmlToolsException {
		return loadFromObject(doc, path.toString());
	}
	
	/**
	 * Retrieve ProgramcallDocument object from a named program or service program
	 * that has been compiled with PGMINFO(*PCML:[*MODULE | *ALL]).
	 * 
	 * @parm doc PCML document name (can be null)
	 * @param path QSYSObjecPathName object to program or service program.
	 * @return PCML document
	 * @throws PcmlToolsException
	 */
	public ProgramCallDocument loadFromObject(QSYSObjectPathName path) throws PcmlToolsException {
		return loadFromObject(null, path);
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
	public ProgramCallDocument loadFromObject(String path) throws PcmlToolsException {
		return loadFromObject(null, path);
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
	public ProgramCallDocument loadFromObject(String doc, String path) throws PcmlToolsException {

		ProgramCallDocument result = null;
		
		QSYSObjectPathName qsysPath = new QSYSObjectPathName(path);
		
		String objname = qsysPath.getObjectName();
		String objtype = qsysPath.getObjectType();
		String libname = qsysPath.getLibraryName();
		
		if (!objtype.equalsIgnoreCase("PGM") && !objtype.equalsIgnoreCase("SRVPGM")) {
			throw new PcmlToolsException("Invalid object type " + objtype);
		}
		
		try {
			
			// Make sure the object exists
			ObjectDescription od = new ObjectDescription(host, qsysPath);
			od.refresh();
			
			// Setup our own PCML to call the QBNRPII api
			ProgramCallDocument pcml = new ProgramCallDocument(host, "com.fallingrock.getpcml.QBNRPII");

			pcml.setStringValue("qbnrpii.objLib", libname);
			pcml.setStringValue("qbnrpii.objName", objname);
			pcml.setStringValue("qbnrpii.objType", "*" + objtype);
			pcml.callProgram("qbnrpii");

			int entries = pcml.getIntValue("qbnrpii.receiver.NumberOfEntries");

			int[] indices = new int[2]; 

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);

			DocumentBuilder builder = factory.newDocumentBuilder();

			Document merged = null;

			// cycle through all the modules in the object, retrieving PCML for each and
			// merging them together with the first set found
			for (indices[0] = 0; indices[0] < entries; indices[0]++) {
				String pcmlSrc = (String)pcml.getValue("qbnrpii.receiver.Entry.InterfaceInfo", indices);

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
			
			// convert merged PCML into ProgramCallDocument object
			
			// First, we need to get the XML source from the information 
			// retrieved from each module
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			
			printDocument(merged, baos);
			
			// now, construct a new ProgramCallDocument object from the XML source
			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			
			// If a PCML document name wasn't specified, use the program name
			if (doc == null) {
				doc = objname;
			}
			
			result = new ProgramCallDocument(host, doc, bais, null, null, 
					ProgramCallDocument.SOURCE_PCML);

		} catch (IOException e) {
			throw new PcmlToolsException(e);
		} catch (TransformerException e) {
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

		return result;
	}

	private void printDocument(Document doc, OutputStream out) throws IOException, TransformerException {
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

		transformer.transform(new DOMSource(doc), 
				new StreamResult(new OutputStreamWriter(out, "UTF-8")));
	}	

}


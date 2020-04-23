package net.fallingrock.pcmltools;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

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
import org.w3c.dom.NodeList;





public class XmlMergeTest {

	private void execute1() {
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setValidating(false);
			DocumentBuilder db = dbf.newDocumentBuilder();

			Document merged = null;

			String[] filenames = new String[] {"mod1.pcml", "mod2.pcml"};

			for (String filename : filenames) {
				Document pcmlDoc = db.parse(filename);

				if (merged == null) {
					merged = pcmlDoc;
				} else {
					NodeList pcmlNl = pcmlDoc.getElementsByTagName("pcml");
					Node pcmlNode = pcmlNl.item(0);
					NodeList children = pcmlNode.getChildNodes();

					for (int i=0;i<children.getLength();i++) {
						Node childNode = children.item(i);

						Node copiedNode = merged.importNode(childNode, true);
						
						Element mergedRoot = merged.getDocumentElement();
						
						mergedRoot.appendChild(copiedNode);
					}
				}
			}
			printDocument(merged, System.out);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


	}


	private void printDocument(Document doc, OutputStream out) throws IOException, TransformerException {
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

		transformer.transform(new DOMSource(doc), 
				new StreamResult(new OutputStreamWriter(out, "UTF-8")));
	}

	public static void main(String[] args) {
		XmlMergeTest xmt = new XmlMergeTest();
		xmt.execute1();

	}

}

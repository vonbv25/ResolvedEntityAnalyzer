package com.clearforest.rea.util;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.clearforest.rea.MyNamespaceContextImpl;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.sparql.util.FmtUtils;

	public class XMLUtils {
	/**
	 * get a XPath object for the linked data document used to extract data.
	 * 
	 * @param linkedData
	 * @return
	 */
	public static XPath getXPath(Document linkedData) {
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		
		MyNamespaceContextImpl nsctx = new MyNamespaceContextImpl();		
		NamedNodeMap attributes = linkedData.getDocumentElement().getAttributes();
		for (int i = 0; i < attributes.getLength(); i++) {
			Node attr = attributes.item(i);				
			String nodename = attr.getNodeName();
			nodename = nodename.substring(nodename.indexOf(':') + 1);
			nsctx.setNamespace(nodename, attr.getNodeValue());
		}
		xpath.setNamespaceContext(nsctx);
		return xpath;
	}
	
	/**
	 * get a string value from jena solution parameters
	 * 
	 * @param rBind 	Sparql solution from query run.
	 * @param varName	field name in query
	 * @return
	 */
	public static String getVarValueAsString(QuerySolution rBind, String varName) {
		RDFNode obj = rBind.get(varName);

		if (obj == null)
			return "N/A";

		return FmtUtils.stringForRDFNode(obj);
	}
	
	/**
	 * get child node by name
	 * 
	 * @param parent
	 * @param name
	 * @return
	 */
	public static Node getNodeByName(Node parent, String name)	{
		for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
			if(parent.getChildNodes().item(i).getNodeName().equals(name))
				return parent.getChildNodes().item(i);
		}	
		
		return null;
	}
}

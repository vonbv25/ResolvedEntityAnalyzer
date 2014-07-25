package com.clearforest.rea;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.apache.xerces.util.DOMUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.clearforest.rea.endpoint.OpenCalais;
import com.clearforest.rea.exception.AnalyzerException;
import com.clearforest.rea.util.Configuration;
import com.clearforest.rea.util.StringUtils;
import com.clearforest.rea.util.XMLUtils;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetRewindable;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.sparql.resultset.ResultSetMem;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

public class DocumentAnalyzer {
	private static DocumentAnalyzer analyzer = null;
	
	
	/**
	 * Analyze document. resolve Entities from Open Calais and output a XML file. 
	 * 
	 * @param key		Open Calais API license key
	 * @param filePath	Path to a text file. contents will be send to Open Calais for analyzing.
	 * @return			Is process succeeded.
	 */
	public static boolean analyze(String key, String filePath) {
		
		// read content from file
		String content;
		try {
			int MAX_DOCSIZE;
			MAX_DOCSIZE = Configuration.getCurrent().getMaxDocsize();
			
			
			if(analyzer == null)
				analyzer = new DocumentAnalyzer();
			System.out.println("Reading text file ...");
			content = analyzer.readfile(filePath);
		
			// OpenCalais is unable to process document 
			// longer the 100,000 charachters
			if(content.length() > MAX_DOCSIZE)
				content = content.substring(0, MAX_DOCSIZE);
	
			System.out.println("Retrieving Calais RDF ...");
			// send request to OpenCalais
			content = analyzer.getTextFromOpenCalais(key, content);			
			if(content == null) return false;
			
			System.out.println("Submit Linked Data Call Call ...");
			// build XML output
			Document outdoc = analyzer.getDocumentFromRdf(content);

			System.out.println("Saving output XML to Disk ...");
			// save XML to file
			serializeDoc(outdoc);		
			
			return true;
		
		} catch (AnalyzerException ae) {
			ae.printStackTrace(System.out);
		}
		return false;
		
		
	}

	private static void serializeDoc(Document outdoc) throws AnalyzerException {
		try {
			XMLSerializer serializer = new XMLSerializer();
			serializer.setOutputCharStream(new FileWriter(Configuration.getCurrent().getOutputFileName()));
			serializer.serialize(outdoc);
		} catch (IOException e) {
			throw new AnalyzerException("(EE) XML Serialization Failed or could not find configuration data", e);
		}
		
	}

	/**
	 * Build simple XML output from RDF content. 
	 * Show list of entities of type company, geography or product
	 * 
	 * @param content	RDF text content
	 * @return			Simple XML document output
	 * @throws AnalyzerException	A throwable wrapper class to easy the error handling
	 */
	private Document getDocumentFromRdf(String content)
			throws AnalyzerException {
		try {
			Document xdoc;		
			xdoc = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder().newDocument();		

			// set root element of the output XML
			Node Response = xdoc.createElement("Response");
			xdoc.appendChild(Response);		
			
			// build Geographies section to output XML
			enrichCompanies(createSection(
				Response, "Companies", "Company", 
				"opencalais/companies.sparql", content), content);
	
			// build Geographies section to output XML
			enrichGeo(createSection(
				Response, "Geographies", "Geography", 
				"opencalais/geo.sparql", content));			
			
			// build Products section to output XML
			enrichProduct(createSection(
				Response, "Products", "Product", 
				"opencalais/products.sparql", content));
			
			return xdoc;
		} catch (ParserConfigurationException e) {
			throw new AnalyzerException("(EE) Error creating new DOM object for content", e);
		}
	}
	
	/**
	 * Build a simple section in XML output
	 * 
	 * @param Response		Root node
	 * @param sectionName	node section in output
	 * @param elementName	elements name in section 
	 * @param sparqlFile	Query file
	 * @param inContent		RDF to extract data from 
	 * @throws AnalyzerException	A throwable wrapper class to easy the error handling
	 */
	private Node createSection(Node Response, String sectionName, 
			String elementName,
			String sparqlFile, String inContent) throws AnalyzerException {
		
		System.out.println("Building " + sectionName + " Section");
		Document xdoc = Response.getOwnerDocument();
		Node sectionNode = xdoc.createElement(sectionName);
		Response.appendChild(sectionNode);
		ResultSet results = getSparqlOutput(sparqlFile, inContent);
		builldXml(sectionNode, elementName,	results);	
		
		// add the exact texts in document	
		if(sectionNode.getFirstChild() != null) {
			NodeList elements = sectionNode.getChildNodes();
			for (int i = 0; i < elements.getLength(); i++) {			
				Node subjectNode = XMLUtils.getNodeByName(elements.item(i), "subject");		
				if (subjectNode != null) {
					Node exacts = subjectNode.getOwnerDocument().createElement("exacts");
					String paramValue = DOMUtil.getChildText(subjectNode);
					
					results = getSparqlOutput("opencalais/exact.sparql", inContent, "${subject}", paramValue);
					builldXml(exacts, null,	results);	
					
					elements.item(i).appendChild(exacts);
				}
			}
		}
		
		return sectionNode;
	}

	/**
	 * Use "Linked Data" to add nodes with information to the simple XML format "Companies" section
	 * 
	 * @param companiesNodes simple XML format "Companies" section 
	 * @param content 
	 * @throws AnalyzerException	A throwable wrapper class to easy the error handling
	 */
	private void enrichCompanies(Node companiesNodes, String content) throws AnalyzerException {
		for (int i = 0; i < companiesNodes.getChildNodes().getLength(); i++) {
			Node company = companiesNodes.getChildNodes().item(i);
			
			Node resourceNode = company.getChildNodes().item(2);
			String resourceUri = DOMUtil.getChildText(resourceNode);
			resourceUri = resourceUri.substring(1, resourceUri.length() - 1);
			
			Node newnode = company.getOwnerDocument().createElement("url");
					
			newnode.appendChild(company.getOwnerDocument().createTextNode(resourceUri));		
			company.appendChild(newnode);
			company.removeChild(resourceNode);
			updateEventsAndFacts(company, content);
			
			Node subjectNode = XMLUtils.getNodeByName(company, "subject");
			company.removeChild(subjectNode);
			
			Document linkedData = OpenCalais.getCalaisRdf(resourceUri + ".rdf");
			if(linkedData!=null) {
				XPath xpath = XMLUtils.getXPath(linkedData); 
				if(linkedData.getDocumentElement().getAttributes().getNamedItem("xmlns:foaf") != null)
					enrichFromLinked(xpath, linkedData, company, "//foaf:homepage", null, "Homepage");
				enrichFromLinked(xpath, linkedData, company, "//cld:competitor", "Competitors", "Competitor");
				enrichFromLinked(xpath, linkedData, company, "//cld:personposition", "PersonsInPosition", "Person");
				if(linkedData.getDocumentElement().getAttributes().getNamedItem("xmlns:owl") != null)
					enrichFromLinked(xpath, linkedData, company, "//owl:sameAs", "RdfLinks", "Link");
				if(linkedData.getDocumentElement().getAttributes().getNamedItem("xmlns:foaf") != null)
					enrichFromLinked(xpath, linkedData, company, "//foaf:page", "WebLinks", "Link");
			}
			
			
			
		}
	}

	/**
	 * building events and facts section in xml output and displaing type of event and markup data.
	 * 
	 * @param company
	 * @param content
	 * @throws AnalyzerException	A throwable wrapper class to easy the error handling
	 */
	private void updateEventsAndFacts(Node company, String content) throws AnalyzerException {
		System.out.println("Handling 'EventsAndFacts' ...");
		
		// subject uri refer to the id of the markup entity 
		// contains the company
		Node subject = company.getChildNodes().item(2);
		String subjectURI = DOMUtil.getChildText(subject);		
		subjectURI = subjectURI.substring(1, subjectURI.length() - 1);
		System.out.println(subjectURI);
		
		// use xpath to search for all the element linked to the company 
		// markup entity on calais api output RDF.
		Document contentDom = OpenCalais.stringToDom(content);		
		XPath xpath = XMLUtils.getXPath(contentDom);		
		String xpathQuery = "//*[@rdf:resource='" + subjectURI + "']/..";
		NodeList nlist = getNodesFromDoc(xpath, xpathQuery, contentDom);
		
		// create section to add to the simple 
		Node eventsAndFacts = company.getOwnerDocument().createElement("EventsAndFacts");
		
		for (int i = 0; i < nlist.getLength(); i++) {		
			Node typeNode = XMLUtils.getNodeByName(nlist.item(i), "rdf:type");
			if(typeNode != null)	{ // if node has type
				String rdfType = typeNode.getAttributes().getNamedItem("rdf:resource").getTextContent();
				if(rdfType.indexOf("/type/em/r/") > -1) { // if type is a "Relational" element (Like Acquisition)
					// an "event or fact" element in put xml for each event
					Node eventNode = company.getOwnerDocument().createElement("EventOrFact");
					
					// subject URI will link to the markup entity of the Relational element					
					subjectURI = typeNode.getParentNode().getAttributes()
							.getNamedItem("rdf:about").getTextContent();
					// set rdfType to hold the relational type name.
					rdfType = rdfType.substring(rdfType.lastIndexOf('/') + 1);
					
					// search for the markup element using xpath
					xpathQuery = "//c:subject[@rdf:resource='" + subjectURI + "']/..";
					String markuptext = "";
					NodeList nlistMarkup = getNodesFromDoc(xpath, xpathQuery, contentDom);
					for (int j = 0; j < nlistMarkup.getLength(); j++) {
						Node typeMarkup = nlistMarkup.item(j);
						markuptext += DOMUtil.getChildText(XMLUtils.getNodeByName(typeMarkup, "c:prefix")) 
							+ " " + DOMUtil.getChildText(XMLUtils.getNodeByName(typeMarkup, "c:exact")) 
							+ " " + DOMUtil.getChildText(XMLUtils.getNodeByName(typeMarkup, "c:suffix"));
					}
					
					// create Type element for the ouput XML
					Node tempnode = company.getOwnerDocument().createElement("Type");
					tempnode.appendChild(company.getOwnerDocument().createTextNode(rdfType));
					eventNode.appendChild(tempnode);
					
					// create Markup element for the ouput XML
					tempnode = company.getOwnerDocument().createElement("Markup");
					tempnode.appendChild(company.getOwnerDocument().createTextNode(markuptext));
					eventNode.appendChild(tempnode);
					
					eventsAndFacts.appendChild(eventNode);
				}
			}
		}
		
		if(eventsAndFacts.getChildNodes().getLength() > 0)
			company.appendChild(eventsAndFacts);		
	}

	/**
	 * Use "Linked Data" to add nodes with information to the simple XML format "Geograghies" section
	 * 
	 * @param companiesNodes simple XML format "Geograghies" section 
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws AnalyzerException 	A throwable wrapper class to easy the error handling
	 */
	private void enrichGeo(Node placesNodes) throws AnalyzerException {
		for (int i = 0; i < placesNodes.getChildNodes().getLength(); i++) {
			Node place = placesNodes.getChildNodes().item(i);
			
			Node resourceNode = place.getChildNodes().item(2);
			String resourceUri = DOMUtil.getChildText(resourceNode);
			resourceUri = resourceUri.substring(1, resourceUri.length() - 1);
			
			Node newnode = place.getOwnerDocument().createElement("url");
					
			newnode.appendChild(place.getOwnerDocument().createTextNode(resourceUri));		
			place.appendChild(newnode);			
			place.removeChild(resourceNode);
			
			Node subjectNode = XMLUtils.getNodeByName(place, "subject");
			place.removeChild(subjectNode);
			
			Document linkedData = OpenCalais.getCalaisRdf(resourceUri + ".rdf");
			if(linkedData!=null) {			
				XPath xpath = XMLUtils.getXPath(linkedData); 
				if(linkedData.getDocumentElement().getAttributes().getNamedItem("xmlns:foaf") != null)
					enrichFromLinked(xpath, linkedData, place, "//foaf:homepage", null, "Homepage");
				
				if(linkedData.getDocumentElement().getAttributes().getNamedItem("xmlns:geo") != null) {
					enrichFromLinked(xpath, linkedData, place, "//geo:lat", null, "Latitude");
					enrichFromLinked(xpath, linkedData, place, "//geo:long", null, "Longitude");
				}
				
				if(linkedData.getDocumentElement().getAttributes().getNamedItem("xmlns:owl") != null)
					enrichFromLinked(xpath, linkedData, place, "//owl:sameAs", "RdfLinks", "Link");
				if(linkedData.getDocumentElement().getAttributes().getNamedItem("xmlns:foaf") != null)
					enrichFromLinked(xpath, linkedData, place, "//foaf:page", "WebLinks", "Link");
			}
		}
	}
	
	/**
	 * Use "Linked Data" to add nodes with information to the simple XML format "Products" section
	 * 
	 * @param companiesNodes simple XML format "Products" section 
	 * @throws AnalyzerException 	A throwable wrapper class to easy the error handling
	 */
	private void enrichProduct(Node productsNodes) throws AnalyzerException {
		for (int i = 0; i < productsNodes.getChildNodes().getLength(); i++) {
			Node product = productsNodes.getChildNodes().item(i);

			Node resourceNode = product.getChildNodes().item(2);
			String resourceUri = DOMUtil.getChildText(resourceNode);
			resourceUri = resourceUri.substring(1, resourceUri.length() - 1);
			
			Node newnode = product.getOwnerDocument().createElement("url");
					
			newnode.appendChild(product.getOwnerDocument().createTextNode(resourceUri));		
			product.appendChild(newnode);			
			product.removeChild(resourceNode);
			
			Node subjectNode = XMLUtils.getNodeByName(product, "subject");
			product.removeChild(subjectNode);
			
			Document linkedData = OpenCalais.getCalaisRdf(resourceUri + ".rdf");
			if(linkedData!=null) {			
				XPath xpath = XMLUtils.getXPath(linkedData); 
				if(linkedData.getDocumentElement().getAttributes().getNamedItem("xmlns:foaf") != null) {
					enrichFromLinked(xpath, linkedData, product, "//foaf:homepage", null, "Homepage");
					enrichFromLinked(xpath, linkedData, product, "//foaf:img", null, "Image");
				}
				
				if(linkedData.getDocumentElement().getAttributes().getNamedItem("xmlns:owl") != null)
					enrichFromLinked(xpath, linkedData, product, "//owl:sameAs", "RdfLinks", "Link");
				if(linkedData.getDocumentElement().getAttributes().getNamedItem("xmlns:foaf") != null){
					enrichFromLinked(xpath, linkedData, product, "//foaf:page", "WebLinks", "Link");					
				}
			}
		}
	}
	
	/**
	 * Use "Linked Data" document retrieved for RDF Resource to add elemants and information to simple XML output
	 * 	
	 * @param xpath			represents which data to add to XML output.
	 * @param linkedData	linked document of a resolved entity.
	 * @param enrichMe		XML Node to add information to (on XML output).
	 * @param rNodeNames	nodes on the linked data document to extract. 
	 * @param section		section on XML output.
	 * @param entName		data node to fill with data extracted from linked data.
	 * @throws AnalyzerException	xpathString provided in order to analyze XML was wrong empty or contained an error 
	 */
	private void enrichFromLinked(XPath xpath, Document linkedData, Node enrichMe, String rNodeNames, String section, String entName) throws AnalyzerException {
		Node sectionNode;
		if(section == null) {
			sectionNode = enrichMe;
		}
		else {			
			sectionNode = enrichMe.getOwnerDocument().createElement(section);
			enrichMe.appendChild(sectionNode);
		}
		NodeList nlist;
		nlist = getNodesFromDoc(xpath, rNodeNames, linkedData);
		
		for (int j = 0; j < nlist.getLength(); j++) {
			Node rcompetitor = nlist.item(j);
			Node competitor = enrichMe.getOwnerDocument().createElement(entName);
			
			String xText;
			xText = DOMUtil.getChildText(rcompetitor);
			if(rcompetitor.getAttributes().getNamedItem("rdf:resource") != null)
				xText = rcompetitor.getAttributes().getNamedItem("rdf:resource").getNodeValue();
			competitor.appendChild(enrichMe.getOwnerDocument().createTextNode(xText));
			
			sectionNode.appendChild(competitor);
		}
	}
	
	/**
	 * evaluate / extract data action. search and return nodes relevant for the action.
	 * 
	 * @param xpath			XPath object used for the linked data.
	 * @param xpathString	represents the nodes need to get data from. 
	 * @param linkedData	linked data document.
	 * @return				Node list with data to extract.
	 * @throws AnalyzerException   xpathString provided in order to analyze XML was wrong empty or contained an error
	 */
	private NodeList getNodesFromDoc(XPath xpath, String xpathString, Document linkedData) throws AnalyzerException {
		try {
			XPathExpression expr = xpath.compile(xpathString);
			return (NodeList) expr.evaluate(linkedData, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			throw new AnalyzerException("(EE) xpathString provided in order to " +
					"analyze XML was wrong empty " +
					"or contained an error ("+xpathString+")", e);
		}
	}

	/**
	 * Convert Jena's ResultSet to simple XML output 
	 * 
	 * @param paNode
	 * @param resultName
	 * @param resultSet
	 */
	private void builldXml(Node paNode, String resultName,
			ResultSet resultSet) {
		ResultSetRewindable resultSetRewindable = new ResultSetMem(resultSet);
		int numCols = resultSetRewindable.getResultVars().size();

		while (resultSetRewindable.hasNext()) {
			Node resultNode = paNode;
			if(resultName != null && !resultName.equals(""))
				resultNode = paNode.appendChild(paNode.getOwnerDocument()
					.createElement(resultName));

			QuerySolution rBind = resultSetRewindable.nextSolution();
			for (int col = 0; col < numCols; col++) {				
				String colName = (String) resultSet.getResultVars().get(col);
				String colValue = XMLUtils.getVarValueAsString(rBind, colName);
				
				resultNode.appendChild(paNode.getOwnerDocument().createElement(colName));				
				resultNode.getLastChild().appendChild(paNode.getOwnerDocument().createTextNode(colValue));
			}
		}

	}
	
	/**
	 * submit and RDF Query (Sparql) to RDF.
	 * 
	 * @param file			file name contains the sparql query
	 * @param content		RDF
	 * @param parameterValue 
	 * @param parameterName 
	 * @return 				Result set (Collection of solution)
	 * @throws AnalyzerException	Could not read the file
	 */
	private ResultSet getSparqlOutput(String file, String content) throws AnalyzerException {
		return getSparqlOutput(file, content, null, null);
	}

	/**
	 * submit and RDF Query (Sparql) to RDF.
	 * 
	 * @param file			file name contains the sparql query
	 * @param content		RDF
	 * @param parameterValue 
	 * @param parameterName 
	 * @return 				Result set (Collection of solution)
	 * @throws AnalyzerException	Could not read the file
	 */
	private ResultSet getSparqlOutput(String file, String content, String parameterName, String parameterValue) throws AnalyzerException {
		Reader reader = new StringReader(content);
		
		// Create an empty in-memory model and populate it from the graph
		Model model = ModelFactory.createMemModelMaker().createDefaultModel();
		model.read(reader, null); // null base URI, since model URIs are
		// absolute
		try {
			reader.close();
		} catch (IOException e) {
			throw new AnalyzerException("(EE) could not close input reader", e);
		}

		// Create a new query
		String queryString = getSparqlQuery(file);
		
		if(parameterName != null && parameterValue != null){
			queryString = StringUtils.replace(queryString, parameterName, parameterValue);
		}

		com.hp.hpl.jena.query.Query query = QueryFactory.create(queryString);

		// Execute the query and obtain results
		QueryExecution qe = QueryExecutionFactory.create(query, model);
		ResultSet results = qe.execSelect();

		// Output query results | Use as Debug option in order to see a text format of a table
		// ResultSetFormatter.out(System.out, results, query);

		// Important - free up resources used running the query
		qe.close();

		return results;
	}

	/**
	 * Get the file from file system and return its string contents
	 * 
	 * @param filePath
	 * @return
	 * @throws AnalyzerException	Could not read the file
	 */
	private String readfile(String filePath) throws AnalyzerException {
		BufferedReader in = null;
		StringBuffer out = null;
		try {
			in = new BufferedReader(new FileReader(filePath));
			out = new StringBuffer();
			String line;

			while ((line = in.readLine()) != null) {
				out.append(line);
				out.append("\r\n");
			}
		} catch (IOException e) {
			throw new AnalyzerException("(EE) Could read text file (" + filePath + ")", e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					System.out.println("(WW) could not close input reader for " + filePath);
				}
			}
		}

		return out.toString();
	}

	/**
	 * get RDF content from Open Calais API
	 * 
	 * @param key		Open Calais API license key 
	 * @param content	Text content to analyze
	 * @return
	 * @throws AnalyzerException	A throwable wrapper class to easy the error handling
	 */
	private String getTextFromOpenCalais(String key, String content) throws AnalyzerException {
		return OpenCalais.getCalaisRdfText(key, content);
	}

	/**
	 * get Sparql query to from resource
	 * 
	 * @param filename
	 * @return
	 * @throws AnalyzerException	A throwable wrapper class to easy the error handling
	 */
	private String getSparqlQuery(String filename) throws AnalyzerException {
		return StringUtils.getFileContentsFromResource("/resources/" + filename);
	}

	
}

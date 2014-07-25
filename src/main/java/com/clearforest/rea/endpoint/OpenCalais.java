package com.clearforest.rea.endpoint;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.clearforest.rea.exception.AnalyzerException;
import com.clearforest.rea.util.Configuration;
import com.clearforest.rea.util.StringUtils;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

public class OpenCalais {
	private final static StringBuilder strParams = new StringBuilder();
	
	static {
		strParams.append("<c:params xmlns:c=\"http://s.opencalais.com/1/pred/\"");
		strParams.append(" xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">");
		strParams.append("<c:processingDirectives c:contentType=\"${CONTENT_TYPE}\" c:outputFormat=\"${OUTPUT_FORMAT}\"></c:processingDirectives>");
		strParams.append("<c:userDirectives c:allowDistribution=\"true\" c:allowSearch=\"true\" c:externalID=\"17cabs901\" c:submitter=\"${SUBMITTER}\"></c:userDirectives>");
		strParams.append("<c:externalMetadata c:caller=\"SemanticProxy\"/>");
		strParams.append("</c:params>");
	}
	
	/**
	 * Create a web request and submit an OpenClais API Call on a REST web service.
	 * return output as XML DOM Document
	 * 
	 * @param key		Calais API License
	 * @param content	Text document content
	 * @return			XML DOM Document
	 * @throws AnalyzerException 	A throwable wrapper class to easy the error handling
	 */
	public static Document getCalaisRdf(String key, String content)
			throws AnalyzerException {
		return stringToDom(getCalaisRdfText(key, content));
	}

	/**
	 * Create a web request and submit an OpenClais API Call on a REST web service.
	 * 
	 * @param key		Calais API License
	 * @param content	Text document content
	 * @return			String body of XML output
	 * @throws AnalyzerException 	A throwable wrapper class to easy the error handling
	 */
	public static String getCalaisRdfText(String key, String content)
			throws AnalyzerException {
		try {
			ClientConfig clientConfig = new DefaultClientConfig();
			Client client = Client.create(clientConfig);
	
			System.out.println("Submit Calais API Call ... ");
			String body = "licenseID=" + URLEncoder.encode(key, "UTF-8")
					+ "&content=" + URLEncoder.encode(content, "UTF-8")
					+ "&paramsXML=" + URLEncoder.encode(getParamsXml(), "UTF-8");
	
			WebResource webResource = client
					.resource(Configuration.getCurrent().getCalaisRestUri());
			webResource.accept(new String[] { "application/xml" });
	
			// body is a hard-coded string, with replacements for the variable bits
			ClientResponse response = webResource.post(ClientResponse.class, body);
			
			if(response.getStatus() == 200)
				return response.getEntity(String.class);
			
			System.out.println(StringUtils.replace(
				Configuration.getCurrent().getHttpStatusWarningMessage()
				, "$status", response.getStatus()));
			System.out.println(response.getEntity(String.class));
		} catch (UnsupportedEncodingException e) {
			throw new AnalyzerException("(EE) Error encoding URL", e);
		} catch (IOException e) {
			throw new AnalyzerException("(EE) Could not parse string into XML output", e);
		}		
		return null;
	}
	
	/**
	 * Create a web request and submit an OpenClais API Call on a REST web service.
	 * return output as XML DOM Document
	 * 
	 * @param url
	 * @return
	 * @throws AnalyzerException 	A throwable wrapper class to easy the error handling
	 */
	public static Document getCalaisRdf(String url) throws AnalyzerException {
		return stringToDom(getCalaisRdfText(url));
	}

	/**
	 * return simple http "GET" request output
	 * 
	 * @param 				url of the "GET" request
	 * @return  			output body as String  
	 * @throws AnalyzerException	A throwable wrapper class to easy the error handling
	 */
	public static String getCalaisRdfText(String url) throws AnalyzerException {
		ClientConfig clientConfig = new DefaultClientConfig();
		Client client = Client.create(clientConfig);
		
		System.out.println("Submit Linked Data Call (" + url + ") ... ");
		
		WebResource webResource = client.resource(url);
		webResource.accept(new String[] { "application/xml" });

		// body is a hard-coded string, with replacements for the variable bits
		ClientResponse response = webResource.get(ClientResponse.class);
		
		if(response.getStatus() == 200)
			return response.getEntity(String.class);
		
		System.out.println(StringUtils.replace(
				Configuration.getCurrent().getHttpStatusWarningMessage()
				, "$status", response.getStatus()));
		System.out.println(response.getEntity(String.class));
		return null;
	}

	

	/**
	 * Build the parameters XML for Open Calais API Report
	 * 
	 * @return
	 * @throws AnalyzerException	a throwable wrapper class to easy the error handling
	 */
	private static String getParamsXml() throws AnalyzerException {
		String params = strParams.toString();

		params = StringUtils.replace(params, "${CONTENT_TYPE}", 
					Configuration.getCurrent().getCalaisContentType());
		params = StringUtils.replace(params, "${OUTPUT_FORMAT}", 
					Configuration.getCurrent().getCalaisOutputFormat());
		params = StringUtils.replace(params, "${SUBMITTER}", 
				Configuration.getCurrent().getCalaisSubmitter());
		
		return params;
	}

	/**
	 * Build a XML DOM Document object from string file 
	 * 
	 * @return
	 * @throws AnalyzerException	A throwable wrapper class to easy the error handling
	 */
	public static Document stringToDom(String xmlSource) throws AnalyzerException {		
		if(xmlSource == null)
			return null;
		
		try {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		return builder.parse(new InputSource(new StringReader(xmlSource)));
		} catch (ParserConfigurationException e) {			
			throw new AnalyzerException("(EE) Could not create a document builder due to configuration error", e);			
		} catch (SAXException e) {
			throw new AnalyzerException("(EE) Could not parse string into XML output", e);
		} catch (IOException e) {
			throw new AnalyzerException("(EE) Could not parse string into XML output", e);
		}
	}

}

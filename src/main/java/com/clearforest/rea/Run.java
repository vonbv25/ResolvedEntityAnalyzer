package com.clearforest.rea;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.xml.sax.SAXException;

import com.clearforest.rea.exception.AnalyzerException;
import com.clearforest.rea.util.StringUtils;
public class Run {

	/**
	 * @param args
	 */
	public static void main(String[] args) {		
		// extract parameter

		if(args.length == 2) {
			// analyze
			System.out.println("Starting ");
			if(DocumentAnalyzer.analyze(args[0], args[1]))
				System.out.println("Process Succeded..");
			else
				System.out.println("FAIL!");
		}
		else {
			try {
				System.out.println(StringUtils.getFileContentsFromResource("/resources/README"));
			} catch (AnalyzerException ae) {
				Throwable e = null;
				ae.printStackTrace(System.out);
				e = ae.getCause();
				// print Exceptions and their causes			
				while(e != null) {
					ae.printStackTrace(System.out);
				}
			}			
		}
	}
}

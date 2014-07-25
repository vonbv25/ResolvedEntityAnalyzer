package com.clearforest.rea.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.clearforest.rea.DocumentAnalyzer;
import com.clearforest.rea.exception.AnalyzerException;

public class StringUtils {
	public static String replace(String text, String repl, Object with) {
		int max = 1;
		StringBuffer buf = new StringBuffer(text.length());
		int start = 0, end = 0;
		while ((end = text.indexOf(repl, start)) != -1) {
			buf.append(text.substring(start, end)).append(with);
			start = end + repl.length();

			if (--max == 0) {
				break;
			}
		}
		buf.append(text.substring(start));
		return buf.toString();
	}
	
	public static String getFileContentsFromResource(String filename) throws AnalyzerException {
		try {		
			return getContents(DocumentAnalyzer.class.getResourceAsStream(filename));
		} catch (NullPointerException e) {
			throw new AnalyzerException("(EE) Read file error / Could not find file " + filename, e);
		}
	}
	
	/**
	 * Fetch the entire contents of a text file, and return it in a String. This
	 * style of implementation does not throw Exceptions to the caller.
	 * 
	 * @param inputFile
	 *            is a file which already exists and can be read.
	 * @throws AnalyzerException 
	 */
	private static String getContents(InputStream inputFile) throws AnalyzerException {
		/*
         * To convert the InputStream to String we use the BufferedReader.readLine()
         * method. We iterate until the BufferedReader return null which means
         * there's no more data to read. Each line will appended to a StringBuilder
         * and returned as String.
         */
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputFile));
        StringBuilder sb = new StringBuilder();
 
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
        	throw new AnalyzerException("(EE) Read file error / Could not find file ", e);
        } finally {
            try {
            	inputFile.close();
            } catch (IOException e) {
            	System.out.println("(WW) Could not parse string into XML output");
            }
        }
 
        return sb.toString();
	}
}

package com.clearforest.rea.util;

import java.util.Properties;
import java.util.regex.Pattern;

import com.clearforest.rea.exception.AnalyzerException;

public class Configuration {
	private static Configuration current = null;
	
	private Properties properties = null;
	
	private Configuration() throws AnalyzerException {
		try {	
			properties = new Properties();		
			this.properties.load(Configuration.class.getResourceAsStream("/resources/rea.properties"));			
		} catch (Exception e) {
			throw new AnalyzerException("(EE) Read file error / Could not find file /resources/rea.propertie", e);
		}
	}

	private static void setCurrent(Configuration current) {
		Configuration.current = current;
	}

	public static Configuration getCurrent() throws AnalyzerException {
		if(current == null)
			setCurrent(new Configuration());
		return current;
	}
	
	public String get(String key){		
		return this.properties.getProperty(key);
	}
	
	public int getMaxDocsize() {
		String max = this.get("maxdocsize");
		if(max != null && !max.equals("") 
				&& Pattern.matches("\\d+", max))
			return Integer.parseInt(max);
		return 0;
	}

	public String getOutputFileName() {
		return this.get("output.filename");
	}

	public String getCalaisRestUri() {
		return this.get("calais.rest.uri");
	}

	public String getHttpStatusWarningMessage() {
		return this.get("httpstatus.warning");
	}

	public Object getCalaisContentType() {
		return this.get("opencalais.contenttype");
	}

	public Object getCalaisOutputFormat() {
		return this.get("opencalais.outputformat");
	}

	public Object getCalaisSubmitter() {
		return this.get("opencalais.submitter");
	}
}

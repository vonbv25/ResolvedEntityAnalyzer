package com.clearforest.rea;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.NamespaceContext;

public class MyNamespaceContextImpl implements NamespaceContext {

	private Map map;

	public MyNamespaceContextImpl() {
		map = new HashMap();
	}

	public void setNamespace(String prefix, String namespaceURI) {
		map.put(prefix, namespaceURI);
	}

	public String getNamespaceURI(String prefix) {
		return (String) map.get(prefix);
	}

	public String getPrefix(String namespaceURI) {
		Set keys = map.keySet();
		for (Iterator iterator = keys.iterator(); iterator.hasNext();) {
			String prefix = (String) iterator.next();
			String uri = (String) map.get(prefix);
			if (uri.equals(namespaceURI))
				return prefix;
		}
		return null;
	}

	public Iterator getPrefixes(String namespaceURI) {
		List prefixes = new ArrayList();
		Set keys = map.keySet();
		for (Iterator iterator = keys.iterator(); iterator.hasNext();) {
			String prefix = (String) iterator.next();
			String uri = (String) map.get(prefix);
			if (uri.equals(namespaceURI))
				prefixes.add(prefix);
		}

		return prefixes.iterator();
	}
}
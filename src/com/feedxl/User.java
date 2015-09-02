package com.feedxl;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {

	private Map<String, String> attributes;

	public User(List<Map<String, String>> attributes) {
		this.attributes = new HashMap<String, String>();
		for (Map<String,String> attribute : attributes) {
			String key = attribute.get("name");
			String value = attribute.get("value");
			this.attributes.put(key, value);
		}
	}

	public String getEmail() {
		return attributes.get("email");
	}

	public void merge(User user) {
		for (String key : user.attributes.keySet()) {
			String value1 = this.attributes.get(key);
			String value2 = user.attributes.get(key);
			if (value1 != null && value2 != null && !value1.toLowerCase().equals(value2.toLowerCase())) {
				String newValue = value1 + "\n" + value2;
				this.attributes.put(key, newValue);
			}
		}
	}

	public String getTime() {
		return attributes.get("time");
	}

	public boolean isContactable() {
		return attributes.containsKey("contact") && Boolean.parseBoolean(attributes.get("contact"));
	}
	
	public String asCsv() {
		return "\""+attributes.get("country")+"\",\""+attributes.get("email")+"\",\""+attributes.get("name")+"\",\""+isContactable()+"\"";
	}

}

package com.example.beans;

import java.util.ArrayList;

public class NameConverterBean {
	
	public String convert(ArrayList<String> as) {
		StringBuilder sb = new StringBuilder() ;
		for(String s : as) {
			sb.append(s.charAt(0));
		}
		return sb.toString() ;
	}

}

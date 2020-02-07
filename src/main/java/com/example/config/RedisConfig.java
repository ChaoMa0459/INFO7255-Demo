package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.beans.JedisBean;
import com.example.beans.MyJsonValidator;
import com.example.beans.NameConverterBean;

@Configuration
public class RedisConfig {

	@Bean("validator")
	public MyJsonValidator myJsonValidator() {
		return new MyJsonValidator() ;
	}
	
	@Bean("jedisBean")
	public JedisBean jedisBean() {
		return new JedisBean() ;
	}
	
}

package com.example.beans;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
// import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.CacheControl;
import org.springframework.util.DigestUtils;

import io.lettuce.core.RedisException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

public class JedisBean {

	private static final String redisHost = "localhost";
	private static final Integer redisPort = 6379;
	//the jedis connection pool..
	private static JedisPool pool = null;
	
	private final Logger LOG = LoggerFactory.getLogger(getClass());
	
	public JedisBean() {
		pool = new JedisPool(redisHost, redisPort);
	}
	
	public String add(JSONObject jsonObject) {
		try {
			String idOne = (String)jsonObject.get("objectId");
			if(!doesKeyExist(idOne) && addHelper(jsonObject, idOne.toString()))
				return idOne;
			else
				return null;
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private boolean addHelper(JSONObject jsonObject, String objectId) {
		
		try {
			//get a jedis connection jedis connection pool
			Jedis jedis = pool.getResource();
			
			for(Object key : jsonObject.keySet()) {
				String attributeKey = objectId + "_" + String.valueOf(key);
				Object attributeVal = jsonObject.get(String.valueOf(key));
				
				if(attributeVal instanceof JSONObject) {
					Map<String,String> map = handleObjectAsMap((JSONObject) attributeVal);
					jedis.hmset(attributeKey, map);
				} else if (attributeVal instanceof JSONArray) {
					Set<String> set  = handleObjectAsArray((JSONArray) attributeVal);
					for(String v : set)
						jedis.sadd(attributeKey, v);
				} else {
					jedis.set(attributeKey, String.valueOf(attributeVal));
				}
			}
			jedis.sadd("MyApplicationKeys", objectId);
			jedis.close();
		}
		catch(JedisException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private Map<String,String> handleObjectAsMap(JSONObject jsonObject) {
		Map<String,String> map = new HashMap<String, String>();
		for(String key : jsonObject.keySet()) {
			map.put(key, jsonObject.get(key).toString());
		}
		return map;
	}
	
	private Set<String> handleObjectAsArray(JSONArray jsonArray) {
		Set<String> set = new HashSet<String>();
		for(Object o : jsonArray) {
			JSONObject ob = (JSONObject) o;
			set.add(ob.toString());
		}
		return set;
	}
	
	public boolean delete(String objectId) {
		try {
			Jedis jedis = pool.getResource();
			Set<String> keys = jedis.keys(objectId + "*");
			for(String key : keys) {
				jedis.del(key);
			}
			jedis.srem("MyApplicationKeys", objectId);
			jedis.close();
			return true;
		} catch(JedisException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public String getFromDB(String objectId) {		
			
		// System.out.printf("Getting object {} from database.\n", objectId);
		LOG.info("Getting object {} from database.", objectId);
		
		JSONObject jsonObject = getHelper(objectId);
		if(jsonObject != null) {
			return jsonObject.toString();
		} else {
			return null;
		}
	}
	
	private JSONObject getHelper(String objectId) {
		try {
			Jedis jedis = pool.getResource();
			JSONObject o = new JSONObject();
			// System.out.println("Reading keys from pattern");
			Set<String> keys = jedis.keys(objectId + "*");
//			for(String k : keys) {
//				System.out.println(k);
//			}
			for(String key : keys) {
				if(jedis.type(key).equalsIgnoreCase("set")) {
					JSONArray ja = new JSONArray();
					Set<String> set = jedis.smembers(key);
					for(String member : set) {
						ja.put(new JSONObject(member));
					}
					o.put(key.substring(objectId.length() + 1), ja);
				} else if (jedis.type(key).equalsIgnoreCase("hash")) {
					Map<String, String> map = jedis.hgetAll(key);
					JSONObject n = new JSONObject();
					for(String k : map.keySet()) {
						n.put(k, map.get(k));
					}
					o.put(key.substring(objectId.length() + 1), n);
				} else {
					o.put(key.substring(objectId.length() + 1), jedis.get(key));
				}
			}
			jedis.close();
			return o;
		} catch(RedisException e) {
			e.printStackTrace();
            return null;
		}
	}
	
	public boolean doesKeyExist(String objectId) {
		
		try {
			Jedis jedis = pool.getResource();
			if(jedis.sismember("MyApplicationKeys", objectId)) {
				jedis.close();
				return true;
			} else {
				jedis.close();
				return false;
			}
		} catch(JedisException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public boolean update(JSONObject jsonObject, String objectId) {
		try {
			Jedis jedis = pool.getResource();
			if( delete(objectId) && addHelper(jsonObject, objectId)) {
				jedis.close();
				return true;
			} else {
				jedis.close();
				return false;
			}
		} catch(JedisException e) {
			e.printStackTrace();
			return false;
		}
	}
	
}	


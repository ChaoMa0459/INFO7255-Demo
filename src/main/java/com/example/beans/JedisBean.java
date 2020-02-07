package com.example.beans;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

import io.lettuce.core.RedisException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

public class JedisBean {

	private static final String redisHost = "localhost";
	private static final Integer redisPort = 6379;
	private static JedisPool pool = null;
	
	public JedisBean() {
		pool = new JedisPool(redisHost, redisPort);
	}
	
	public UUID insert(JSONObject jsonObject) {
		UUID idOne = UUID.randomUUID();
		if(insertUtil(jsonObject, idOne.toString()))
			return idOne;
		else
			return null;
	}
	
	private boolean insertUtil(JSONObject jsonObject, String uuid) {
		
		try {
			Jedis jedis = pool.getResource();
			
			for(Object key : jsonObject.keySet()) {
				String attributeKey = uuid + "_" + String.valueOf(key);
				Object attributeVal = jsonObject.get(String.valueOf(key));
				
				if(attributeVal instanceof JSONObject) {
					Map<String,String> map = handleObjectAsMap( (JSONObject) attributeVal);
					jedis.hmset(attributeKey, map);
				} else if (attributeVal instanceof JSONArray) {
					Set<String> set  = handleObjectAsArray((JSONArray) attributeVal);
					for(String v : set)
						jedis.sadd(attributeKey, v);
				} else {
					jedis.set(attributeKey, String.valueOf(attributeVal));
				}
			}
			jedis.sadd("MyApplicationKeys", uuid);
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
	
	public boolean delete(String uuid) {
		try {
			Jedis jedis = pool.getResource();
			Set<String> keys = jedis.keys(uuid+"*");
			for(String key : keys) {
				jedis.del(key);
			}
			jedis.srem("MyApplicationKeys", uuid);
			jedis.close();
			return true;
		} catch(JedisException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public String read(String uuid) {
		
			
			System.out.println("Calling readutil");
			JSONObject jsonObject = readUtil(uuid);
			if(jsonObject != null)
				return jsonObject.toString();
			else
				return null;
	}
	
	private JSONObject readUtil(String uuid) {
		try {
			Jedis jedis = pool.getResource();
			JSONObject o = new JSONObject();
			System.out.println("Reading keys from pattern");
			Set<String> keys = jedis.keys(uuid+"*");
			for(String k : keys) {
				System.out.println(k);
			}
			for(String key : keys) {
				if(jedis.type(key).equalsIgnoreCase("set")) {
					JSONArray ja = new JSONArray();
					Set<String> set = jedis.smembers(key);
					for(String member : set) {
						ja.put(new JSONObject(member));
					}
					o.put(key.substring(uuid.length()+1), ja);
				} else if (jedis.type(key).equalsIgnoreCase("hash")) {
					Map<String, String> map = jedis.hgetAll(key);
					JSONObject n = new JSONObject();
					for(String k : map.keySet()) {
						n.put(k, map.get(k));
					}
					o.put(key.substring(uuid.length()+1), n);
				} else {
					o.put(key.substring(uuid.length()+1), jedis.get(key));
				}
			}
			jedis.close();
			return o;
		} catch(RedisException e) {
			e.printStackTrace();
            return null;
		}
	}
	
	public boolean doesKeyExist(String uuid) {
		
		try {
			Jedis jedis = pool.getResource();
			if(jedis.sismember("MyApplicationKeys", uuid)) {
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
	
	public boolean update(JSONObject jsonObject, String uuid) {
		try {
			Jedis jedis = pool.getResource();
			if( delete(uuid) && insertUtil(jsonObject, uuid)) {
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


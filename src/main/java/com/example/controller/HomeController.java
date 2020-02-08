package com.example.controller;

// import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.beans.JedisBean;
import com.example.beans.MyJsonValidator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.everit.json.schema.Schema;
import org.json.JSONObject;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;


@RestController
public class HomeController {
	
	@Autowired
	private MyJsonValidator validator;
	@Autowired
	private JedisBean jedisBean;
	private final Logger LOG = LoggerFactory.getLogger(getClass());
	// the map of eTags, key: objectId, value: eTag
	private Map<String, String> eTagMap = new HashMap<>();

	
	@RequestMapping("/")
	public String home() {
		return "Welcome to my INFO7255 demo!";
	}

	// to read json instance from redis or cache
	@GetMapping("/getplan/{objectId}")
	public ResponseEntity<String> getplan(@PathVariable(name="objectId", required=true) String objectId, @RequestHeader("eTag") String newETag) {		
		LOG.info("Getting object with ID {}.", objectId);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		String eTag = eTagMap.get(objectId);
		System.out.println("eTag: " + eTag);
		System.out.println("newETag: " + newETag);
		
		String jsonString = null;
		if (eTag != null && newETag != null && newETag != "" && newETag.equals(eTag)) {
			System.out.printf("Getting object {} from cache.\n", objectId);
			jsonString = jedisBean.getFromCache(objectId);
		} else {
			jsonString = jedisBean.getFromDB(objectId);
		}
		if (jsonString != null) {
			eTag = DigestUtils.md5DigestAsHex(jsonString.getBytes());
			// update eTag of this objectId
			eTagMap.put(objectId, eTag);
			// add eTag in response
			headers.add("eTag", eTag);
			return new ResponseEntity<String>(jsonString, headers, HttpStatus.OK);
		} else {
			return new ResponseEntity<String>("Read unsuccessfull", headers, HttpStatus.BAD_REQUEST);
		}
	}
	
	
	// to insert new json instance into redis
	@PostMapping("/addplan")
	public ResponseEntity<String> addplan(@RequestBody(required=true) String body) {
		LOG.info("Adding object.");
		
		Schema schema = validator.getSchema();
		if (schema == null)
			return new ResponseEntity<String>("Schema file is not found.", HttpStatus.BAD_REQUEST);
		
		JSONObject jsonObject = validator.getJsonObjectFromString(body);
		
		if (validator.validate(jsonObject)) {			
			String objectId = jedisBean.add(jsonObject);
			return new ResponseEntity<String>("objectId: " + objectId + " is inserted successfully.", HttpStatus.OK);
		} else {
			return new ResponseEntity<String>("Invalid JSON input against schema.", HttpStatus.BAD_REQUEST);
		}
			
	}
	
	
	// to delete json instance with key id from redis
	@DeleteMapping("/deleteplan/{objectId}")
	public ResponseEntity<String> deleteplan(@PathVariable(name="objectId", required=true) String objectId) {
		LOG.info("Deleting object with ID {}.", objectId);

		if (jedisBean.delete(objectId, eTagMap))
			return new ResponseEntity<String>(objectId + " is deleted successfully.", HttpStatus.OK);
		else
			return new ResponseEntity<String>("Deletion is unsuccessfull.", HttpStatus.BAD_REQUEST);
	}
	
	
	// to update Json instance with key id in Redis
	@PutMapping("/updateplan/{objectId}")
	public ResponseEntity<String> updateplan(@PathVariable(name="objectId", required=true) String objectId, @RequestBody(required=true) String body) {
		
		//if id does not exist
		if (!jedisBean.doesKeyExist(objectId))
			return addplan(body);
		
		// else
		Schema schema = validator.getSchema();
		if (schema == null)
			return new ResponseEntity<String>("schema file not found exception", HttpStatus.BAD_REQUEST);
		
		JSONObject jsonObject = validator.getJsonObjectFromString(body);
		
		if (!validator.validate(jsonObject))
			return new ResponseEntity<String>("Invalid JSON input against schema.", HttpStatus.BAD_REQUEST);
		
		if (!jedisBean.update(jsonObject, objectId, eTagMap))
			return new ResponseEntity<String>("Failed to update JSON instance in Redis", HttpStatus.BAD_REQUEST);
		
		return new ResponseEntity<String>("JSON instance updated in Redis", HttpStatus.OK);
	
	}


}

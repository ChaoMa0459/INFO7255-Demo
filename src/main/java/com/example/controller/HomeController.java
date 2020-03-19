package com.example.controller;

// import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import com.example.beans.JedisBean;
import com.example.beans.MyJsonValidator;

import org.everit.json.schema.Schema;
import org.json.JSONObject;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.text.ParseException;
import java.util.Date;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jwt.*;


@RestController
public class HomeController {
	
	@Autowired
	private MyJsonValidator validator;
	@Autowired
	private JedisBean jedisBean;
	private final Logger LOG = LoggerFactory.getLogger(getClass());
	
	private ShallowEtagHeaderFilter eTagFilter = new ShallowEtagHeaderFilter();
	private RSAKey rsaPublicJWK;
	// private JWSVerifier verifier;

	
	@RequestMapping("/")
	public String home() {
		return "Welcome to my INFO7255 demo!";
	}

	// to read json instance from redis or cache
	@GetMapping("/getplan/{objectId}")
	public ResponseEntity<String> getplan(@PathVariable(name="objectId", required=true) String objectId, @RequestHeader HttpHeaders requestHeaders) {		
		LOG.info("Getting object with ID {}.", objectId);
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		String res = null;
		try {
			if (!ifAuthorized(requestHeaders)) {
				res = "{\"status\": \"Failure\",\"message\": \"Unauthorized\"}";
				return new ResponseEntity<String>(res, headers, HttpStatus.BAD_REQUEST);
			}
			String jsonString = jedisBean.getFromDB(objectId);
//			System.out.println("jsonString: " + jsonString);

			if (jsonString != null) {
				res = "{\"status\": \"Success\",\"result\": " + jsonString + "}";
//				System.out.println("res: " + res);
				return new ResponseEntity<String>(res, headers, HttpStatus.OK);
			} else {
				res = "{\"status\": \"Failure\",\"message\": \"Read unsuccessfull\"}";
				return new ResponseEntity<String>(res, headers, HttpStatus.BAD_REQUEST);
			}
		} catch (Exception e) {
			e.printStackTrace();
			res = "{\"status\": \"Failure\",\"message\": \"Unauthorized\"}";
			return new ResponseEntity<String>(res, headers, HttpStatus.BAD_REQUEST);		
		}
	}
	
	
	// to insert new json instance into redis
	@PostMapping("/addplan")
	public ResponseEntity<String> addplan(@RequestBody(required=true) String body, @RequestHeader HttpHeaders requestHeaders) {
		LOG.info("Adding object.");
		
		Schema schema = validator.getSchema();
		if (schema == null)
			return new ResponseEntity<String>("Schema file is not found.", HttpStatus.BAD_REQUEST);
		
		JSONObject jsonObject = validator.getJsonObjectFromString(body);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		String res = null;
		try {
			if (!ifAuthorized(requestHeaders)) {
				res = "{\"status\": \"Failure\",\"message\": \"Unauthorized\"}";
				return new ResponseEntity<String>(res, headers, HttpStatus.BAD_REQUEST);
			}
			if (validator.validate(jsonObject)) {
				String objectId = jedisBean.add(jsonObject);
				if (objectId == null) {
					res = "{\"status\": \"Failure\",\"message\": \"objectId already exists.\"}";
					return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);				
				} else {
					
					// TODO
					// Don't do it like this. I can't find etag in other request methods
					String url = "http://localhost:8080/getplan/" + objectId;
			        RestTemplate restTemplate = new RestTemplate();  
			        HttpEntity<String> entity = new HttpEntity<String>("parameters", requestHeaders);     
			        ResponseEntity<String> result = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
			        HttpHeaders newHeaders = result.getHeaders();
			        System.out.println("newHeaders: " + newHeaders);
			        
					res = "{\"status\": \"Success\",\"message\": \"" + objectId + " is inserted successfully.\"}";
					return new ResponseEntity<String>(res, newHeaders, HttpStatus.OK);
				}
			} else {
				res = "{\"status\": \"Failure\",\"message\": \"Invalid JSON input against schema.\"}";
				return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
			}
		} catch (Exception e) {
			e.printStackTrace();
			res = "{\"status\": \"Failure\",\"message\": \"Unauthorized\"}";
			return new ResponseEntity<String>(res, headers, HttpStatus.BAD_REQUEST);		
		}
			
	}
	
	
	// to delete json instance with key id from redis
	@DeleteMapping("/deleteplan/{objectId}")
	public ResponseEntity<String> deleteplan(@PathVariable(name="objectId", required=true) String objectId, @RequestHeader HttpHeaders requestHeaders) {
		LOG.info("Deleting object with ID {}.", objectId);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		String res = null;
		try {
			if (!ifAuthorized(requestHeaders)) {
				res = "{\"status\": \"Failure\",\"message\": \"Unauthorized\"}";
				return new ResponseEntity<String>(res, headers, HttpStatus.BAD_REQUEST);
			}
			if (!jedisBean.doesKeyExist(objectId)) {
				res = "{\"status\": \"Failure\",\"message\": \"ObjectId does not exist.\"}";
				return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
			}
			if (jedisBean.delete(objectId)) {
				res = "{\"status\": \"Success\",\"message\": \"" + objectId + " is deleted successfully.\"}";
				return new ResponseEntity<String>(res, HttpStatus.OK);
			} else {
				res = "{\"status\": \"Failure\",\"message\": \"Deletion is unsuccessfull.\"}";
				return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
			}
		} catch (Exception e) {
			e.printStackTrace();
			res = "{\"status\": \"Failure\",\"message\": \"Unauthorized\"}";
			return new ResponseEntity<String>(res, headers, HttpStatus.BAD_REQUEST);		
		}
	}
	
	
	// to update Json instance with key id in Redis
	@PutMapping("/updateplan")
	public ResponseEntity<String> updateplan(@RequestBody(required=true) String body, @RequestHeader HttpHeaders requestHeaders) {
		
		// else
		Schema schema = validator.getSchema();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		String res = null;
		try {
			if (!ifAuthorized(requestHeaders)) {
				res = "{\"status\": \"Failure\",\"message\": \"Unauthorized\"}";
				return new ResponseEntity<String>(res, headers, HttpStatus.BAD_REQUEST);
			}
			if (schema == null) {
				res = "{\"status\": \"Failure\",\"message\": \"schema file not found exception\"}";
				return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
			}
			
			JSONObject jsonObject = validator.getJsonObjectFromString(body);
			
			if (!validator.validate(jsonObject)) {
				res = "{\"status\": \"Failure\",\"message\": \"Invalid JSON input against schema.\"}";
				return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
			}
			if (!jedisBean.update(jsonObject)) {
				res = "{\"status\": \"Failure\",\"message\": \"Failed to update JSON instance in Redis\"}";
				return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
			}
			
//			// TODO
//			// Don't do it like this. I can't find etag in other request methods
//	        String url = "http://localhost:8080/getplan/" + objectId;
//	        RestTemplate restTemplate = new RestTemplate();  
//	        HttpEntity<String> entity = new HttpEntity<String>("parameters", requestHeaders);     
//	        ResponseEntity<String> result = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
//	        HttpHeaders newHeaders = result.getHeaders();
//	        System.out.println("newHeaders: " + newHeaders);
	        
			res = "{\"status\": \"Success\",\"message\": \"JSON instance updated in Redis\"}";
			return new ResponseEntity<String>(res, headers, HttpStatus.OK);
		} catch (Exception e) {
			e.printStackTrace();
			res = "{\"status\": \"Failure\",\"message\": \"Unauthorized\"}";
			return new ResponseEntity<String>(res, headers, HttpStatus.BAD_REQUEST);		
		}	
	}

	
//	// to update Json instance with key id in Redis
//	@PatchMapping("/changeplan/{objectId}")
//	public ResponseEntity<String> changeplan(@PathVariable(name="objectId", required=true) String objectId, @RequestBody(required=true) String body, @RequestHeader HttpHeaders requestHeaders) {
//		
//		//if id does not exist
//		if (!jedisBean.doesKeyExist(objectId))
//			return addplan(body, requestHeaders);
//		
//		// else
//		Schema schema = validator.getSchema();
//		HttpHeaders headers = new HttpHeaders();
//		headers.setContentType(MediaType.APPLICATION_JSON);
//		
//		String res = null;
//		try {
//			if (!ifAuthorized(requestHeaders)) {
//				res = "{\"status\": \"Failure\",\"message\": \"Unauthorized\"}";
//				return new ResponseEntity<String>(res, headers, HttpStatus.BAD_REQUEST);
//			}
//			if (schema == null) {
//				res = "{\"status\": \"Failure\",\"message\": \"schema file not found exception\"}";
//				return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
//			}
//			
//			JSONObject jsonObject = validator.getJsonObjectFromString(body);
//			
//			if (!validator.validate(jsonObject)) {
//				res = "{\"status\": \"Failure\",\"message\": \"Invalid JSON input against schema.\"}";
//				return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
//			}
//			if (!jedisBean.update(jsonObject, objectId)) {
//				res = "{\"status\": \"Failure\",\"message\": \"Failed to update JSON instance in Redis\"}";
//				return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
//			}
//			
//			// TODO
//			// Don't do it like this. I can't find etag in other request methods
//	        String url = "http://localhost:8080/getplan/" + objectId;
//	        RestTemplate restTemplate = new RestTemplate();  
//	        HttpEntity<String> entity = new HttpEntity<String>("parameters", requestHeaders);     
//	        ResponseEntity<String> result = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
//	        HttpHeaders newHeaders = result.getHeaders();
//	        System.out.println("newHeaders: " + newHeaders);
//			
//			res = "{\"status\": \"Success\",\"message\": \"JSON instance updated in Redis\"}";
//			return new ResponseEntity<String>(res, newHeaders, HttpStatus.OK);
//		} catch (Exception e) {
//			e.printStackTrace();
//			res = "{\"status\": \"Failure\",\"message\": \"Unauthorized\"}";
//			return new ResponseEntity<String>(res, headers, HttpStatus.BAD_REQUEST);		
//		}	
//	}
	
	
	@GetMapping("/token")
	public ResponseEntity<String> getToken() throws JOSEException, ParseException {
		// RSA signatures require a public and private RSA key pair, the public key 
		// must be made known to the JWS recipient in order to verify the signatures
		RSAKey rsaJWK = new RSAKeyGenerator(2048).keyID("123").generate();
		rsaPublicJWK = rsaJWK.toPublicJWK();
		// verifier = new RSASSAVerifier(rsaPublicJWK);

		// Create RSA-signer with the private key
		JWSSigner signer = new RSASSASigner(rsaJWK);

		// Prepare JWT with claims set
		int expireTime = 30000; // seconds
		
		JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
		    .expirationTime(new Date(new Date().getTime() + expireTime * 1000)) // milliseconds
		    .build();

		SignedJWT signedJWT = new SignedJWT(
		    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJWK.getKeyID()).build(),
		    claimsSet);

		// Compute the RSA signature
		signedJWT.sign(signer);
		
		// To serialize to compact form, produces something like
		// eyJhbGciOiJSUzI1NiJ9.SW4gUlNBIHdlIHRydXN0IQ.IRMQENi4nJyp4er2L
		// mZq3ivwoAjqa1uUkSBKFIX7ATndFF5ivnt-m8uApHO4kfIFOrW7w2Ezmlg3Qd
		// maXlS9DhN0nUk_hGI3amEjkKd0BWYCB8vfUbUv0XGjQip78AI4z1PrFRNidm7
		// -jPDm5Iq0SZnjKjCNS5Q15fokXZc8u0A
		String token = signedJWT.serialize();
		
		String res = "{\"status\": \"Success\",\"token\": \"" + token + "\"}";
		return new ResponseEntity<String>(res, HttpStatus.OK);
	
	}
	
	private boolean ifAuthorized(HttpHeaders requestHeaders) throws ParseException, JOSEException {
		String token = requestHeaders.getFirst("Authorization").substring(7);
		// On the consumer side, parse the JWS and verify its RSA signature
		SignedJWT signedJWT = SignedJWT.parse(token);

		JWSVerifier verifier = new RSASSAVerifier(rsaPublicJWK);
		// Retrieve / verify the JWT claims according to the app requirements
		if (!signedJWT.verify(verifier)) {
			return false;
		}
		JWTClaimsSet claimset = signedJWT.getJWTClaimsSet();
		Date exp = 	claimset.getExpirationTime();
		
		// System.out.println(exp);		
		// System.out.println(new Date());
		
		return new Date().before(exp);
	}

	// TODO e-tag for PUT
	// TODO PATCH


}

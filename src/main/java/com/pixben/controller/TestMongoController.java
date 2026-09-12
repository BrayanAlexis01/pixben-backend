package com.pixben.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestMongoController {

    @Autowired
    private MongoTemplate mongoTemplate;

@GetMapping("/mongo-db")
public String db() {
    return "BD: " + mongoTemplate.getDb().getName();
    

} 
   @GetMapping("/mongo-info")
public String info() {
    return mongoTemplate.getMongoDatabaseFactory()
            .getMongoDatabase()
            .getName();
}
}
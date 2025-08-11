package com.encipherhealth.sip.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DbService {
    
    private final MongoTemplate mongoTemplate;

    public DbService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public <T> T save(T entity, String collectionName) {
        try {
            log.debug("Saving entity to collection: {}", collectionName);
            return mongoTemplate.save(entity, collectionName);
        } catch (Exception e) {
            log.error("Error saving entity to collection {}: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to save entity", e);
        }
    }

    public <T> T findOne(Query query, Class<T> entityClass, String collectionName) {
        try {
            log.debug("Finding one entity from collection: {}", collectionName);
            return mongoTemplate.findOne(query, entityClass, collectionName);
        } catch (Exception e) {
            log.error("Error finding entity from collection {}: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to find entity", e);
        }
    }

    public <T> List<T> find(Query query, Class<T> entityClass, String collectionName) {
        try {
            log.debug("Finding entities from collection: {}", collectionName);
            return mongoTemplate.find(query, entityClass, collectionName);
        } catch (Exception e) {
            log.error("Error finding entities from collection {}: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to find entities", e);
        }
    }

    public <T> void updateCollectionList(Query query, String field, Object value, Class<T> entityClass, String collectionName) {
        try {
            log.debug("Updating collection list in collection: {} for field: {}", collectionName, field);
            Update update = new Update().push(field, value);
            mongoTemplate.updateFirst(query, update, entityClass, collectionName);
        } catch (Exception e) {
            log.error("Error updating collection list in collection {} for field {}: {}", collectionName, field, e.getMessage(), e);
            throw new RuntimeException("Failed to update collection list", e);
        }
    }

    public <T> void delete(Query query, Class<T> entityClass, String collectionName) {
        try {
            log.debug("Deleting entity from collection: {}", collectionName);
            mongoTemplate.remove(query, entityClass, collectionName);
        } catch (Exception e) {
            log.error("Error deleting entity from collection {}: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to delete entity", e);
        }
    }
}

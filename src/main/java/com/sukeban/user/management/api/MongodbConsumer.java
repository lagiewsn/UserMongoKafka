package com.sukeban.user.management.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

public class MongodbConsumer extends Thread {

    private String dbName;
    private String topicName;
    private String groupName;
    private KafkaConsumer<String, String> kafkaConsumer;
    private String topicproducer;

    public MongodbConsumer() {
    }

    public MongodbConsumer(String dbName,
            String topicName,
            String groupName,
            String topicProducer
    ) {

        this.dbName = dbName;
        this.topicName = topicName;
        this.groupName = groupName;
        this.topicproducer = topicProducer;

    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getTopicproducer() {
        return topicproducer;
    }

    public void setTopicproducer(String topicproducer) {
        this.topicproducer = topicproducer;
    }

    @Override
    public void run() {

        DbQuery dbQuery = new DbQuery(this.dbName, this.topicproducer);
        ObjectMapper mapper = new ObjectMapper();

        Properties configProperties = new Properties();

        configProperties.
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        configProperties.
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                        "org.apache.kafka.common.serialization.StringDeserializer");

        configProperties.
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                        "org.apache.kafka.common.serialization.StringDeserializer");

        configProperties.
                put(ConsumerConfig.GROUP_ID_CONFIG, groupName);

        configProperties.
                put(ConsumerConfig.CLIENT_ID_CONFIG, "user-management");

        //Figure out where to start processing messages from
        kafkaConsumer = new KafkaConsumer<>(configProperties);
        kafkaConsumer.subscribe(Arrays.asList(topicName));
        //Start processing messages
        try {
            while (true) {
                ConsumerRecords<String, String> records = kafkaConsumer.poll(100);
                for (ConsumerRecord<String, String> record : records) {

                    User user = mapper.readValue(record.value(), User.class);
                    if (user.getCars() != null) {
                        Query<User> query = dbQuery.getDatastore().createQuery(User.class).field("lastName").contains(user.getLastName())
                                .field("firstName").contains(user.getFirstName());
                        UpdateOperations<User> ops = dbQuery.getDatastore().createUpdateOperations(User.class).addToSet("cars", user.getCars());

                        dbQuery.getDatastore().update(query, ops);
                    }

                }
            }
        } catch (WakeupException ex) {
            System.out.println("Exception caught " + ex.getMessage());
        } catch (IOException ex) {
            Logger.getLogger(MongodbConsumer.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            kafkaConsumer.close();
            System.out.println("After closing KafkaConsumer");
        }
    }
}

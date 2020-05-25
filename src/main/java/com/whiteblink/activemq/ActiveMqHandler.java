package com.whiteblink.activemq;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.json.JSONException;
import org.json.JSONObject;

import javax.jms.*;
import java.util.Collections;

/*
This class is used for:
1) Establishing connection to the ActiveMQ Instance
2) Creating a Queue using ActiveMQ
3) Adding Json objects to the queue
4) Removing Json objects from the queue
 */
public class ActiveMqHandler {

    final ActiveMQConnectionFactory connectionFactory;
    final Connection connection;
    final Session session;
    final Destination destination;
    final MessageProducer messageProducer;
    final MessageConsumer consumer;


    //Constructor used to create ActiveMq connection
    public ActiveMqHandler(String queueName) throws JMSException {
        String url = ActiveMQConnection.DEFAULT_BROKER_URL;
        connectionFactory = new ActiveMQConnectionFactory(url);
        connectionFactory.setTrustedPackages(Collections.singletonList("com.whiteblink.activemq"));
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        destination = session.createQueue(queueName);
        messageProducer = session.createProducer(destination);
        consumer = session.createConsumer(destination);

    }

    //Enqueue Json Objects
    public void messageSender(JSONObject jsonObject) throws JMSException {
        TextMessage message = session.createTextMessage(jsonObject.toString());
        messageProducer.send(message);
    }

    //Dequeue Json Objects
    public JSONObject messageReceiver() throws JMSException, JSONException {
        TextMessage textMessage = (TextMessage)consumer.receive();
        return new JSONObject(textMessage.getText());
    }

    //Closes ActiveMq Connection
    public void close() throws JMSException {
        connection.close();
    }
}

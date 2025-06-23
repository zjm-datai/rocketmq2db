package com.jmz.mq2data.controller;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jmz.mq2data.config.RocketMQConfig;
import com.jmz.mq2data.jms.Producer;

@RestController
public class TestController {
    @Autowired
    private Producer producer; 

    @GetMapping(value="/test")
    public Object test(String msg) throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        String str = "这是一条测试日志数据 = " + msg; 
        Message message = new Message(RocketMQConfig.TOPIC, "tag1", str.getBytes());
        SendResult sendResult = producer.getProducer().send(message); 

        return sendResult.toString();
    }
}

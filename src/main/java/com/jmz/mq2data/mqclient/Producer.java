package com.jmz.mq2data.mqclient;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.stereotype.Component;

import com.jmz.mq2data.config.RocketMQConfig;

@Component
public class Producer {
    private String producerGroup = "producer_group"; 

    private DefaultMQProducer producer; 

    public Producer(){
        producer = new DefaultMQProducer(producerGroup); 
        // 指定 nameserver 地址，多个地址使用，分隔
        producer.setNamesrvAddr(RocketMQConfig.NAME_SERVER);
        start();
    }

    public DefaultMQProducer getProducer(){
        return this.producer;
    }

    public void start(){
        try {
            this.producer.start();
        } catch (MQClientException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        this.producer.shutdown();
    }
}

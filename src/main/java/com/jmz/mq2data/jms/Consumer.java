package com.jmz.mq2data.jms;

import java.util.List;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jmz.mq2data.config.RocketMQConfig;

@Component
public class Consumer {

    private static final Logger log = LoggerFactory.getLogger(Consumer.class);

    private DefaultMQPushConsumer consumer;
    private String consumerGroup = "consumer_group";

    public Consumer() throws MQClientException {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(RocketMQConfig.NAME_SERVER);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_TIMESTAMP);
        consumer.setConsumeTimestamp("20240101000000");

        log.info("RocketMQ 消费者初始化中...");
        log.info("NameServer地址: {}", RocketMQConfig.NAME_SERVER);
        log.info("消费者组: {}", consumerGroup);

        String topic = RocketMQConfig.TOPIC;
        String tag = "*";
        consumer.subscribe(topic, tag);

        log.info("订阅的 Topic: {}，Tag: {}", topic, tag);

        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                log.info("接收到 {} 条消息", msgs.size());

                for (MessageExt message : msgs) {
                    try {
                        String body = new String(message.getBody(), "utf-8");

                        log.info("线程：{} 收到消息", Thread.currentThread().getName());
                        log.info("消息内容: {}", body);
                        log.info("消息详情 -> Topic: {}, Tags: {}, Keys: {}, MsgId: {}",
                                message.getTopic(),
                                message.getTags(),
                                message.getKeys(),
                                message.getMsgId());

                    } catch (Exception e) {
                        log.error("处理消息异常: ", e);
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }

                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        try {
            consumer.start();
            log.info("RocketMQ 消费者启动成功！");
            log.info("消费者客户端 IP: {}", consumer.getClientIP());
            log.info("实例名称: {}", consumer.getInstanceName());
        } catch (MQClientException e) {
            log.error("消费者启动失败：{}", e.getErrorMessage(), e);
            throw e;
        }
    }
}

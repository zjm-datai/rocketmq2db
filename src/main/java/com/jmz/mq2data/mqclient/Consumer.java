package com.jmz.mq2data.mqclient;


import java.nio.charset.StandardCharsets;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.jmz.mq2data.config.RocketMQConfig;
import com.jmz.mq2data.mapper.MqConsumeLogMapper;
import com.jmz.mq2data.model.MqConsumeLog;

@Component
public class Consumer {
    private static final Logger log = LoggerFactory.getLogger(Consumer.class);

    private DefaultMQPushConsumer consumer;
    // RocketMQ 推模式消费者对象

    private String consumerGroup = "apisix_group"; 

    @Autowired
    private MqConsumeLogMapper logMapper; 
    // 自动注入 MyBatis Mapper，用于向数据库插入消费日志

    public Consumer() throws MQClientException {
        // 构造方法，初始化并启动 RocketMQ 消费者，如果配置有误会抛出错误

        consumer = new DefaultMQPushConsumer(consumerGroup);
        // 创建一个 DefaultMQPushConsumer 实例，并指定消费组

        consumer.setNamesrvAddr(RocketMQConfig.NAME_SERVER);

        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_TIMESTAMP);
        // 指定消费起点为指定时间戳之后的消息

        consumer.setConsumeTimestamp("20240101000000");
        // 如果消费进度不存在，则从 2024-01-01 00:00:00 开始消费

        log.info("RocketMQ 消费者初始化中...");
        // 打印初始化日志

        log.info("NameServer地址: {}", RocketMQConfig.NAME_SERVER);
        // 打印 NameServer 地址

        log.info("消费者组: {}", consumerGroup);
        // 打印消费者组名称

        consumer.subscribe(RocketMQConfig.TOPIC, "*");
        // 订阅指定 Topic，并且 Tag 为全部（*）

        // 注册并发消息监听器
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(
                List<MessageExt> msgs, ConsumeConcurrentlyContext context 
            ) {
                // 重写收到消息后的回调方法，参数 msgs 是本次拉取收到消息列表
                for (MessageExt message : msgs) {
                    // 遍历每一条消息

                    String msgId = message.getMsgId();
                    // 获取消息唯一 ID
                    // 我们以此为凭据，防止重复消费

                    String body = new String(message.getBody(), StandardCharsets.UTF_8);
                    
                    try {
                        // 构建要入库的日志实体对象
                        MqConsumeLog logEntity = new MqConsumeLog();
                        logEntity.setMsgId(msgId);
                        logEntity.setTopic(message.getTopic());
                        logEntity.setTags(message.getTags());
                        logEntity.setMessageKeys(message.getKeys());
                        logEntity.setBody(body);

                        log.info("消息内容: {}", body);

                        // 调用 Mapper 插入数据库
                        // 如果 msg_id 重复会抛出 DuplicateKeyEception 
                        logMapper.insert(logEntity);

                        log.info("消息 [{}] 已写入数据库", msgId); 
                    } catch (DuplicateKeyException e) {
                        // 捕获到主键冲突异常，说明是重复消息
                        log.warn("检测到重复消息 [{}]，已跳过", msgId);
                        // 记录警告日志，并跳过，不触发重试 
                    } catch (Exception e) {
                        // 捕获其他任何异常（如数据库连接失败等）
                        log.error("写库异常，msgId={}，稍后重试", msgId, e);
                        // 记录错误日志，并返回 RECONSUME_LATER，通知 RocketMQ 稍后重试
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }

                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        
        try {
            consumer.start();
            // 启动消费端，连接到 NameServer 并开始拉取消息

            log.info("RocketMQ 消费者启动成功！");
            // 打印启动成功日志

        } catch (MQClientException e) {
            log.error("消费者启动失败：{}", e.getErrorMessage(), e);
            // 打印启动失败的错误日志
            throw e;
            // 将异常抛出，防止应用启动成功却无法正常消费
        }
    }
}

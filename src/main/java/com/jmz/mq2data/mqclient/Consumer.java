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
import org.springframework.util.StringUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.jmz.mq2data.config.RocketMQConfig;
import com.jmz.mq2data.mapper.MqConsumeLogMapper;
import com.jmz.mq2data.model.MqConsumeLog;

@Component
public class Consumer {
    private static final Logger log = LoggerFactory.getLogger(Consumer.class);

    private DefaultMQPushConsumer consumer;
    private String consumerGroup = "apisix_group";

    @Autowired
    private MqConsumeLogMapper logMapper;

    public Consumer() throws MQClientException {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(RocketMQConfig.NAME_SERVER);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_TIMESTAMP);
        consumer.setConsumeTimestamp("20240101000000");

        log.info("RocketMQ 消费者初始化中...");
        log.info("NameServer地址: {}", RocketMQConfig.NAME_SERVER);
        log.info("消费者组: {}", consumerGroup);

        consumer.subscribe(RocketMQConfig.TOPIC, "*");

        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(
                List<MessageExt> msgs, ConsumeConcurrentlyContext context
            ) {
                for (MessageExt message : msgs) {
                    String msgId = message.getMsgId();
                    String body = new String(message.getBody(), StandardCharsets.UTF_8);
                    
                    log.info("本次消费的消息为：{}", body);

                    try {
                        // 1) 先解析最外层 APISIX 日志，支持数组或对象
                        Object parsed = JSON.parse(body);
                        JSONObject logJson;
                        if (parsed instanceof JSONArray) {
                            JSONArray arr = (JSONArray) parsed;
                            if (arr.isEmpty()) {
                                log.warn("收到空数组日志，msgId={}", msgId);
                                continue;
                            }
                            logJson = arr.getJSONObject(0);
                        } else if (parsed instanceof JSONObject) {
                            logJson = (JSONObject) parsed;
                        } else {
                            log.error("日志格式不是 JSON 对象或数组，msgId={}, body={}", msgId, body);
                            continue;
                        }

                        // 2) 拆出路由 ID
                        String routeId = logJson.getString("route_id");

                        // 3) 拆出 APISIX response.body（它本身是字符串）
                        JSONObject responseJson = logJson.getJSONObject("response");
                        String responseBodyStr = responseJson != null
                            ? responseJson.getString("body")
                            : null;

                        // 4) 解析 OpenAI usage，可能为 null
                        Integer promptTokens = null, completionTokens = null, totalTokens = null;
                        // 只有当 responseBodyStr 不为空时才尝试解析
                        if (StringUtils.hasText(responseBodyStr)) {
                            try {
                                // 尝试将 response body 当作 JSON 解析
                                JSONObject aiJson = JSON.parseObject(responseBodyStr);
                                
                                // 确保解析结果不为null（例如，当responseBodyStr是"null"字符串时）
                                if (aiJson != null) {
                                    JSONObject usage = aiJson.getJSONObject("usage");
                                    if (usage != null) {
                                        promptTokens = usage.getInteger("prompt_tokens");
                                        completionTokens = usage.getInteger("completion_tokens");
                                        totalTokens = usage.getInteger("total_tokens");
                                    }
                                }
                            } catch (JSONException e) {
                                // 如果 response.body 不是一个有效的 JSON（例如，是一个 HTML 错误页面），
                                // 这不是一个需要重试的错误。我们只需记录下来，然后继续，让 token 字段保持 null。
                                log.warn("响应体不是有效的JSON格式，无法提取 usage 信息。msgId={}, responseBody='{}'", msgId, responseBodyStr);
                            }
                        }
                        // 5) 新增：提取 Authorization Token
                        String massAuthToken = null;
                        JSONObject requestJson = logJson.getJSONObject("request");
                        if (requestJson != null) {
                            JSONObject headers = requestJson.getJSONObject("headers");
                            if (headers != null) {
                                String authHeader = headers.getString("mass-auth-token");
                                if (authHeader != null) {
                                    // 去掉 "Bearer " 前缀（如果有）
                                    if (authHeader.toLowerCase().startsWith("bearer ")) {
                                        massAuthToken = authHeader.substring(7);
                                    } else {
                                        massAuthToken = authHeader;
                                    }
                                }
                            }
                        }

                        // 6) 填充实体并入库
                        MqConsumeLog logEntity = new MqConsumeLog();
                        logEntity.setMsgId(msgId);
                        logEntity.setTopic(message.getTopic());
                        logEntity.setTags(message.getTags());
                        logEntity.setMessageKeys(message.getKeys());
                        logEntity.setBody(body);
                        logEntity.setRouteId(routeId);
                        logEntity.setPromptTokens(promptTokens);
                        logEntity.setCompletionTokens(completionTokens);
                        logEntity.setTotalTokens(totalTokens);
                        logEntity.setMassAuthToken(massAuthToken);

                        logMapper.insert(logEntity);
                        log.info("消息 [{}] 已写入数据库", msgId);
                    } catch (DuplicateKeyException e) {
                        log.warn("检测到重复消息 [{}]，已跳过", msgId);
                    } catch (Exception e) {
                        log.error("写库异常，msgId={}，稍后重试", msgId, e);
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        try {
            consumer.start();
            log.info("RocketMQ 消费者启动成功！");
        } catch (MQClientException e) {
            log.error("消费者启动失败：{}", e.getErrorMessage(), e);
            throw e;
        }
    }
}

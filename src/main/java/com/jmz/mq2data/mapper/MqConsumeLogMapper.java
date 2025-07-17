package com.jmz.mq2data.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import com.jmz.mq2data.model.MqConsumeLog;

@Mapper
public interface MqConsumeLogMapper {
    @Insert("INSERT INTO mq_consume_log "
          + "(msg_id, mass_auth_token, topic, tags, message_keys, body, "
          + " route_id, prompt_tokens, completion_tokens, total_tokens) "
          + "VALUES (#{msgId}, #{massAuthToken}, #{topic}, #{tags}, #{messageKeys}, #{body}, "
          + "#{routeId}, #{promptTokens}, #{completionTokens}, #{totalTokens})")
    int insert(MqConsumeLog log);
}
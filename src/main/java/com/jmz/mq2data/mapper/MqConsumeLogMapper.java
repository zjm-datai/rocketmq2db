package com.jmz.mq2data.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import com.jmz.mq2data.model.MqConsumeLog;

@Mapper
public interface MqConsumeLogMapper {
    @Insert("INSERT INTO mq_consume_log " +
            "(msg_id, topic, tags, message_keys, body) " +
            "VALUES(#{msgId}, #{topic}, #{tags}, #{messageKeys}, #{body})")
    int insert(MqConsumeLog log);
}

package com.jmz.mq2data.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MqConsumeLog {
    private Long    id;
    private String massAuthToken;
    private String  msgId;
    private String  topic;
    private String  tags;
    private String  messageKeys;
    private String  body;
    private String  routeId;            
    private Integer promptTokens;       
    private Integer completionTokens;   
    private Integer totalTokens;        
    private LocalDateTime createTime;
}
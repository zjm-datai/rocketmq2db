package com.jmz.mq2data;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.jmz.mq2data.mapper")
public class Mq2dataApplication {

	public static void main(String[] args) {
		SpringApplication.run(Mq2dataApplication.class, args);
	}

}

package com.zhutao.medrms.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@ComponentScan(basePackages = {"com.zhutao.medrms"})
@MapperScan("com.zhutao.medrms.**.mapper")
@EnableScheduling
@RestController
@RequestMapping("/")
public class MedRmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedRmsApplication.class, args);
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
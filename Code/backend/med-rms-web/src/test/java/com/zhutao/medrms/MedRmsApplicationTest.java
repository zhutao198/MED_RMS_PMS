package com.zhutao.medrms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = com.zhutao.medrms.web.MedRmsApplication.class)
@ActiveProfiles("test")
class MedRmsApplicationTest {

    @Test
    void contextLoads() {
    }
}
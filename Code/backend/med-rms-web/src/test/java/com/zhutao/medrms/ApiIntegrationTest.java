package com.zhutao.medrms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.zhutao.medrms.web.MedRmsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper om = new ObjectMapper();

    private String loginAs(String user) throws Exception {
        String body = String.format("{\"username\":\"%s\",\"password\":\"admin123\"}", user);
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn();
        JsonNode data = om.readTree(r.getResponse().getContentAsString()).get("data");
        return data.get("token").asText();
    }

    @Test
    void testRequirementsEndpoint() throws Exception {
        String token = loginAs("admin");
        mockMvc.perform(get("/requirements").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}

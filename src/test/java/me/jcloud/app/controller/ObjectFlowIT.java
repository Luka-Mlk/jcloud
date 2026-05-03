package me.jcloud.app.controller;

import me.jcloud.app.model.Bucket;
import me.jcloud.app.repository.BucketRepository;
import me.jcloud.app.repository.FileMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest
class ObjectFlowIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private BucketRepository bucketRepository;

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        userId = UUID.randomUUID();
        fileMetadataRepository.deleteAll();
        bucketRepository.deleteAll();
    }

    @Test
    void testFullObjectFlow() throws Exception {
        String bucketName = "test-bucket";

        // 1. Create Bucket
        mockMvc.perform(post("/api/v1/buckets")
                        .param("name", bucketName)
                        .requestAttr("authenticatedUserId", userId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(bucketName));

        // 2. Upload Object (S3-style PutObject)
        mockMvc.perform(put("/api/v1/objects/" + bucketName + "/test.txt")
                        .content("Hello JCloud")
                        .contentType(MediaType.TEXT_PLAIN_VALUE)
                        .requestAttr("authenticatedUserId", userId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("test.txt"))
                .andExpect(jsonPath("$.bucket_name").value(bucketName));

        // 3. List Objects
        mockMvc.perform(get("/api/v1/objects/" + bucketName)
                        .requestAttr("authenticatedUserId", userId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].path").value("test.txt"));

        // 4. Download Object
        mockMvc.perform(get("/api/v1/objects/" + bucketName + "/test.txt")
                        .requestAttr("authenticatedUserId", userId))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello JCloud"));

        // 5. Delete Object
        mockMvc.perform(delete("/api/v1/objects/" + bucketName + "/test.txt")
                        .requestAttr("authenticatedUserId", userId))
                .andExpect(status().isNoContent());

        // 6. Verify Deleted
        mockMvc.perform(get("/api/v1/objects/" + bucketName + "/test.txt")
                        .requestAttr("authenticatedUserId", userId))
                .andExpect(status().isNotFound());
    }
}

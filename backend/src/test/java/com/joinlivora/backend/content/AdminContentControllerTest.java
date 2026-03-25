package com.joinlivora.backend.content;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminContentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContentService contentService;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AdminContentController controller;

    @BeforeEach
    void setup() {
        MappingJackson2HttpMessageConverter jacksonMessageConverter = new MappingJackson2HttpMessageConverter();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        jacksonMessageConverter.setObjectMapper(mapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(jacksonMessageConverter)
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void getContent_ShouldReturnPaginatedContent() throws Exception {
        UUID contentId = UUID.randomUUID();
        User creator = new User();
        creator.setEmail("creator@test.com");

        Content content = new Content();
        content.setId(contentId);
        content.setTitle("Test Content");
        content.setCreator(creator);
        content.setDisabled(false);
        content.setCreatedAt(Instant.now());

        Page<Content> page = new PageImpl<>(Collections.singletonList(content), PageRequest.of(0, 10), 1);

        when(contentRepository.findAllWithCreator(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/admin/content")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(contentId.toString()))
                .andExpect(jsonPath("$.content[0].title").value("Test Content"))
                .andExpect(jsonPath("$.content[0].creatorEmail").value("creator@test.com"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void getContent_WithDisabledContent_ShouldReturnDisabledStatus() throws Exception {
        UUID contentId = UUID.randomUUID();
        User creator = new User();
        creator.setEmail("creator@test.com");

        Content content = new Content();
        content.setId(contentId);
        content.setTitle("Disabled Content");
        content.setCreator(creator);
        content.setDisabled(true);
        content.setCreatedAt(Instant.now());

        Page<Content> page = new PageImpl<>(Collections.singletonList(content), PageRequest.of(0, 10), 1);

        when(contentRepository.findAllWithCreator(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/admin/content")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("DISABLED"));
    }
}

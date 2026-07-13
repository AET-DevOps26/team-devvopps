package com.tum.gateway.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.tum.gateway.security.JwtAuthFilter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;


/**
 * Integration tests for GatewayController.
 *
 * Boots the complete gateway application context and verifies:
 *
 * - Real Spring MVC routing
 * - Real GatewayController forwarding logic
 * - Real RestTemplate forwarding
 * - Downstream communication through WireMock
 *
 * Only downstream services are mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GatewayControllerIntegrationTest {

    private static final String SIGNING_KEY = "dev-only-insecure-key-change-me-0123456789";

    static WireMockServer wireMock =
            new WireMockServer(
                    com.github.tomakehurst.wiremock.core.WireMockConfiguration
                            .wireMockConfig()
                            .dynamicPort()
            );


    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {

        wireMock.start();

        registry.add(
                "services.user.url",
                () -> "http://localhost:" + wireMock.port()
        );

        registry.add(
                "services.course.url",
                () -> "http://localhost:" + wireMock.port()
        );

        registry.add(
                "services.roadmap.url",
                () -> "http://localhost:" + wireMock.port()
        );

        registry.add(
                "services.llm.url",
                () -> "http://localhost:" + wireMock.port()
        );

        registry.add("app.jwt.signing-key", () -> SIGNING_KEY);
    }


    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
    }

    @AfterEach
    void tearDown() {
        wireMock.resetAll();
    }


    // -------------------------------------------------------------------------
    // USER SERVICE ROUTING
    // -------------------------------------------------------------------------

    /**
     * Verifies /users/** is forwarded to user-service.
     */
    @Test
    void forwardUsers_returnsDownstreamResponse() throws Exception {
        wireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock
                .get(urlEqualTo("/users/1"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type",
                                                "application/json"
                                        )
                                        .withBody("""
                                                {
                                                  "user_id":1,
                                                  "name":"Alice"
                                                }
                                                """)
                        )
        );

        String token = createJwt(1L, "ADMIN");

        mockMvc.perform(get("/users/1")
            .cookie(new Cookie("token", token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user_id").value(1));
    }

    // -------------------------------------------------------------------------
    // HEADER SECURITY
    // -------------------------------------------------------------------------

    /**
     * Verifies client cannot spoof X-User-* headers.
     *
     * Gateway must replace them with trusted JWT attributes.
     */
    @Test
    void removesSpoofedUserHeadersAndInjectsTrustedIdentity() throws Exception {
        wireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock
                .get(urlEqualTo("/users/1"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                        )
        );

        String token = createJwt(1L, "ADMIN");

        mockMvc.perform(
                get("/users/1")
                        .header(
                                "X-User-Id",
                                "999"
                        )
                        .header(
                                "X-User-Role",
                                "USER"
                        )
                        .cookie(new Cookie("token", token))
        )
        .andExpect(status().isOk());


        wireMock.verify(getRequestedFor(urlEqualTo("/users/1"))
                .withHeader(
                        "X-User-Id",
                        equalTo("1")
                )
                .withHeader(
                        "X-User-Role",
                        equalTo("ADMIN")
                )
        );
    }

    // -------------------------------------------------------------------------
    // QUERY STRING
    // -------------------------------------------------------------------------

    /**
     * Verifies query parameters are preserved.
     */
    @Test
    void forwardsQueryString() throws Exception {
        wireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock
                .get(urlEqualTo("/courses/search?title=ml"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                        )
        );

        String token = createJwt(1L, "USER");

        mockMvc.perform(
                get("/courses/search?title=ml")
                        .cookie(new Cookie("token", token))
        )
        .andExpect(status().isOk());


        wireMock.verify(
                getRequestedFor(
                        urlEqualTo("/courses/search?title=ml")
                )
        );
    }


    // -------------------------------------------------------------------------
    // HTTP METHODS
    // -------------------------------------------------------------------------

    /**
     * Verifies POST is forwarded correctly.
     */
    @Test
    void forwardsPostRequests() throws Exception {
        wireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock
                .post(urlEqualTo("/roadmaps/generate"))
                        .willReturn(
                                aResponse()
                                        .withStatus(201)
                        )
        );

        String token = createJwt(1L, "USER");

        mockMvc.perform(
                post("/roadmaps/generate").cookie(new Cookie("token", token))
        )
        .andExpect(status().isCreated());

        wireMock.verify(postRequestedFor(urlEqualTo("/roadmaps/generate")));
    }

    /**
     * Verifies DELETE forwarding.
     */
    @Test
    void forwardsDeleteRequests() throws Exception {
        wireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock
                .delete(urlEqualTo("/users/1"))
                        .willReturn(
                                aResponse()
                                        .withStatus(204)
                        )
        );

        String token = createJwt(1L, "ADMIN");

        mockMvc.perform(
                delete("/users/1").cookie(new Cookie("token", token))
        )
        .andExpect(status().isNoContent());
    }


    // -------------------------------------------------------------------------
    // LLM PREFIX HANDLING
    // -------------------------------------------------------------------------

    /**
     * Verifies /llm prefix is stripped.
     *
     * Incoming:
     *     /llm/recommend
     *
     * Downstream:
     *     /recommend
     */
    @Test
    void llmPrefixIsRemovedBeforeForwarding() throws Exception {
        wireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock
                .post(urlEqualTo("/recommend"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withBody("""
                                                {
                                                  "result":"ok"
                                                }
                                                """)
                        )
        );

        String token = createJwt(1L, "USER");

        mockMvc.perform(
                post("/llm/recommend").cookie(new Cookie("token", token))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").value("ok"));


        wireMock.verify(postRequestedFor(urlEqualTo("/recommend")));
    }

    // -------------------------------------------------------------------------
    // ERROR PROPAGATION
    // -------------------------------------------------------------------------

    /**
     * Verifies downstream 404 is propagated unchanged.
     */
    @Test
    void propagatesDownstreamErrors() throws Exception {
        wireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock
                .get(urlEqualTo("/users/99"))
                        .willReturn(
                                aResponse()
                                        .withStatus(404)
                                        .withBody("Not found")
                        )
        );

        String token = createJwt(1L, "ADMIN");

        mockMvc.perform(
                get("/users/99").cookie(new Cookie("token", token))
        )
        .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private String createJwt(Long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(SIGNING_KEY.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

}
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

    /**
     * Starts a single WireMockServer on a random port for the entire
     * test class, then registers its address as the upstream URL for every
     * downstream service the gateway knows about. The JWT signing key is also
     * registered so the full Spring Security filter chain initialises with the
     * same secret used to sign tokens in individual tests.
     * 
     * Using a dynamic port avoids conflicts when multiple test suites run in
     * parallel, and @DynamicPropertySource ensures the values are
     * injected into the application context before the first test runs.
     */
    static WireMockServer wireMock =
            new WireMockServer(
                    com.github.tomakehurst.wiremock.core.WireMockConfiguration
                            .wireMockConfig()
                            .dynamicPort()
            );

    /**
     * Overrides application properties for the integration test environment.
     *
     * Routes service URLs to the WireMock server instead of real services and
     * provides a test JWT signing key so authentication works during tests.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {

        wireMock.start();

        registry.add("services.user.url", () -> "http://localhost:" + wireMock.port());
        registry.add("services.course.url", () -> "http://localhost:" + wireMock.port());
        registry.add("services.roadmap.url", () -> "http://localhost:" + wireMock.port());
        registry.add("services.llm.url", () -> "http://localhost:" + wireMock.port());
        registry.add("app.jwt.signing-key", () -> SIGNING_KEY);
    }


    /** Injects MockMvc to send HTTP requests through the full filter
     *  and controller stack without starting a real server. 
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * Clears all WireMock stub mappings and recorded requests before each test
     * so that stubs registered in one test cannot influence another.
     */
    @BeforeEach
    void setUp() {
        wireMock.resetAll();
    }

    /**
     * Clears WireMock state after each test as a safety net for any stubs or
     * requests that may have been recorded after the @BeforeEach reset
     * but that the test itself did not clean up.
     */
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
    // FEATURES ROUTING
    // -------------------------------------------------------------------------

    /**
     * Verifies /features/** is forwarded to user-service.
     */
    @Test
    void forwardsFeaturesRequests() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
            .get(urlEqualTo("/features"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "llm": true
                        }
                        """)
            )
        );

        String token = createJwt(1L, "USER");

        mockMvc.perform(
            get("/features")
                    .cookie(new Cookie("token", token))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.llm").value(true));

        wireMock.verify(
            getRequestedFor(urlEqualTo("/features"))
                    .withHeader("X-User-Id", equalTo("1"))
                    .withHeader("X-User-Role", equalTo("USER"))
        );
    }

    /**
     * Verifies PUT /features/** is forwarded correctly.
     */
    @Test
    void forwardsFeatureUpdates() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
            .put(urlEqualTo("/features"))
            .willReturn(
                aResponse()
                    .withStatus(204)
            )
        );

        String token = createJwt(1L, "ADMIN");

        mockMvc.perform(
            put("/features")
                    .cookie(new Cookie("token", token))
                    .contentType("application/json")
                    .content("""
                        {
                          "llm": false
                        }
                        """)
        )
        .andExpect(status().isNoContent());

        wireMock.verify(
            com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor(
                    urlEqualTo("/features"))
                    .withHeader("X-User-Id", equalTo("1"))
                    .withHeader("X-User-Role", equalTo("ADMIN"))
        );
    }

    // -------------------------------------------------------------------------
    // FEATURES ROUTING
    // -------------------------------------------------------------------------
    
    /**
     * Verifies /settings/** is forwarded to user-service.
     */
    @Test
    void forwardsSettingsRequests() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
            .get(urlEqualTo("/settings"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "tokenLimit": 8000
                        }
                        """)
            )
        );

        String token = createJwt(1L, "USER");

        mockMvc.perform(
            get("/settings")
                    .cookie(new Cookie("token", token))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenLimit").value(8000));

        wireMock.verify(
            getRequestedFor(urlEqualTo("/settings"))
                    .withHeader("X-User-Id", equalTo("1"))
                    .withHeader("X-User-Role", equalTo("USER"))
        );
    }

    /**
     * Verifies PUT /settings/** is forwarded correctly.
     */
    @Test
    void forwardsSettingsUpdates() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
            .put(urlEqualTo("/settings"))
            .willReturn(
                aResponse()
                    .withStatus(204)
            )
        );

        String token = createJwt(1L, "ADMIN");

        mockMvc.perform(
            put("/settings")
                    .cookie(new Cookie("token", token))
                    .contentType("application/json")
                    .content("""
                        {
                          "tokenLimit": 12000
                        }
                        """)
        )
        .andExpect(status().isNoContent());

        wireMock.verify(
            com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor(
                    urlEqualTo("/settings"))
                    .withHeader("X-User-Id", equalTo("1"))
                    .withHeader("X-User-Role", equalTo("ADMIN"))
        );
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
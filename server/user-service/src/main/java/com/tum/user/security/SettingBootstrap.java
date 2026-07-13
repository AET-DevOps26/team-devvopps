package com.tum.user.security;

import com.tum.user.model.AppSetting;
import com.tum.user.repository.AppSettingRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * On startup, seeds the default app settings. Idempotent: an existing
 * setting keeps whatever an admin last saved — only missing ones are inserted.
 *
 * The three prompt* sections are assembled by llm-service into its fixed
 * prompt skeleton (role → goal/courses data block → instructions → response
 * format). The data block itself is NOT editable — it is structural.
 */
@Component
public class SettingBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SettingBootstrap.class);

    static final String DEFAULT_ROLE =
            "You are an expert academic advisor creating a personalised learning roadmap.";

    static final String DEFAULT_INSTRUCTIONS = """
            1. Select the most relevant courses from the list above to reach the student's goal.
            2. Break the journey into clear milestones (e.g. "Complete foundational mathematics"). Also include external milestones that are not courses.
            3. For each milestone, define concrete tasks the student should do. For course tasks, include the course code in brackets (e.g. "Enroll in [IN2064] Machine Learning").
            4. Each milestone MUST contain at least 2–4 tasks. Tasks MUST belong to their milestone (nested structure)
            5. Create at most 5 milestones. Keep titles and descriptions short (one sentence) — the response must stay compact.
            6. Respond with ONLY valid JSON.""";

    static final String DEFAULT_RESPONSE_FORMAT = """
            {
              "milestones": [
                {
                  "title": "Milestone name",
                  "description": "What this milestone achieves",
                  "tasks": [
                    {
                      "title": "Task description",
                      "completed": false
                    }
                  ]
                }
              ]
            }""";

    private final AppSettingRepository repo;

    public SettingBootstrap(AppSettingRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        List<AppSetting> defaults = List.of(
                new AppSetting("promptRole", DEFAULT_ROLE,
                        "Who the LLM is told to be (first line of the prompt)"),
                new AppSetting("promptInstructions", DEFAULT_INSTRUCTIONS,
                        "The numbered instructions the LLM must follow"),
                new AppSetting("promptResponseFormat", DEFAULT_RESPONSE_FORMAT,
                        "The JSON schema example the LLM must produce — edit with care"),
                new AppSetting("monthlyTokenLimit", "50000",
                        "Per-user LLM token budget per calendar month (used when the tokenQuota flag is ON)")
        );
        for (AppSetting setting : defaults) {
            if (repo.findById(setting.getName()).isEmpty()) {
                repo.save(setting);
                log.info("[Settings] seeded '{}'", setting.getName());
            }
        }
    }
}

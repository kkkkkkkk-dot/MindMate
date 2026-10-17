package com.mindbridge.agent.config;

import com.mindbridge.agent.domain.UserAccount;
import com.mindbridge.agent.repository.UserAccountRepository;
import com.mindbridge.agent.service.knowledge.KnowledgeIngestionService;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final MindBridgeProperties properties;

    public DataInitializer(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            KnowledgeIngestionService knowledgeIngestionService,
            MindBridgeProperties properties
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 首次启动只创建显式配置的账号；没有配置密码时不生成任何默认账号。
        seedUsers();
        knowledgeIngestionService.ingestClasspathKnowledgeIfEmpty();
    }

    private void seedUsers() {
        MindBridgeProperties.Bootstrap bootstrap = properties.getBootstrap();
        seedUser(
                bootstrap.getAdminUsername(),
                bootstrap.getAdminPassword(),
                "Counselor Admin",
                Set.of("ROLE_ADMIN", "ROLE_USER"),
                "MIND_BRIDGE_ADMIN_PASSWORD"
        );
        seedUser(
                bootstrap.getStudentUsername(),
                bootstrap.getStudentPassword(),
                "Demo Student",
                Set.of("ROLE_USER"),
                "MIND_BRIDGE_STUDENT_PASSWORD"
        );
    }

    private void seedUser(String username, String password, String displayName, Set<String> roles, String passwordEnvName) {
        if (!StringUtils.hasText(password)) {
            logger.info("Skipping bootstrap account '{}': set {} to create it.", username, passwordEnvName);
            return;
        }
        if (!StringUtils.hasText(username)) {
            logger.warn("Skipping bootstrap account: username is empty.");
            return;
        }
        if (userAccountRepository.findByUsername(username).isPresent()) {
            return;
        }
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setDisplayName(displayName);
        account.setPassword(passwordEncoder.encode(password));
        account.setRoles(roles);
        userAccountRepository.save(account);
        logger.info("Created bootstrap account '{}'.", username);
    }
}
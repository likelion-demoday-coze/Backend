package demoday.backend.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ServicePeriodProperties.class,
        PassPolicyProperties.class
})
public class PaymentPolicyConfig {
}

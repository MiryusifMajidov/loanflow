package az.yusif.loanflow.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableAsync
public class SharedConfig {

    public static final ZoneId BAKU = ZoneId.of("Asia/Baku");

    @Bean
    Clock clock() {
        return Clock.system(BAKU);
    }
}

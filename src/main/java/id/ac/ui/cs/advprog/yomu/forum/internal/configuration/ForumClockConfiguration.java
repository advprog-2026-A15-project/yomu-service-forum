package id.ac.ui.cs.advprog.yomu.forum.internal.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ForumClockConfiguration {

    @Bean
    Clock forumClock() {
        return Clock.systemUTC();
    }
}


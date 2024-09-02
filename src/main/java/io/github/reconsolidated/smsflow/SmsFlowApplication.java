package io.github.reconsolidated.smsflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmsFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmsFlowApplication.class, args);
    }

}

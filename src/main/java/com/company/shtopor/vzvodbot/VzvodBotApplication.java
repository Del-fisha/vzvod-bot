package com.company.shtopor.vzvodbot;

import com.company.shtopor.vzvodbot.config.CoreClientProperties;
import com.company.shtopor.vzvodbot.config.TelegramBotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({TelegramBotProperties.class, CoreClientProperties.class})
public class VzvodBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(VzvodBotApplication.class, args);
    }
}

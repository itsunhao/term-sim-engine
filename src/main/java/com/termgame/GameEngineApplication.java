package com.termgame;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.termgame.config.EngineProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(EngineProperties.class)
public class GameEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameEngineApplication.class, args);
    }

    /**
     * 游戏定义/存档专用的 ObjectMapper：
     * JSON 使用 snake_case，便于游戏文件作者阅读与编写。
     */
    @Bean
    public ObjectMapper gameObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}

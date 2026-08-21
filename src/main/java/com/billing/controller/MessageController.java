package com.billing.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

@RestController
@RequestMapping("/api/i18n")
public class MessageController {

    private final ResourceLoader resourceLoader;

    @Autowired
    public MessageController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @GetMapping("/messages")
    public Map<String, String> messages(@RequestParam(name = "lang", defaultValue = "en") String lang) throws IOException {
        String file = "ta".equalsIgnoreCase(lang) ? "classpath:messages_ta.properties" : "classpath:messages.properties";
        Resource resource = resourceLoader.getResource(file);

        Properties properties = new Properties();
        try (InputStream inputStream = resource.getInputStream();
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            result.put(key, properties.getProperty(key));
        }
        return result;
    }
}

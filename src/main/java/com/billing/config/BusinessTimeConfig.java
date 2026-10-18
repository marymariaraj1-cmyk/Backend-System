package com.billing.config;

import com.billing.util.SalesUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessTimeConfig {

    public BusinessTimeConfig(@Value("${app.timezone:Asia/Kolkata}") String timezone) {
        SalesUtil.setBusinessZone(timezone);
    }
}

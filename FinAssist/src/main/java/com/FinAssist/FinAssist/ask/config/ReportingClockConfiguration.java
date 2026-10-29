package com.FinAssist.FinAssist.ask.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ReportingClockConfiguration {

    @Bean
    Clock transactionReportingClock(@Value("${finassist.reporting-zone:UTC}") String reportingZone) {
        return Clock.system(ZoneId.of(reportingZone));
    }
}

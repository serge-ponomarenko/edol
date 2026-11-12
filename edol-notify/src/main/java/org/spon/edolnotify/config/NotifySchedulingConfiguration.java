package org.spon.edolnotify.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "edol-notify.telegram.settings-source", havingValue = "hub")
@EnableScheduling
public class NotifySchedulingConfiguration {
}

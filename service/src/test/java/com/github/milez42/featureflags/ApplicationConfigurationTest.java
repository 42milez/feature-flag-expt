package com.github.milez42.featureflags;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class ApplicationConfigurationTest {
  @Test
  void enablesGracefulShutdownWithShutdownPhaseTimeout() {
    Properties properties = loadApplicationProperties();

    assertThat(properties)
        .containsEntry("server.shutdown", "graceful")
        .containsEntry("spring.lifecycle.timeout-per-shutdown-phase", "20s");
  }

  private static Properties loadApplicationProperties() {
    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("application.yaml"));
    return yaml.getObject();
  }
}

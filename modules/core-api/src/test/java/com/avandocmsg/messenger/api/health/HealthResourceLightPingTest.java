package com.avandocmsg.messenger.api.health;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.config.RedisProbe;
import com.avandocmsg.messenger.core.port.DatabaseHealthPort;
import com.avandocmsg.messenger.core.port.NatsConnectionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthResourceLightPingTest {

  @Test
  void ready_usesLightDatabasePing() {
    var resource = new HealthResource(
        new AppConfig(),
        new DatabaseHealthPort() {
          @Override
          public boolean lightPing() {
            return true;
          }

          @Override
          public boolean ping() {
            return false;
          }
        },
        NatsConnectionStatus.always(true),
        new RedisProbe(new AppConfig(), null));

    var response = resource.ready();
    assertEquals(200, response.getStatus());
  }
}

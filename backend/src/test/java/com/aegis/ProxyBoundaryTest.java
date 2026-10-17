package com.aegis;

import static org.assertj.core.api.Assertions.*;

import com.aegis.auth.ClientAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ProxyBoundaryTest {
  @Test
  void onlyExactTrustedPeerCanSupplyCanonicalAddress() {
    var r = new MockHttpServletRequest();
    r.setRemoteAddr("10.0.0.5");
    r.addHeader("X-Forwarded-For", "198.51.100.5");
    r.addHeader("X-Aegis-Client-IP", "198.51.100.6");
    assertThat(ClientAddress.resolve(r, "172.29.0.10")).isEqualTo("10.0.0.5");
    r.setRemoteAddr("172.29.0.10");
    assertThat(ClientAddress.resolve(r, "172.29.0.10")).isEqualTo("198.51.100.6");
    for (String value :
        new String[] {"198.51.100.6,198.51.100.7", "::1", "999.1.2.3", "01.2.3.4", "hostname"}) {
      r.removeHeader("X-Aegis-Client-IP");
      r.addHeader("X-Aegis-Client-IP", value);
      assertThatThrownBy(() -> ClientAddress.resolve(r, "172.29.0.10"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}

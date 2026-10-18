package com.aegis.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/** Only an exact private edge peer may assert a single canonical client address. */
public final class ClientAddress {
  private static final Pattern IPV4 = Pattern.compile("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}");

  private ClientAddress() {}

  public static String resolve(HttpServletRequest request, String trustedPeer) {
    if (trustedPeer.isBlank() || !trustedPeer.equals(request.getRemoteAddr()))
      return request.getRemoteAddr();
    String value = request.getHeader("X-Aegis-Client-IP");
    if (value == null || !IPV4.matcher(value).matches())
      throw new IllegalArgumentException("Missing canonical proxy client address");
    for (String part : value.split("\\."))
      if (Integer.parseInt(part) > 255 || (part.length() > 1 && part.startsWith("0")))
        throw new IllegalArgumentException("Invalid canonical proxy client address");
    return value;
  }
}

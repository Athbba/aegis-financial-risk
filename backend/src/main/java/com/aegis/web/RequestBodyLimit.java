package com.aegis.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestBodyLimit extends OncePerRequestFilter {
  public static final int MAX_BYTES = 65_536;
  private final ObjectMapper mapper;

  public RequestBodyLimit(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (!req.getRequestURI().startsWith("/api/")
        || !(req.getMethod().equals("POST")
            || req.getMethod().equals("PUT")
            || req.getMethod().equals("PATCH"))) {
      chain.doFilter(req, res);
      return;
    }
    if (req.getContentLengthLong() > MAX_BYTES) {
      reject(res);
      return;
    }
    byte[] body = req.getInputStream().readNBytes(MAX_BYTES + 1);
    if (body.length > MAX_BYTES) {
      reject(res);
      return;
    }
    chain.doFilter(
        new HttpServletRequestWrapper(req) {
          @Override
          public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
              @Override
              public int read() {
                return input.read();
              }

              @Override
              public int read(byte[] b, int off, int len) {
                return input.read(b, off, len);
              }

              @Override
              public boolean isFinished() {
                return input.available() == 0;
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("Synchronous JSON requests only");
              }
            };
          }

          @Override
          public BufferedReader getReader() {
            return new BufferedReader(
                new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
          }
        },
        res);
  }

  private void reject(HttpServletResponse res) throws IOException {
    res.setStatus(413);
    res.setContentType("application/problem+json");
    mapper.writeValue(
        res.getOutputStream(),
        ApiErrors.problem(
            org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
            "REQUEST_TOO_LARGE",
            "JSON requests must not exceed 64 KiB."));
  }
}

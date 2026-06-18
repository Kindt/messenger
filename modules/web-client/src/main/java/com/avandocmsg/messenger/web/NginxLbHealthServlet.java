package com.avandocmsg.messenger.web;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Synthetic LB probe (FLEET-06): JSON when nginx forwards or Tomcat is hit directly (QEMU :19088→:9088). */
final class NginxLbHealthServlet extends HttpServlet {
  private static final byte[] BODY =
      "{\"status\":\"ok\",\"component\":\"nginx-lb\"}".getBytes(StandardCharsets.UTF_8);

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("application/json;charset=UTF-8");
    resp.getOutputStream().write(BODY);
  }
}

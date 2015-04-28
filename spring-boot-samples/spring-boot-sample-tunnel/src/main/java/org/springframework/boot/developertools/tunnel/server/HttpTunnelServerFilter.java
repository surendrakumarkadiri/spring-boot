/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.developertools.tunnel.server;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;

/**
 * Servlet Filter to manage a HTTP tunnel server.
 *
 * @author Phillip Webb
 * @since 1.3.0
 */
public class HttpTunnelServerFilter implements Filter {

	private final HttpTunnelServer httpHandler;

	public HttpTunnelServerFilter(int port) {
		this(new HttpTunnelServer(new SocketTargetServerConnection(port)));
	}

	public HttpTunnelServerFilter(HttpTunnelServer httpHandler) {
		this.httpHandler = httpHandler;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		if (request instanceof HttpServletRequest
				&& response instanceof HttpServletResponse) {
			ServerHttpRequest httpRequest = new ServletServerHttpRequest(
					(HttpServletRequest) request);
			ServerHttpResponse httpResponse = new ServletServerHttpResponse(
					(HttpServletResponse) response);
			if (httpRequest.getURI().toString().endsWith("/httptunnel")) {
				this.httpHandler.handle(httpRequest, httpResponse);
				return;
			}
		}
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
	}

}

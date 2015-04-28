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

package org.springframework.boot.developertools.tunnel.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.developertools.tunnel.payload.HttpTunnelPayload;
import org.springframework.boot.developertools.tunnel.payload.HttpTunnelPayloadForwarder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Assert;

/**
 * {@link TunnelConnection} implementation that uses HTTP to transfer data.
 *
 * @author Phillip Webb
 * @since 1.3.0
 * @see TunnelClient
 * @see org.springframework.boot.developertools.tunnel.server.HttpTunnelServer
 */
public class HttpTunnelConnection implements TunnelConnection {

	private static Log logger = LogFactory.getLog(HttpTunnelConnection.class);

	private final URI uri;

	private final ClientHttpRequestFactory requestFactory;

	public HttpTunnelConnection(String url) {
		this(url, new SimpleClientHttpRequestFactory());
	}

	protected HttpTunnelConnection(String url, ClientHttpRequestFactory requestFactory) {
		Assert.notNull(url, "URL must not be null");
		Assert.hasLength(url, "URL must not be empty");
		try {
			this.uri = new URL(url).toURI();
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException("Malformed URL '" + url + "'");
		}
		catch (MalformedURLException ex) {
			throw new IllegalArgumentException("Malformed URL '" + url + "'");
		}
		this.requestFactory = requestFactory;
	}

	@Override
	public TunnelChannel open(WritableByteChannel incomingChannel, Closeable closeable)
			throws Exception {
		return new TunnelChannel(incomingChannel, closeable);
	}

	protected final ClientHttpRequest createRequest(boolean hasPayload)
			throws IOException {
		HttpMethod method = (hasPayload ? HttpMethod.POST : HttpMethod.GET);
		return this.requestFactory.createRequest(this.uri, method);
	}

	/**
	 * A {@link WritableByteChannel} used to transfer traffic.
	 */
	protected class TunnelChannel implements WritableByteChannel {

		private final HttpTunnelPayloadForwarder forwarder;

		private final Closeable closeable;

		private boolean open = true;

		private AtomicLong requestSeq = new AtomicLong();

		private final ExecutorService executor = Executors
				.newCachedThreadPool(new TunnelThreadFactory());

		public TunnelChannel(WritableByteChannel incomingChannel, Closeable closeable) {
			this.forwarder = new HttpTunnelPayloadForwarder(incomingChannel);
			this.closeable = closeable;
			openNewConnection(null);
		}

		@Override
		public boolean isOpen() {
			return this.open;
		}

		@Override
		public void close() throws IOException {
			if (this.open) {
				this.open = false;
				this.closeable.close();
			}
		}

		@Override
		public int write(ByteBuffer src) throws IOException {
			int size = src.remaining();
			if (size > 0) {
				openNewConnection(new HttpTunnelPayload(
						this.requestSeq.incrementAndGet(), src));
			}
			return size;
		}

		private synchronized void openNewConnection(final HttpTunnelPayload payload) {
			this.executor.execute(new Runnable() {

				@Override
				public void run() {
					try {
						sendAndReceive(payload);
					}
					catch (IOException ex) {
						logger.trace("Unexpected connection error", ex);
						closeQuitely();
					}
				}

				private void closeQuitely() {
					try {
						close();
					}
					catch (IOException ex) {
					}
				}

			});
		}

		private void sendAndReceive(HttpTunnelPayload payload) throws IOException {
			ClientHttpRequest request = createRequest(payload != null);
			if (payload != null) {
				payload.assignTo(request);
			}
			handleResponse(request.execute());
		}

		private void handleResponse(ClientHttpResponse response) throws IOException {
			if (response.getStatusCode() == HttpStatus.GONE) {
				close();
				return;

			}
			if (response.getStatusCode() == HttpStatus.OK) {
				HttpTunnelPayload payload = HttpTunnelPayload.get(response);
				if (payload != null) {
					this.forwarder.forward(payload);
				}
			}
			if (response.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) {
				openNewConnection(null);
			}
		}

	}

	private static class TunnelThreadFactory implements ThreadFactory {

		@Override
		public Thread newThread(Runnable runnable) {
			Thread thread = new Thread(runnable, "HTTP Tunnel Connection");
			thread.setDaemon(true);
			return thread;
		}

	}

}
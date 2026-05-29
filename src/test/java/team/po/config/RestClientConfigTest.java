package team.po.config;

import static org.assertj.core.api.Assertions.*;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class RestClientConfigTest {

	@Test
	void restClient_timesOutWhenExternalApiTakesTooLong() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/slow", exchange -> {
			try {
				Thread.sleep(300);
				byte[] body = "ok".getBytes();
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream outputStream = exchange.getResponseBody()) {
					outputStream.write(body);
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			} finally {
				exchange.close();
			}
		});
		server.start();

		try {
			int port = server.getAddress().getPort();
			RestClient restClient = new RestClientConfig().restClient(
				Duration.ofMillis(100),
				Duration.ofMillis(100)
			);

			assertThatThrownBy(() -> restClient.get()
				.uri("http://localhost:" + port + "/slow")
				.retrieve()
				.toBodilessEntity())
				.isInstanceOf(ResourceAccessException.class);
		} finally {
			server.stop(0);
		}
	}
}

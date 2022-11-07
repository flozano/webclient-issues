package flozano.issues.spring.webclientheaderror;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import com.nimbusds.jose.util.StandardCharset;

import reactor.netty.http.client.HttpClient;

public class ItemClientTest {

	/*
	 * If true, the exception generated inside the exchangeToMono IS NOT propagated.
	 * If false, the exception generated inside the exchangeToMono IS propagated
	 */
	private static final boolean EXTRACT_BODY_IN_CASE_OF_ERROR = true;

	@Rule
	public MockServerRule mockServerRule = new MockServerRule(this);

	private MockServerClient mockServerClient; // filled by rule...

	private ItemClient itemClient;

	private static final String RESPONSE = """
			{
				"something": true
			}
			""";
	private static final long CONTENT_LENGTH = RESPONSE.getBytes(StandardCharset.UTF_8).length;
	private static final String CONTENT_TYPE = "application/vnd.mystuff+json";
	private static final String CONTENT_LENGTH_STR = "" + CONTENT_LENGTH;

	@Before
	public void setup() {

		mockServerClient.when(request().withMethod("HEAD").withPath("/somewhere-200"))
				.respond(response().withStatusCode(200).withHeader("Content-Length", CONTENT_LENGTH_STR)
						.withHeader("Content-Type", CONTENT_TYPE).withHeader("code", "value"));

		mockServerClient.when(request().withMethod("HEAD").withPath("/somewhere-401"))
				.respond(response().withStatusCode(401).withHeader("code", "error401"));

		mockServerClient.when(request().withMethod("HEAD").withPath("/somewhere-500"))
				.respond(response().withStatusCode(500).withHeader("code", "error500"));

		mockServerClient.when(request().withMethod("GET").withPath("/somewhere-200"))
				.respond(response().withStatusCode(200).withHeader("Content-Length", CONTENT_LENGTH_STR)
						.withHeader("Content-Type", CONTENT_TYPE).withHeader("code", "value")
						.withBody(RESPONSE, StandardCharsets.UTF_8));

		mockServerClient.when(request().withMethod("GET").withPath("/somewhere-401"))
				.respond(response().withStatusCode(401).withHeader("code", "error401"));

		mockServerClient.when(request().withMethod("GET").withPath("/somewhere-500"))
				.respond(response().withStatusCode(500).withHeader("code", "error500"));

		itemClient = new ItemClient("http://127.0.0.1:" + mockServerRule.getPort(), nettyConnector(),
				EXTRACT_BODY_IN_CASE_OF_ERROR);
	}

	@Test
	public void testWebClientHead200() {
		var metadata = itemClient.head("/somewhere-200").block();
		assertNotNull(metadata);
		assertEquals(CONTENT_TYPE, metadata.code());
		assertEquals(CONTENT_LENGTH, metadata.length());
	}

	@Test
	public void testWebClientGet200() {
		var item = itemClient.get("/somewhere-200").block();
		assertNotNull(item);
		assertNotNull(item.metadata());

		assertEquals(CONTENT_TYPE, item.metadata().code());
		assertEquals(CONTENT_LENGTH, item.metadata().length());
		assertEquals(RESPONSE, item.content());
	}

	@Test
	public void testWebClientHead401() {
		try {
			itemClient.head("/somewhere-401").block();
			fail("UnauthorizedException expected here");
		} catch (UnauthorizedException e) {

		}
	}

	@Test
	public void testWebClientGet401() {
		try {
			itemClient.get("/somewhere-401").block();
			fail("UnauthorizedException expected here");
		} catch (UnauthorizedException e) {

		}
	}

	@Test
	public void testWebClientHead500() {
		try {
			itemClient.head("/somewhere-500").block();
			fail("IllegalStateException expected here");
		} catch (IllegalStateException e) {

		}
	}

	@Test
	public void testWebClientGet500() {
		try {
			itemClient.get("/somewhere-500").block();
			fail("IllegalStateException expected here");
		} catch (IllegalStateException e) {

		}
	}

	ClientHttpConnector nettyConnector() {
		return new ReactorClientHttpConnector(HttpClient.create());
	}

	ClientHttpConnector jdkConnector() {
		return new JdkClientHttpConnector();
	}
}
package flozano.issues.spring.webclientheaderror;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

class ItemClient {
	private final WebClient client;
	private final boolean extractErrorFromResponse;

	ItemClient(String baseUrl, ClientHttpConnector clientConnector, boolean extractErrorFromResponse) {
		client = WebClient.builder().clientConnector(clientConnector).baseUrl(baseUrl).build();
		this.extractErrorFromResponse = extractErrorFromResponse;
	}

	Mono<ItemMetadata> head(String path) {
		return client.head().uri(path)
				.exchangeToMono(response -> check(response).flatMap(ItemClient::toMetadataResponse));
	}

	Mono<Item> get(String path) {
		return client.get().uri(path).exchangeToMono(response -> check(response).flatMap(ItemClient::toResponse));
	}

	private static Mono<Item> toResponse(ClientResponse response) {
		var body = response.bodyToMono(String.class);
		var metadata = toMetadataResponse(response);
		return Mono.zip(metadata, body).map(pair -> new Item(pair.getT1(), pair.getT2()));
	}

	private static Mono<ItemMetadata> toMetadataResponse(ClientResponse response) {
		var codes = response.headers().header("code");
		var code = codes != null && codes.size() > 0 ? codes.get(0) : null;
		return Mono.just(new ItemMetadata(response.headers().contentLength().orElse(0), code,
				response.headers().contentType().map(Object::toString).orElse("application/octet-stream")));
	}

	private Mono<ClientResponse> check(ClientResponse response) {
		if (response.statusCode().isError()) {
			return responseToException(response).flatMap(Mono::error);
		}
		return Mono.just(response);
	}

	private Mono<? extends Throwable> responseToException(ClientResponse response) {
		if (response.statusCode() == HttpStatus.UNAUTHORIZED) {
			return toMessage(response).map(UnauthorizedException::new);
		}
		return toMessage(response)
				.map(msg -> new IllegalStateException("unexpected status code " + response.statusCode() + " : " + msg));
	}

	private Mono<String> toMessage(ClientResponse response) {
		if (extractErrorFromResponse) {
			// fails:
			return response.bodyToMono(String.class);
		} else {
			// works:
			return Mono.just("An HTTP error happened (response code = " + response.statusCode().value() + ")");
		}
	}
}

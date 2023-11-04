package vb.nego;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

class NegotiateHttpClient extends HttpClient {
    public static HttpClient wrap(HttpClient delegate) {
        return new NegotiateHttpClient(delegate);
    }

    private final HttpClient delegate;

    private NegotiateHttpClient(HttpClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return delegate.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return delegate.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return delegate.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return delegate.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return delegate.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return delegate.authenticator();
    }

    @Override
    public Version version() {
        return delegate.version();
    }

    @Override
    public Optional<Executor> executor() {
        return delegate.executor();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        var response = delegate.send(request, responseBodyHandler);
        var arq = needsAuthorization(request, response);
        return arq.isPresent() ? delegate.send(arq.get(), responseBodyHandler) : response;
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, BodyHandler<T> responseBodyHandler) {
        var fu = delegate.sendAsync(request, responseBodyHandler);
        return fu.thenCompose(response -> {
            var arq = needsAuthorization(request, response);
            return arq.isPresent() ? delegate.sendAsync(arq.get(), responseBodyHandler) : fu;
        });
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, BodyHandler<T> responseBodyHandler,
            PushPromiseHandler<T> pushPromiseHandler) {
        var fu = delegate.sendAsync(request, responseBodyHandler, pushPromiseHandler);
        return fu.thenCompose(response -> {
            var arq = needsAuthorization(request, response);
            return arq.isPresent() ? delegate.sendAsync(arq.get(), responseBodyHandler, pushPromiseHandler) : fu;
        });
    }

    @Override
    public java.net.http.WebSocket.Builder newWebSocketBuilder() {
        // TODO
        return delegate.newWebSocketBuilder();
    }

    private Optional<HttpRequest> needsAuthorization(HttpRequest request, HttpResponse<?> response) {
        if (response.statusCode() == 401 && response.headers().allValues("WWW-Authenticate").stream()
                .anyMatch(v -> v.equalsIgnoreCase("negotiate"))) {
            return Optional.of(HttpRequest.newBuilder(request, (k, v) -> true)
                    .header("Authorization", "Negotiate " + request.uri() + " " + LocalDateTime.now()).build());
        } else {
            return Optional.empty();
        }
    }
}

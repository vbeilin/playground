package vb.nego;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpRunner {
    private static final Logger logS = LoggerFactory.getLogger(HttpRunner.class.getName() + ".Server");
    private static final Logger logC = LoggerFactory.getLogger(HttpRunner.class.getName() + ".Client");

    public static void main(String[] args) throws Exception {

        var server = HttpServer.create();
        server.createContext("/", exch -> {
            logS.info("Request {} {}", exch.getRequestMethod(), exch.getRequestHeaders().entrySet());
            List<String> nego = exch.getRequestHeaders().get("Authorization");
            if (nego == null || nego.isEmpty()
                    || nego.stream().noneMatch(v -> v.toLowerCase().startsWith("negotiate "))) {
                logS.info("401");
                exch.getResponseHeaders().put("WWW-Authenticate", List.of("Negotiate"));
                exch.sendResponseHeaders(401, -1);
                exch.getResponseBody().close();
            } else {
                logS.info("200");
                exch.sendResponseHeaders(200, 0);
                exch.getResponseBody().write(nego.toString().getBytes());
                if (exch.getRequestMethod().equalsIgnoreCase("post")) {
                    exch.getResponseBody().write("\nRequest:\n".getBytes());
                    exch.getRequestBody().transferTo(exch.getResponseBody());
                }
                exch.getResponseBody().close();
            }
        });
        server.bind(new InetSocketAddress(8088), 0);
        server.start();
        logS.info("Started.");
        URI uri = URI.create("http://localhost:8088/");

        var client = NegotiateHttpClient.wrap(HttpClient.newBuilder().build());
        send(client, HttpRequest.newBuilder(uri).GET().build());
        send(client, HttpRequest.newBuilder(uri).POST(BodyPublishers.ofString("AQUA")).build());
        sendAsync(client, HttpRequest.newBuilder(uri).POST(BodyPublishers.ofString("AQUA")).build());
        server.stop(0);
    }

    private static void applyLog(HttpRequest request, Block<HttpRequest, HttpResponse<String>> sender)
            throws Exception {
        logC.info("Request {}", request);
        var response = sender.apply(request);
        logC.info("Response {}, Body: [{}]", response, response.body());
    }

    private static void send(HttpClient client, HttpRequest request) throws Exception {
        applyLog(request, rq -> client.send(rq, HttpResponse.BodyHandlers.ofString()));
    }

    private static void sendAsync(HttpClient client, HttpRequest request) throws Exception {
        applyLog(request, rq -> client.sendAsync(rq, HttpResponse.BodyHandlers.ofString()).get());
    }

    private interface Block<T, R> {
        R apply(T t) throws Exception;
    }
}

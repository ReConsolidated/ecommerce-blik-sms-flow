package io.github.reconsolidated.smsflow;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SmsService {
    @Value("${apiKeys.smsPlanet.secret}")
    private String smsPlanetApiKey;
    private final HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
            .responseTimeout(Duration.ofMillis(15000))
            .doOnConnected(conn ->
                    conn.addHandlerLast(new ReadTimeoutHandler(15000, TimeUnit.MILLISECONDS))
                            .addHandlerLast(new WriteTimeoutHandler(15000, TimeUnit.MILLISECONDS)));


    public void sendSms(String phoneNumber, String message) {
        System.out.println("Sending SMS to: " + phoneNumber + " with message: " + message);
        WebClient client = WebClient.builder()
                .baseUrl("https://api2.smsplanet.pl/sms")
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setBearerAuth(smsPlanetApiKey);
                    httpHeaders.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
                })
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("from", "48532963363");
        formData.add("to", phoneNumber);
        formData.add("msg", message);

        Map response = client.post()
                .body(BodyInserters.fromFormData(formData))
                .exchangeToMono(clientResponse -> {
                    System.out.println(clientResponse.statusCode());
                    return clientResponse.bodyToMono(Map.class);
                }).block();
        System.out.println(response);
    }
}

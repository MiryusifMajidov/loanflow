package az.yusif.loanflow.scoring.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class CreditBureauClient {

    private final RestClient restClient;

    public CreditBureauClient(RestClient.Builder builder, BureauProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }

    public Optional<BureauReport> findReport(String finCode) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/reports/{fin}", finCode)
                    .retrieve()
                    .body(BureauReport.class));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            throw e;
        }
    }
}

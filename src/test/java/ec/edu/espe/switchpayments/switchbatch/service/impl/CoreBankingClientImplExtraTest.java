package ec.edu.espe.switchpayments.switchbatch.service.impl;

import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CoreBankingClientImplExtraTest {

    private MockRestServiceServer server;
    private CoreBankingClientImpl client;

    @BeforeEach
    void setUp() {
        FileReceptionProperties properties = new FileReceptionProperties();
        properties.setCoreBaseUrl("http://core.test");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CoreBankingClientImpl(properties, builder);
    }

    @Test
    void isAccountValidReturnsTrueWhenCoreReportsValid() {
        server.expect(once(), requestTo(
                        "http://core.test/api/v1/accounts/validate?accountNumber=1234567890&clientRuc=0912345678"))
                .andRespond(withSuccess("{\"valid\": true}", MediaType.APPLICATION_JSON));

        assertThat(client.isAccountValid("1234567890", "0912345678")).isTrue();
        server.verify();
    }

    @Test
    void isAccountValidSkipsCoreCallWhenValidationDisabled() {
        FileReceptionProperties properties = new FileReceptionProperties();
        properties.setCoreBaseUrl("http://core.test");
        properties.setCoreValidationEnabled(false);
        client = new CoreBankingClientImpl(properties, RestClient.builder());

        assertThat(client.isAccountValid("1234567890", "0912345678")).isTrue();
    }

    @Test
    void hasActiveMassPaymentServiceReturnsTrueWhenCoreReportsActive() {
        server.expect(once(), requestTo(
                        "http://core.test/api/v1/customers/mass-payments/active?clientRuc=0912345678&serviceType=NOMINA"))
                .andRespond(withSuccess("{\"active\": true}", MediaType.APPLICATION_JSON));

        assertThat(client.hasActiveMassPaymentService("0912345678", "NOMINA")).isTrue();
        server.verify();
    }

}

package ec.edu.espe.switchbatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ec.edu.espe.switchbatch.config.FileReceptionProperties;

class CoreBankingClientImplTest {

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
    void returnsTrueWhenSourceAccountMatchesFavoriteAccount() {
        server.expect(once(), requestTo("http://core.test/api/v2/accounts/customer/0912345678/favorite"))
                .andRespond(withSuccess("""
                        {
                          "accountNumber": "1234567890",
                          "availableBalance": 100.50,
                          "status": "ACTIVE",
                          "favorite": true
                        }
                        """, MediaType.APPLICATION_JSON));

        assertTrue(client.isFavoriteAccount("1234567890", "0912345678"));
        server.verify();
    }

    @Test
    void returnsTrueWhenFavoriteAccountNumberIsNestedInAccountData() {
        server.expect(once(), requestTo("http://core.test/api/v2/accounts/customer/0912345678/favorite"))
                .andRespond(withSuccess("""
                        {
                          "account": {
                            "number": "1234567890",
                            "status": "ACTIVE"
                          },
                          "balances": {
                            "available": 100.50
                          },
                          "favorite": true
                        }
                        """, MediaType.APPLICATION_JSON));

        assertTrue(client.isFavoriteAccount("1234567890", "0912345678"));
        server.verify();
    }

    @Test
    void returnsFalseWhenSourceAccountDoesNotMatchFavoriteAccount() {
        server.expect(once(), requestTo("http://core.test/api/v2/accounts/customer/0912345678/favorite"))
                .andRespond(withSuccess("""
                        {"accountNumber":"1234567890","favorite":true}
                        """, MediaType.APPLICATION_JSON));

        assertFalse(client.isFavoriteAccount("0000000000", "0912345678"));
        server.verify();
    }

    @Test
    void returnsFalseWhenCoreSaysCustomerHasNoFavoriteAccount() {
        server.expect(once(), requestTo("http://core.test/api/v2/accounts/customer/0912345678/favorite"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFalse(client.isFavoriteAccount("1234567890", "0912345678"));
        server.verify();
    }

    @Test
    void returnsFalseWhenCoreSaysCustomerIdIsInvalid() {
        server.expect(once(), requestTo("http://core.test/api/v2/accounts/customer/invalid/favorite"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertFalse(client.isFavoriteAccount("1234567890", "invalid"));
        server.verify();
    }

    @Test
    void returnsFalseWhenCustomerIdIsInvalid() {
        assertFalse(client.isFavoriteAccount("1234567890", " "));
    }
}

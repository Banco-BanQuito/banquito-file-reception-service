package ec.edu.espe.switchbatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ec.edu.espe.switchbatch.config.FileReceptionProperties;

class BusinessDayServiceImplTest {

    private MockRestServiceServer server;
    private BusinessDayServiceImpl service;

    @BeforeEach
    void setUp() {
        FileReceptionProperties properties = new FileReceptionProperties();
        properties.setCoreBaseUrl("http://core.test");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new BusinessDayServiceImpl(properties, builder);
    }

    @Test
    void returnsBusinessDayWhenCoreSaysDateIsNotHolidayNorWeekend() {
        server.expect(once(), requestTo("http://core.test/api/v2/calendar/holidays/check?date=2026-12-24"))
                .andRespond(withSuccess("""
                        {"date":"2026-12-24","holiday":false,"weekend":false}
                        """, MediaType.APPLICATION_JSON));

        assertTrue(service.isBusinessDay(LocalDate.of(2026, Month.DECEMBER, 24)));
        server.verify();
    }

    @Test
    void returnsNonBusinessDayWhenCoreSaysDateIsHoliday() {
        server.expect(once(), requestTo("http://core.test/api/v2/calendar/holidays/check?date=2026-12-25"))
                .andRespond(withSuccess("""
                        {"date":"2026-12-25","holiday":true,"name":"Navidad","weekend":false}
                        """, MediaType.APPLICATION_JSON));

        assertFalse(service.isBusinessDay(LocalDate.of(2026, Month.DECEMBER, 25)));
        server.verify();
    }

    @Test
    void returnsNonBusinessDayWhenCoreSaysDateIsWeekend() {
        server.expect(once(), requestTo("http://core.test/api/v2/calendar/holidays/check?date=2026-12-26"))
                .andRespond(withSuccess("""
                        {"date":"2026-12-26","holiday":false,"weekend":true}
                        """, MediaType.APPLICATION_JSON));

        assertFalse(service.isBusinessDay(LocalDate.of(2026, Month.DECEMBER, 26)));
        server.verify();
    }

    @Test
    void fallsBackToWeekdayRuleWhenCoreFails() {
        server.expect(once(), requestTo("http://core.test/api/v2/calendar/holidays/check?date=2026-12-24"))
                .andRespond(withServerError());

        assertTrue(service.isBusinessDay(LocalDate.of(2026, Month.DECEMBER, 24)));
        server.verify();
    }

    @Test
    void nextBusinessDaySkipsHolidayAndWeekendDates() {
        server.expect(once(), requestTo("http://core.test/api/v2/calendar/holidays/check?date=2026-12-25"))
                .andRespond(withSuccess("""
                        {"date":"2026-12-25","holiday":true,"name":"Navidad","weekend":false}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://core.test/api/v2/calendar/holidays/check?date=2026-12-26"))
                .andRespond(withSuccess("""
                        {"date":"2026-12-26","holiday":false,"weekend":true}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://core.test/api/v2/calendar/holidays/check?date=2026-12-27"))
                .andRespond(withSuccess("""
                        {"date":"2026-12-27","holiday":false,"weekend":true}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://core.test/api/v2/calendar/holidays/check?date=2026-12-28"))
                .andRespond(withSuccess("""
                        {"date":"2026-12-28","holiday":false,"weekend":false}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(LocalDate.of(2026, Month.DECEMBER, 28),
                service.nextBusinessDay(LocalDate.of(2026, Month.DECEMBER, 24)));
        server.verify();
    }
}

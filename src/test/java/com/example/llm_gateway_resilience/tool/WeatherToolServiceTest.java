package com.example.llm_gateway_resilience.tool;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WeatherToolServiceTest {

    @Test
    void deberiaDevolverElTiempoActualParaUnaUbicacionValida() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(startsWith("https://geocoding-api.open-meteo.com/v1/search")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"results":[{"latitude":40.4,"longitude":-3.7,"name":"Madrid"}]}
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(startsWith("https://api.open-meteo.com/v1/forecast")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"current":{"temperature_2m":25.0,"wind_speed_10m":10.0,"weather_code":0}}
                        """, MediaType.APPLICATION_JSON));

        WeatherToolService weatherToolService = new WeatherToolService(builder.build());

        WeatherResult result = weatherToolService.getCurrentWeather("Madrid");

        assertEquals("Madrid", result.location());
        assertEquals(25.0, result.temperatureC());
        assertEquals(10.0, result.windSpeedKmh());
        assertEquals("despejado", result.condition());

        server.verify();
    }

    @Test
    void deberiaPropagarErrorCuandoElServicioDeGeocodingFalla() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(startsWith("https://geocoding-api.open-meteo.com/v1/search")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        WeatherToolService weatherToolService = new WeatherToolService(builder.build());

        // Sin proxy de Spring AOP en este test unitario, @Retry/@CircuitBreaker no interceptan
        // la llamada: el fallo se propaga tal cual, en vez de convertirse en WeatherToolException
        // (eso solo ocurre a través del fallbackMethod cuando el bean corre dentro del contexto Spring).
        assertThrows(HttpServerErrorException.class, () -> weatherToolService.getCurrentWeather("Ciudad Inexistente"));
    }

    @Test
    void deberiaTraducirCodigosDeTiempoComunes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(startsWith("https://geocoding-api.open-meteo.com/v1/search")))
                .andRespond(withSuccess("""
                        {"results":[{"latitude":48.85,"longitude":2.35,"name":"Paris"}]}
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(startsWith("https://api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess("""
                        {"current":{"temperature_2m":12.0,"wind_speed_10m":5.0,"weather_code":61}}
                        """, MediaType.APPLICATION_JSON));

        WeatherToolService weatherToolService = new WeatherToolService(builder.build());

        WeatherResult result = weatherToolService.getCurrentWeather("Paris");

        assertEquals("lluvia", result.condition());
    }
}

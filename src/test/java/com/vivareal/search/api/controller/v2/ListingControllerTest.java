package com.vivareal.search.api.controller.v2;

import java.util.List;
import com.vivareal.search.api.model.SearchApiResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.ResponseExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ListingControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void exampleTest() {
        SearchApiResponse response = this.restTemplate.getForObject("/v2/listings/", SearchApiResponse.class);

        assertNotNull(response);
        assertThat(response.getListings()).isEqualTo("foi memo!");
    }

    @Test
    public void exampleStreamTest() {
        String list = this.restTemplate.execute("/v2/listings/stream", HttpMethod.GET, null, (ClientHttpResponse client) -> {
            try (Scanner sc = new Scanner(client.getBody())) {
                return sc.nextLine();
            }
        });

        String[] listings = list.split("\\}\\{");
        assertNotNull(list);
        assertEquals(10, listings.length);
    }

}
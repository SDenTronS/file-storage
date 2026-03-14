package dev.dentron.filestorage.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dentron.filestorage.api.security.ServiceDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

abstract class AbstractControllerWebMvcTestSupport {
    protected static final String SERVICE_ID = "dev";

    @Autowired
    protected MockMvcTester mockMvc;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected RequestPostProcessor serviceAuthentication() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new ServiceDetails(SERVICE_ID),
                null,
                List.of()
        );
        return authentication(authentication);
    }
}

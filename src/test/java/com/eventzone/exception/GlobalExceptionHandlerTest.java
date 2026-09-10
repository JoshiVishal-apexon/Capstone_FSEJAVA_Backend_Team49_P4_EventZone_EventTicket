package com.eventzone.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void handleApiException_returnsMappedError() {
        var response = handler.handleApiException(new ConflictException("Already exists"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo("CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("Already exists");
    }

    @Test
    void handleValidation_returnsNormalizedFieldError() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("User", "email", "Email is required")));

        var response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Field is required");
    }

    @Test
    void handleConstraintViolation_returnsNormalisedMessage() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("Name must not be blank");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));
        var response = handler.handleConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Field is required");
    }

    @Test
    void handleTypeMismatch_returnsBadRequest() {
        var ex = new MethodArgumentTypeMismatchException("abc", Integer.class, "count", null, null);
        var response = handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Invalid value for request parameter");
    }

    @Test
    void handleNotReadable_returnsMalformedJsonMessage() {
        var response = handler.handleNotReadable(new HttpMessageNotReadableException("Malformed JSON"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Malformed JSON request");
    }

    @Test
    void handleUnsupportedRequest_handlesMethodNotAllowed() {
        var response = handler.handleUnsupportedRequest(new HttpRequestMethodNotSupportedException("GET", List.of("POST")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().error()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void handleUnsupportedRequest_handlesUnsupportedMediaType() {
        var response = handler.handleUnsupportedRequest(new HttpMediaTypeNotSupportedException("application/xml"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().error()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void handleBadCredentials_returnsUnauthorized() {
        var response = handler.handleBadCredentials(new BadCredentialsException("bad creds"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().error()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().message()).isEqualTo("Invalid email or password");
    }

    @Test
    void handleAuthentication_returnsUnauthorized() {
        var response = handler.handleAuthentication(new AuthenticationException("missing token") {
        }, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().error()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().message()).isEqualTo("Authentication required");
    }

    @Test
    void handleAccessDenied_returnsForbidden() {
        var response = handler.handleAccessDenied(new AccessDeniedException("deny"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().error()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().message()).isEqualTo("You do not have permission to perform this action");
    }

    @Test
    void handleDataAccess_returnsDatabaseError() {
        var response = handler.handleDataAccess(new DataAccessResourceFailureException("db down"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("DATABASE_ERROR");
    }

    @Test
    void handleBadRequest_handlesIllegalArgumentAndNoSuchElement() {
        var illegalResponse = handler.handleBadRequest(new IllegalArgumentException("Invalid id"), request);
        var missingResponse = handler.handleBadRequest(new NoSuchElementException("Missing value"), request);

        assertThat(illegalResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(illegalResponse.getBody().message()).isEqualTo("Invalid id");
        assertThat(missingResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingResponse.getBody().message()).isEqualTo("Missing value");
    }

    @Test
    void handleGeneric_returnsInternalError() {
        var response = handler.handleGeneric(new RuntimeException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void normalizeValidationMessage_handlesNullAndRequiredPatterns() {
        assertThat(handler.handleValidation(mockMethodArgumentNotValidException(""), request).getBody().message()).isEqualTo("Validation failed");
        assertThat(handler.handleValidation(mockMethodArgumentNotValidException("Field is required"), request).getBody().message()).isEqualTo("Field is required");
    }

    private MethodArgumentNotValidException mockMethodArgumentNotValidException(String message) {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("User", "name", message)));
        return ex;
    }
}

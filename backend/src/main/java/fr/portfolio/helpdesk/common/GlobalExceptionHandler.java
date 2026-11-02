package fr.portfolio.helpdesk.common;

import fr.portfolio.helpdesk.domain.InvalidTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * Toutes les erreurs sont renvoyées au format RFC 7807 (ProblemDetail),
 * avec un champ "message" lisible par l'interface.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ProblemDetail problem(HttpStatus status, String message) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, message);
        p.setProperty("message", message);
        return p;
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException e) {
        return problem(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(InvalidTransitionException.class)
    ProblemDetail transition(InvalidTransitionException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " : " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return problem(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "Accès refusé");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux (10 Mo maximum)");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Erreur inattendue", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne");
    }
}

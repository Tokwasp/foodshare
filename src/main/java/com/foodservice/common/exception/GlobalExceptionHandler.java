package com.foodservice.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 예외 발생 이름과 원인을 로그로 기록
    private void logException(Exception e) {
        Throwable cause = e.getCause();
        log.error("예외 발생: {} - 원인: {}",
                e.getClass().getSimpleName(),
                cause != null ? cause.getMessage() : e.getMessage(),
                e);
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException e) {
        logException(e);
        ErrorCode errorCode = e.getErrorCode();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                errorCode.getStatus(),
                errorCode.getMessage()
        );
        problem.setTitle(errorCode.name());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException e) {
        logException(e);
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("VALIDATION_FAILED");
        return problem;
    }

    // 파일 업로드 용량 초과 (spring.servlet.multipart 한도 초과)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException e) {
        logException(e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                ErrorCode.FILE_TOO_LARGE.getStatus(),
                ErrorCode.FILE_TOO_LARGE.getMessage()
        );
        problem.setTitle(ErrorCode.FILE_TOO_LARGE.name());
        return problem;
    }

    // multipart 파트 누락 (예: request / expiredImage 파트 미전송)
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail handleMissingPart(MissingServletRequestPartException e) {
        logException(e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "필수 요청 파트가 누락되었습니다: " + e.getRequestPartName()
        );
        problem.setTitle("MISSING_REQUEST_PART");
        return problem;
    }

    // 잘못된 JSON 본문/파트
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleNotReadable(HttpMessageNotReadableException e) {
        logException(e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "요청 본문을 읽을 수 없습니다."
        );
        problem.setTitle("MALFORMED_REQUEST");
        return problem;
    }
}

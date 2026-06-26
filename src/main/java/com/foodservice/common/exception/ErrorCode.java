package com.foodservice.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "메일 발송에 실패했습니다."),
    CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."),
    CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다."),

    //image
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "업로드할 파일이 존재하지 않습니다."),
    INVALID_FILE_FORMAT(HttpStatus.BAD_REQUEST, "올바르지 않은 파일 형식입니다."),
    IMAGE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 저장 중 오류가 발생했습니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 용량이 허용 범위를 초과했습니다."),
    EXPIRED_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "소비기한 사진은 필수입니다."),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 이미지입니다."),
    FORBIDDEN_IMAGE_ACCESS(HttpStatus.FORBIDDEN, "해당 이미지에 대한 권한이 없습니다."),
    EXPIRED_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "유효하지 않은 소비기한 이미지입니다."),

    //food
    FOOD_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 음식입니다."),
    FOOD_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 음식에 대한 권한이 없습니다."),
    FOOD_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "활성 음식 등록 개수 한도를 초과했습니다."),
    FOOD_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "신청 가능한 상태의 음식이 아닙니다."),
    CAPACITY_TOO_SMALL(HttpStatus.BAD_REQUEST, "정원 수는 현재 승인 인원보다 작을 수 없습니다."),
    EXPIRATION_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "소비기한 분석 중 오류가 발생했습니다."),
    INVALID_SORT_FIELD(HttpStatus.BAD_REQUEST, "허용되지 않은 정렬 필드입니다."),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "페이지 크기는 최대 100까지 허용됩니다."),

    //food-request
    FOOD_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 음식 신청입니다."),
    FOOD_REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 신청한 음식입니다."),
    INVALID_REQUEST_STATUS(HttpStatus.CONFLICT, "이미 처리된 신청입니다."),
    FOOD_REQUEST_MISMATCH(HttpStatus.BAD_REQUEST, "해당 음식에 속하지 않는 신청입니다."),

    //chat
    SELF_CHAT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인 물품에는 채팅할 수 없습니다."),
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 채팅방입니다."),
    FORBIDDEN_CHAT_ACCESS(HttpStatus.FORBIDDEN, "해당 채팅방에 대한 권한이 없습니다."),

    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 완료되지 않았습니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");

    private final HttpStatus status;
    private final String message;
}

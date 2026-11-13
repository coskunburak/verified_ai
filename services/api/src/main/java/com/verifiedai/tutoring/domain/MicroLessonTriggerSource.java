package com.verifiedai.tutoring.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

public enum MicroLessonTriggerSource {
    TWIN_SECOND_FAILURE,
    SOCRATIC_RUNG_2,
    COACH_REPAIR_DRAWER;

    public static MicroLessonTriggerSource fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return COACH_REPAIR_DRAWER;
        }
        try {
            return MicroLessonTriggerSource.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.MICRO_LESSON_TRIGGER_NOT_ELIGIBLE,
                    "Unsupported micro-lesson trigger source",
                    false,
                    "Provide a valid trigger source such as TWIN_SECOND_FAILURE, SOCRATIC_RUNG_2, or COACH_REPAIR_DRAWER."
            );
        }
    }
}

package com.verifiedai.studyplan.spaced;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class SpacedQueueItemNotFoundException extends ApiProblemException {

    public SpacedQueueItemNotFoundException(UUID itemId) {
        super(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "Spaced review queue item not found: " + itemId,
                false,
                "CHECK_ITEM_ID"
        );
    }
}

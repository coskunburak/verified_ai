package com.verifiedai.exam.application;

import com.verifiedai.exam.infrastructure.persistence.PreExamStudySessionRepository.Snapshot;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PersonalCheatSheetService {
    private final PreExamReviewService reviews;
    public PersonalCheatSheetService(PreExamReviewService reviews) { this.reviews=reviews; }
    public Snapshot sheet(UUID user, String examCode) { return reviews.review(user,examCode); }
}

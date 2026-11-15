package com.verifiedai.exam.application;

import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusVisionResult;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;

/**
 * Backend multimodal syllabus extraction port.
 * Raw image/PDF bytes are processed ephemerally and must never be marked training eligible.
 */
public interface SyllabusVisionGateway {

    SyllabusVisionResult extractScheduleItems(
            byte[] mediaBytes,
            String detectedMimeType,
            String filenameHint,
            ExamDefinition pinnedExam
    );
}

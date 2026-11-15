package com.verifiedai.exam.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionBankRepository {

    QuestionBankItem save(QuestionBankItem item);

    Optional<QuestionBankItem> findById(UUID id);

    Optional<QuestionBankItem> findByQuestionCode(String questionCode);

    List<QuestionBankItem> findByExamDefinition(
            UUID examDefinitionId,
            Integer examVersionNumber,
            QuestionItemStatus status,
            String topicCode,
            QuestionDifficultyBand difficultyBand
    );

    void incrementExposureCounts(List<UUID> questionItemIds);
}

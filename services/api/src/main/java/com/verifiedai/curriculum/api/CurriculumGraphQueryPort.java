package com.verifiedai.curriculum.api;

import java.util.Optional;

public interface CurriculumGraphQueryPort {
    Optional<CurriculumDagSnapshot> findDagByCurriculumCode(String curriculumCode);
}

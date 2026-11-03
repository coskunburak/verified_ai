"""Evidence-linked semantic paths. Token locations are optional and never guessed from text."""
from app.api.schemas.attempt_v2 import StepDiff, TokenRange
from app.verifiers.attempt_diagnosis import canonical


def value_at(statement,path):
    value=statement.model_dump(exclude_none=True)
    for key in path.strip('/').split('/'):
        if not key: continue
        if isinstance(value,dict) and key in value: value=value[key]
        elif isinstance(value,list) and key.isdigit() and int(key)<len(value): value=value[int(key)]
        else: return None
    return value


def semantic_diff(before,after,path,evidence,index,execution):
    a=value_at(before,path); b=value_at(after,path)
    # Classifier paths that refer to a removed source child cannot locate a submitted term.
    precise=path not in ('/','/relation') and a is not None and b is not None and canonical(a)!=canonical(b)
    return StepDiff(originalStepId=f'{execution}:{index-1}',submittedStepId=f'{execution}:{index}',
        changedExpressionPath=path if precise else '/',originalTokenRange=TokenRange.model_validate(a['tokenRange']) if precise and isinstance(a,dict) and 'tokenRange' in a else None,
        submittedTokenRange=TokenRange.model_validate(b['tokenRange']) if precise and isinstance(b,dict) and 'tokenRange' in b else None,
        diffCategory='PROVEN_TRANSFORMATION',supportingEvidenceId=evidence,
        confidence='EXACT_STRUCTURAL' if precise else 'STEP_ONLY',displayFallback='SUBEXPRESSION' if precise else 'STEP_LEVEL')

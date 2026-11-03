"""Server-only bounded learning artifacts. Correct answers never cross the public drill API."""
from typing import Literal
from uuid import UUID
from pydantic import Field, model_validator
from app.api.schemas.execution import FrozenModel
from app.api.schemas.attempt_v2 import Request as AttemptRequest, Statement, Mistake

class Request(FrozenModel):
    schemaVersion: Literal['attempt-learning-v1'] = 'attempt-learning-v1'
    executionId: UUID
    correlationId: str = Field(max_length=64)
    action: Literal['TWIN','METHODS']
    mistakeCode: Mistake | None = None
    source: AttemptRequest | None = None
    @model_validator(mode='after')
    def binding(self):
        if self.correlationId!=str(self.executionId): raise ValueError('correlation')
        if self.action=='TWIN':
            if self.source is not None or self.mistakeCode in (None,'INVALID_TRANSFORMATION'): raise ValueError('specific diagnosed skill required')
        elif self.source is None or self.mistakeCode is not None: raise ValueError('source required for methods')
        return self

class Twin(FrozenModel):
    templateVersion: Literal['verified-twin-v1'] = 'verified-twin-v1'
    mistakeCode: Mistake
    problem: Statement
    options: list[Statement] = Field(min_length=4,max_length=4)
    correctOption: int = Field(ge=0,le=3)
    explanationKey: str
    domainLabel: str = Field(max_length=120)
    difficulty: Literal['FOUNDATION'] = 'FOUNDATION'

class Method(FrozenModel):
    methodId: Literal['FACTORING','COMPLETING_SQUARE','QUADRATIC_FORMULA','SYMBOLIC_SIMPLIFICATION']
    steps: list[Statement] = Field(min_length=1,max_length=4)
    evidenceVersion: Literal['verified-method-v1'] = 'verified-method-v1'

class Result(FrozenModel):
    schemaVersion: Literal['attempt-learning-result-v1'] = 'attempt-learning-result-v1'
    verifierVersion: Literal['bounded-learning-v1'] = 'bounded-learning-v1'
    executionId: UUID
    correlationId: str
    action: Literal['TWIN','METHODS']
    status: Literal['VERIFIED','UNSUPPORTED','UNAVAILABLE']
    twin: Twin | None = None
    methods: list[Method] = Field(default_factory=list,max_length=3)
    def validate_for(self,request):
        if self.executionId!=request.executionId or self.correlationId!=request.correlationId or self.action!=request.action: raise ValueError('binding')
        if self.status=='VERIFIED' and ((self.action=='TWIN')!=(self.twin is not None) or (self.action=='METHODS')!=bool(self.methods)): raise ValueError('artifact')
        if self.status!='VERIFIED' and (self.twin or self.methods): raise ValueError('unsupported artifact')
        return self

def unavailable(request):
    return Result(executionId=request.executionId,correlationId=request.correlationId,action=request.action,status='UNAVAILABLE')

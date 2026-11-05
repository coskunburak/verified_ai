"""Ephemeral display contract. Samples carry no verification authority."""
from typing import Literal
from uuid import UUID
from pydantic import Field, model_validator, model_serializer
from app.api.schemas.execution import FrozenModel
from app.api.schemas.method_comparison import Source
from app.api.schemas.attempt_v2 import Node

Kind = Literal['FUNCTION', 'EQUATION', 'DERIVATIVE', 'NUMBER_LINE', 'STORY_BRIDGE']
Reason = Literal['BOUNDED_EXACT_MODEL', 'UNSUPPORTED_SOURCE', 'PROVENANCE_UNAVAILABLE', 'TIMEOUT',
                 'RESOURCE_FAILURE', 'WORKER_FAILURE', 'ADMISSION_LIMIT', 'INVALID_RESPONSE', 'DISABLED']
Concept = Literal['APPROXIMATE_SAMPLE', 'ROOT_INTERCEPT', 'POSITIVE_SLOPE', 'NEGATIVE_SLOPE',
                  'ZERO_DERIVATIVE', 'DOMAIN_HOLE', 'QUADRATIC_VERTEX', 'OPEN_BOUNDARY', 'CLOSED_BOUNDARY']

class Identity(FrozenModel):
    canonicalId: UUID
    canonicalRevision: int = Field(ge=1, le=2147483647, strict=True)
    parseId: UUID
    parseRevision: int = Field(ge=1, le=2147483647, strict=True)

class Viewport(FrozenModel):
    xMin: float = Field(ge=-1000, le=1000, allow_inf_nan=False, strict=True)
    xMax: float = Field(ge=-1000, le=1000, allow_inf_nan=False, strict=True)
    yMin: float = Field(ge=-1000, le=1000, allow_inf_nan=False, strict=True)
    yMax: float = Field(ge=-1000, le=1000, allow_inf_nan=False, strict=True)
    @model_validator(mode='after')
    def span(self):
        if not .01 <= self.xMax-self.xMin <= 2000 or not .01 <= self.yMax-self.yMin <= 2000:
            raise ValueError('viewport span')
        return self

class Request(FrozenModel):
    schemaVersion: Literal['math-visualization-request-v1'] = 'math-visualization-request-v1'
    executionId: UUID
    correlationId: str
    identity: Identity
    source: Source
    type: Kind
    viewport: Viewport
    resolution: int = Field(ge=16, le=64, strict=True)
    @model_validator(mode='after')
    def binding(self):
        if self.correlationId != str(self.executionId): raise ValueError('correlation')
        return self

class Sample(FrozenModel):
    x: float = Field(ge=-1000, le=1000, allow_inf_nan=False, strict=True)
    y: float = Field(ge=-1000, le=1000, allow_inf_nan=False, strict=True)

class Segment(FrozenModel):
    id: str = Field(pattern=r'^[fd][0-9]{1,3}$')
    curve: Literal['FUNCTION', 'DERIVATIVE']
    points: list[Sample] = Field(min_length=1, max_length=64)

class Band(FrozenModel):
    lower: str = Field(min_length=1, max_length=96)
    upper: str = Field(min_length=1, max_length=96)
    lowerValue: float | None = Field(ge=-1e12, le=1e12, allow_inf_nan=False, strict=True)
    upperValue: float | None = Field(ge=-1e12, le=1e12, allow_inf_nan=False, strict=True)
    leftOpen: bool
    rightOpen: bool
    @model_validator(mode='after')
    def endpoints(self):
        if (self.lowerValue is None) != (self.lower == '-oo') or (self.upperValue is None) != (self.upper == 'oo'):
            raise ValueError('infinite endpoints')
        if self.lowerValue is None and not self.leftOpen or self.upperValue is None and not self.rightOpen:
            raise ValueError('infinite closure')
        if self.lowerValue is not None and self.upperValue is not None:
            if self.lowerValue > self.upperValue: raise ValueError('order')
            if self.lowerValue == self.upperValue and (self.leftOpen or self.rightOpen): raise ValueError('empty band')
        return self

class Feature(FrozenModel):
    code: Literal['ROOT_INTERCEPT', 'Y_INTERCEPT', 'QUADRATIC_VERTEX', 'SLOPE']
    x: str = Field(min_length=1, max_length=96)
    y: str = Field(min_length=1, max_length=96)

class Result(FrozenModel):
    schemaVersion: Literal['math-visualization-result-v1'] = 'math-visualization-result-v1'
    policyVersion: Literal['math-visualization-policy-v1'] = 'math-visualization-policy-v1'
    executionId: UUID
    correlationId: str
    identity: Identity
    type: Kind
    viewport: Viewport
    resolution: int = Field(ge=16, le=64, strict=True)
    status: Literal['SUPPORTED', 'UNSUPPORTED', 'UNAVAILABLE']
    reasonCode: Reason
    expression: Node | None = None
    derivativeExpression: Node | None = None
    naturalDomain: Node | None = None
    sourceDomain: Node | None = None
    solutionSet: Node | None = None
    numberLine: list[Band] = Field(default_factory=list, max_length=24)
    features: list[Feature] = Field(default_factory=list, max_length=12)
    segments: list[Segment] = Field(default_factory=list, max_length=128)
    concepts: list[Concept] = Field(default_factory=list, max_length=9)
    axes: Literal['X_Y_UNITLESS'] = 'X_Y_UNITLESS'
    sampleSemantics: Literal['APPROXIMATE_DISPLAY_ONLY'] = 'APPROXIMATE_DISPLAY_ONLY'
    trainingEligible: Literal[False] = False
    @model_validator(mode='after')
    def coherent(self):
        if self.correlationId != str(self.executionId): raise ValueError('correlation')
        reasons={'SUPPORTED': {'BOUNDED_EXACT_MODEL'}, 'UNSUPPORTED': {'UNSUPPORTED_SOURCE','PROVENANCE_UNAVAILABLE'},
                 'UNAVAILABLE': {'TIMEOUT','RESOURCE_FAILURE','WORKER_FAILURE','ADMISSION_LIMIT','INVALID_RESPONSE','DISABLED'}}
        if self.reasonCode not in reasons[self.status]: raise ValueError('reason')
        if self.status != 'SUPPORTED':
            if any((self.expression,self.derivativeExpression,self.naturalDomain,self.sourceDomain,self.solutionSet,self.numberLine,self.features,self.segments,self.concepts)):
                raise ValueError('unsupported artifact')
        elif self.naturalDomain is None or self.sourceDomain is None: raise ValueError('domain')
        if self.status == 'SUPPORTED':
            if self.type == 'STORY_BRIDGE' or self.type != 'NUMBER_LINE' and self.expression is None: raise ValueError('model type')
            if self.type == 'NUMBER_LINE' and self.segments: raise ValueError('set curves')
            if (self.derivativeExpression is not None) != (self.type == 'DERIVATIVE'): raise ValueError('derivative scope')
        for domain in (self.naturalDomain,self.sourceDomain,self.solutionSet):
            if domain is not None and domain.kind not in {'REALS','EMPTY_SET','FINITE_SET','INTERVAL','UNION','INTERSECTION'}: raise ValueError('set required')
        if len({v.id for v in self.segments}) != len(self.segments): raise ValueError('duplicate segments')
        if sum(len(v.points) for v in self.segments) > 2*self.resolution: raise ValueError('sample budget')
        if len(set(self.concepts)) != len(self.concepts): raise ValueError('duplicate concepts')
        for segment in self.segments:
            if segment.curve == 'DERIVATIVE' and self.type != 'DERIVATIVE': raise ValueError('curve')
            if any(a.x >= b.x for a,b in zip(segment.points,segment.points[1:])): raise ValueError('order')
            for p in segment.points:
                if not self.viewport.xMin <= p.x <= self.viewport.xMax or not self.viewport.yMin <= p.y <= self.viewport.yMax:
                    raise ValueError('clipping')
        return self
    @model_serializer(mode='wrap')
    def compact(self, handler):
        def trim(value):
            if isinstance(value, dict): return {k:trim(v) for k,v in value.items() if v is not None or 'kind' not in value}
            if isinstance(value, list): return [trim(v) for v in value]
            return value
        return trim(handler(self))
    def validate_for(self, request):
        for field in ('executionId','correlationId','identity','type','viewport','resolution'):
            if getattr(self,field) != getattr(request,field): raise ValueError('request binding')
        return self

def outcome(request, status='UNAVAILABLE', reason='WORKER_FAILURE', **values):
    return Result(**{k:getattr(request,k) for k in ('executionId','correlationId','identity','type','viewport','resolution')},
                  status=status,reasonCode=reason,**values)

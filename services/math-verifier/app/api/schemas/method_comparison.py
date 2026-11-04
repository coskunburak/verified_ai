"""Versioned source-only extension of attempt learning; no student verdict fields."""
from typing import Literal
from uuid import UUID
from pydantic import Field, model_validator, model_serializer
from app.api.schemas.execution import FrozenModel, Variable
from app.api.schemas.attempt_v2 import Statement, Restriction, Node

# This policy is deliberately finite and shared by generation and evidence validation.
POLICY = {
    'FACTORING': ('ZERO_PRODUCT','FACTORING','VISIBLE_FACTORS','FACTOR_DISCOVERY','LOW'),
    'COMPLETING_SQUARE': ('SQUARE_IDENTITY','SQUARE_IDENTITIES','VISIBLE_VERTEX','FRACTION_ARITHMETIC','MODERATE'),
    'QUADRATIC_FORMULA': ('DISCRIMINANT_FORMULA','QUADRATIC_FORMULA','GENERAL_QUADRATIC','FORMULA_SUBSTITUTION','MODERATE'),
    'SYMBOLIC_SIMPLIFICATION': ('EXACT_SIMPLIFICATION','POLYNOMIAL_ALGEBRA','NORMALIZED_EXPRESSION','BOUNDED_POLYNOMIAL','LOW'),
    'POLYNOMIAL_EXPANSION': ('EXPAND_COLLECT','POLYNOMIAL_ALGEBRA','VISIBLE_COEFFICIENTS','EXPANSION_GROWTH','LOW'),
    'DIRECT_ALGEBRA': ('ISOLATE_VARIABLE','LINEAR_ALGEBRA','DIRECT_OUTCOME','LINEAR_ONLY','LOW'),
    'RATIONAL_CLEARING': ('CLEAR_DENOMINATORS','DOMAIN_AND_FACTORS','VISIBLE_NUMERATOR','EXCLUDED_ROOTS','MODERATE'),
    'ABSOLUTE_CASES': ('SPLIT_ABSOLUTE_CASES','PIECEWISE_SIGNS','EXPLICIT_BRANCHES','AFFINE_ABSOLUTE_ONLY','MODERATE')}
RECOMMENDATIONS = {
    'FACTORING': {'REPEATED_ROOT_STRUCTURE','FACTORABLE_OVER_RATIONALS'},
    'COMPLETING_SQUARE': {'NEGATIVE_DISCRIMINANT','DIRECT_SQUARE','VERTEX_FORM'},
    'QUADRATIC_FORMULA': {'EXACT_RADICAL_FORMULA','EQUALLY_VALID'},
    'SYMBOLIC_SIMPLIFICATION': {'EXPOSE_FACTORS'}, 'POLYNOMIAL_EXPANSION': {'COLLECT_TERMS'},
    'DIRECT_ALGEBRA': {'ISOLATE_LINEAR','RESOLVE_CONSTANT'},
    'RATIONAL_CLEARING': {'PRESERVE_DOMAIN_HOLES'}, 'ABSOLUTE_CASES': {'SPLIT_ABSOLUTE_CASES'}}
APPLICABILITY = {
    'FACTORING': {'RATIONAL_QUADRATIC'}, 'COMPLETING_SQUARE': {'RATIONAL_QUADRATIC'}, 'QUADRATIC_FORMULA': {'RATIONAL_QUADRATIC'},
    'SYMBOLIC_SIMPLIFICATION': {'RATIONAL_POLYNOMIAL'}, 'POLYNOMIAL_EXPANSION': {'RATIONAL_POLYNOMIAL'},
    'DIRECT_ALGEBRA': {'RATIONAL_LINEAR','CONSTANT_EQUATION'}, 'RATIONAL_CLEARING': {'RATIONAL_EQUATION'}, 'ABSOLUTE_CASES': {'ABSOLUTE_AFFINE'}}
MethodId = Literal['FACTORING','COMPLETING_SQUARE','QUADRATIC_FORMULA','SYMBOLIC_SIMPLIFICATION','POLYNOMIAL_EXPANSION','DIRECT_ALGEBRA','RATIONAL_CLEARING','ABSOLUTE_CASES']
Reason = Literal['ZERO_PRODUCT','SQUARE_IDENTITY','DISCRIMINANT_FORMULA','EXACT_SIMPLIFICATION','EXPAND_COLLECT','ISOLATE_VARIABLE','CLEAR_DENOMINATORS','SPLIT_ABSOLUTE_CASES']

class Source(FrozenModel):
    original: Statement
    variables: list[Variable] = Field(max_length=1)
    restrictions: list[Restriction] = Field(max_length=48)
    domainBasis: Literal['SOURCE_EXPLICIT','REVIEWED_REAL_TASK','UNRESOLVED']
    @model_validator(mode='after')
    def bounded(self):
        def visit(v,depth=0):
            if depth>40: raise ValueError('depth')
            if isinstance(v,dict): return 1+sum(visit(c,depth+1) for c in v.values())
            if isinstance(v,list): return sum(visit(c,depth+1) for c in v)
            return 0
        if visit(self.model_dump())>1800: raise ValueError('size')
        return self

class Request(FrozenModel):
    schemaVersion: Literal['method-comparison-v2'] = 'method-comparison-v2'
    executionId: UUID
    correlationId: str = Field(max_length=64)
    action: Literal['METHODS'] = 'METHODS'
    source: Source
    @model_validator(mode='after')
    def binding(self):
        if self.correlationId!=str(self.executionId): raise ValueError('correlation')
        return self

class Comparison(FrozenModel):
    policyVersion: Literal['method-comparison-policy-v3'] = 'method-comparison-policy-v3'
    presentationVersion: Literal['method-presentation-v2'] = 'method-presentation-v2'
    applicability: Literal['RATIONAL_QUADRATIC','RATIONAL_POLYNOMIAL','RATIONAL_LINEAR','CONSTANT_EQUATION','RATIONAL_EQUATION','ABSOLUTE_AFFINE']
    reasonCode: Reason
    prerequisite: Literal['FACTORING','SQUARE_IDENTITIES','QUADRATIC_FORMULA','POLYNOMIAL_ALGEBRA','LINEAR_ALGEBRA','DOMAIN_AND_FACTORS','PIECEWISE_SIGNS']
    advantage: Literal['VISIBLE_FACTORS','VISIBLE_VERTEX','GENERAL_QUADRATIC','NORMALIZED_EXPRESSION','VISIBLE_COEFFICIENTS','DIRECT_OUTCOME','VISIBLE_NUMERATOR','EXPLICIT_BRANCHES']
    limitation: Literal['FACTOR_DISCOVERY','FRACTION_ARITHMETIC','FORMULA_SUBSTITUTION','BOUNDED_POLYNOMIAL','EXPANSION_GROWTH','LINEAR_ONLY','EXCLUDED_ROOTS','AFFINE_ABSOLUTE_ONLY']
    domainCautions: list[Literal['REAL_DOMAIN','SOURCE_RESTRICTIONS','NATURAL_DOMAIN_EXCLUSIONS']] = Field(min_length=1,max_length=3)
    structuralStepCount: int = Field(ge=1,le=4)
    compactStepIndices: list[int] = Field(min_length=1,max_length=4)
    manipulationLevel: Literal['LOW','MODERATE']
    recommendation: Literal['REPEATED_ROOT_STRUCTURE','FACTORABLE_OVER_RATIONALS','NEGATIVE_DISCRIMINANT','DIRECT_SQUARE','VERTEX_FORM','EXACT_RADICAL_FORMULA','EQUALLY_VALID','EXPOSE_FACTORS','COLLECT_TERMS','ISOLATE_LINEAR','RESOLVE_CONSTANT','PRESERVE_DOMAIN_HOLES','SPLIT_ABSOLUTE_CASES']

class Method(FrozenModel):
    methodId: MethodId
    evidenceVersion: Literal['verified-method-v2'] = 'verified-method-v2'
    steps: list[Statement] = Field(min_length=1,max_length=4)
    stepReasons: list[Literal['NORMALIZE_ZERO','ZERO_PRODUCT','SQUARE_IDENTITY','DISCRIMINANT_FORMULA','COMPLETE_REAL_SET','EXACT_SIMPLIFICATION','EXPAND_COLLECT','ISOLATE_VARIABLE','CLEAR_DENOMINATORS','SPLIT_ABSOLUTE_CASES']] = Field(min_length=1,max_length=4)
    proofScope: Literal['SOURCE_DOMAIN_COMPLETE_OUTCOME'] = 'SOURCE_DOMAIN_COMPLETE_OUTCOME'
    transitionStatus: Literal['EXACT_SYMBOLIC'] = 'EXACT_SYMBOLIC'
    outcomeStatus: Literal['COMPLETE_SOLUTION_SET','EXPRESSION_IDENTITY']
    comparison: Comparison
    @model_validator(mode='after')
    def coherent(self):
        c=self.comparison; indices=c.compactStepIndices
        if c.structuralStepCount!=len(self.steps) or len(self.stepReasons)!=len(self.steps): raise ValueError('count')
        if indices!=sorted(set(indices)) or indices[0]<0 or indices[-1]!=len(self.steps)-1: raise ValueError('compact scope')
        if (c.reasonCode,c.prerequisite,c.advantage,c.limitation,c.manipulationLevel)!=POLICY[self.methodId]: raise ValueError('method policy')
        expression=self.methodId in {'SYMBOLIC_SIMPLIFICATION','POLYNOMIAL_EXPANSION'}
        if self.outcomeStatus!=('EXPRESSION_IDENTITY' if expression else 'COMPLETE_SOLUTION_SET'): raise ValueError('outcome kind')
        if c.applicability not in APPLICABILITY[self.methodId] or c.recommendation not in RECOMMENDATIONS[self.methodId]: raise ValueError('contextual policy')
        if self.stepReasons[-1]!=(c.reasonCode if expression else 'COMPLETE_REAL_SET'): raise ValueError('final justification')
        return self

class Result(FrozenModel):
    schemaVersion: Literal['method-comparison-result-v2'] = 'method-comparison-result-v2'
    verifierVersion: Literal['bounded-methods-v2'] = 'bounded-methods-v2'
    executionId: UUID
    correlationId: str = Field(max_length=64)
    action: Literal['METHODS'] = 'METHODS'
    status: Literal['VERIFIED','UNSUPPORTED','UNAVAILABLE','NO_ALTERNATIVE']
    reasonCode: Literal['EXACT_METHODS','UNSUPPORTED_SOURCE','NO_DISTINCT_DERIVATION','TIMEOUT','RESOURCE_FAILURE','WORKER_FAILURE','ADMISSION_LIMIT','INVALID_RESPONSE','DISABLED']
    source: Source
    naturalDomain: Node | None = None
    methods: list[Method] = Field(default_factory=list,max_length=3)
    @model_validator(mode='after')
    def coherent(self):
        if (self.status=='VERIFIED')!=bool(self.methods): raise ValueError('artifact')
        if len({m.methodId for m in self.methods})!=len(self.methods): raise ValueError('duplicate method')
        expected={'VERIFIED':{'EXACT_METHODS'},'UNSUPPORTED':{'UNSUPPORTED_SOURCE'},'NO_ALTERNATIVE':{'NO_DISTINCT_DERIVATION'},
                  'UNAVAILABLE':{'TIMEOUT','RESOURCE_FAILURE','WORKER_FAILURE','ADMISSION_LIMIT','INVALID_RESPONSE','DISABLED'}}
        if self.reasonCode not in expected[self.status]: raise ValueError('status reason')
        if self.status=='VERIFIED' and (self.naturalDomain is None or self.source.domainBasis=='UNRESOLVED'): raise ValueError('scope')
        if self.methods:
            if self.naturalDomain.kind not in {'REALS','FINITE_SET','INTERVAL','UNION','INTERSECTION'}: raise ValueError('natural domain')
            cautions=['REAL_DOMAIN']+(['SOURCE_RESTRICTIONS'] if self.source.restrictions else [])+(['NATURAL_DOMAIN_EXCLUSIONS'] if self.naturalDomain.kind!='REALS' else [])
            for method in self.methods:
                if method.comparison.domainCautions!=cautions: raise ValueError('domain warnings')
        return self
    @model_serializer(mode='wrap')
    def compact_wire(self,handler):
        def trim(value):
            if isinstance(value,dict):
                return {k:trim(v) for k,v in value.items() if v is not None or 'kind' not in value}
            if isinstance(value,list): return [trim(v) for v in value]
            return value
        return trim(handler(self))
    def validate_for(self,request):
        if self.executionId!=request.executionId or self.correlationId!=request.correlationId or self.source!=request.source: raise ValueError('source binding')
        return self

def unavailable(request,reason='WORKER_FAILURE'):
    return Result(executionId=request.executionId,correlationId=request.correlationId,source=request.source,status='UNAVAILABLE',reasonCode=reason)

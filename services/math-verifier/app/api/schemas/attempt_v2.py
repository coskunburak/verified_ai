"""Additive canonical_v2 boundary. Legacy attempts remain interpreted by v1."""
from __future__ import annotations
from typing import Literal
from uuid import UUID
from pydantic import Field, model_validator
from app.api.schemas.execution import FrozenModel, Variable
from app.api.schemas.attempt import CounterexampleEvidence, Reason as LegacyReason


class TokenRange(FrozenModel):
    start: int = Field(ge=0,le=256)
    end: int = Field(ge=1,le=256)
    @model_validator(mode='after')
    def ordered(self):
        if self.end<=self.start: raise ValueError('range')
        return self


class Node(FrozenModel):
    kind: Literal['NUMBER','VARIABLE','CONSTANT','UNARY','BINARY','FUNCTION','FINITE_SET','EMPTY_SET','REALS','INTERVAL','UNION','INTERSECTION','PIECEWISE']
    value: str | None = Field(default=None, max_length=80)
    numericType: Literal['INTEGER','DECIMAL','RATIONAL'] | None = None
    symbol: str | None = Field(default=None, pattern=r'^[A-Za-z][A-Za-z0-9_]{0,15}$')
    operator: Literal['NEGATE','ADD','SUBTRACT','MULTIPLY','DIVIDE','POWER'] | None = None
    function: Literal['SQRT','ABS','SIN','COS','TAN','LOG','EXP','DIFF','LIMIT','INTEGRAL','TOTAL'] | None = None
    operand: Node | None = None
    left: Node | None = None
    right: Node | None = None
    args: list[Node] | None = Field(default=None, min_length=1, max_length=12)
    leftOpen: bool | None = None
    rightOpen: bool | None = None
    tokenRange: TokenRange | None = None
    branches: list[Branch] | None = Field(default=None, min_length=1, max_length=6)

    @model_validator(mode='after')
    def shape(self):
        present={k for k,v in self.model_dump().items() if v is not None}-{'kind','tokenRange'}
        allowed={
            'NUMBER':({'value','numericType'},{'value','numericType'}), 'VARIABLE':({'symbol'},{'symbol'}),
            'CONSTANT':({'value'},{'value'}), 'UNARY':({'operator','operand'},{'operator','operand'}),
            'BINARY':({'operator','left','right'},{'operator','left','right'}),
            'FUNCTION':({'function','args'},{'function','args'}),
            'FINITE_SET':({'args'},{'args'}), 'EMPTY_SET':(set(),set()), 'REALS':(set(),set()),
            'INTERVAL':({'left','right','leftOpen','rightOpen'},{'left','right','leftOpen','rightOpen'}),
            'UNION':({'args'},{'args'}),'INTERSECTION':({'args'},{'args'}),'PIECEWISE':({'branches'},{'branches'})}[self.kind]
        if not allowed[1]<=present<=allowed[0]: raise ValueError('invalid canonical node shape')
        if self.kind=='UNARY' and self.operator!='NEGATE': raise ValueError('invalid unary')
        if self.kind=='BINARY' and self.operator=='NEGATE': raise ValueError('invalid binary')
        if self.kind=='FUNCTION' and len(self.args) not in ({'LOG':(1,2),'DIFF':(1,),'LIMIT':(2,),'INTEGRAL':(3,),'TOTAL':(2,)}.get(self.function,(1,))): raise ValueError('invalid function arity')
        if self.kind=='CONSTANT' and self.value not in {'PI','POSITIVE_INFINITY','NEGATIVE_INFINITY'}: raise ValueError('invalid constant')
        return self


class Statement(FrozenModel):
    kind: Literal['EXPRESSION','RELATION']
    expression: Node | None = None
    relation: Literal['EQUALS','NOT_EQUALS','LESS_THAN','LESS_THAN_OR_EQUAL','GREATER_THAN','GREATER_THAN_OR_EQUAL','MEMBER_OF'] | None = None
    left: Node | None = None
    right: Node | None = None
    @model_validator(mode='after')
    def shape(self):
        if self.kind=='EXPRESSION':
            valid=self.expression is not None and self.left is None and self.right is None and self.relation is None
        else: valid=self.expression is None and self.left is not None and self.right is not None and self.relation is not None
        if not valid: raise ValueError('invalid statement shape')
        return self


class Branch(FrozenModel):
    expression: Node
    condition: Statement | None  # null means otherwise, and must be last


class Restriction(FrozenModel):
    id: str = Field(min_length=1,max_length=96)
    relation: Literal['EQUALS','NOT_EQUALS','LESS_THAN','LESS_THAN_OR_EQUAL','GREATER_THAN','GREATER_THAN_OR_EQUAL']
    left: Node
    right: Node


class Step(FrozenModel):
    statement: Statement | None


class Request(FrozenModel):
    schemaVersion: Literal['attempt-verification-v2']
    executionId: UUID
    correlationId: str = Field(pattern=r'^[A-Za-z0-9_-]{1,64}$')
    original: Statement
    variables: list[Variable] = Field(max_length=1)
    restrictions: list[Restriction] = Field(max_length=48)
    steps: list[Step] = Field(min_length=1,max_length=12)
    domainBasis: Literal['SOURCE_EXPLICIT','REVIEWED_REAL_TASK','UNRESOLVED']

    @model_validator(mode='after')
    def bounded(self):
        def visit(value,depth=0):
            if depth>40: raise ValueError('canonical depth bound')
            if isinstance(value,dict): return 1+sum(visit(v,depth+1) for v in value.values())
            if isinstance(value,list): return sum(visit(v,depth+1) for v in value)
            return 0
        if visit(self.model_dump())>1800: raise ValueError('canonical size bound')
        return self

Node.model_rebuild()


Mistake = Literal['INVALID_TRANSFORMATION','ALG_SIGN_FLIP_DISTRIBUTE','ALG_FRESHMAN_DREAM_POWER','ALG_FRACTION_ADD_DENOM',
    'ALG_ROOT_LOST_BY_DIVISION','DOM_EXTRANEOUS_ROOT_DENOM','DOM_EVEN_ROOT_NEGATIVE','DOM_LOG_NON_POSITIVE',
    'INEQ_NEGATIVE_MULT_NO_FLIP','INEQ_CROSS_MULT_VARIABLE','ABS_SINGLE_BRANCH_ONLY','FUNC_COMPOSITION_ORDER','TRIG_PERIOD_OMITTED',
    'CALC_LIMIT_INDETERMINATE_SUB','CALC_CHAIN_RULE_OMITTED','CALC_PRODUCT_QUOTIENT_NAIVE','CALC_INTEGRAL_BOUNDS_SWAPPED','WORD_VARIABLE_MISASSIGNMENT']

class Witness(CounterexampleEvidence):
    verifierVersion: Literal['bounded-attempt-v2'] = 'bounded-attempt-v2'


class Diagnosis(FrozenModel):
    code: Mistake
    evidenceId: str = Field(max_length=96)
    explanationKey: str = Field(max_length=96)
    changedExpressionPath: str = Field(max_length=160)
    displayFallback: Literal['STEP_LEVEL','SUBEXPRESSION']

class StepDiff(FrozenModel):
    schemaVersion: Literal['step-diff-v1'] = 'step-diff-v1'
    originalStepId: str = Field(max_length=96)
    submittedStepId: str = Field(max_length=96)
    changedExpressionPath: str = Field(max_length=160)
    originalTokenRange: TokenRange | None
    submittedTokenRange: TokenRange | None
    diffCategory: Literal['PROVEN_TRANSFORMATION']
    supportingEvidenceId: str = Field(max_length=96)
    confidence: Literal['EXACT_STRUCTURAL','STEP_ONLY']
    displayFallback: Literal['SUBEXPRESSION','STEP_LEVEL']


class ResultStep(FrozenModel):
    index: int = Field(ge=1,le=12)
    verdict: Literal['VALID_EQUIVALENT','FIRST_WRONG_STEP','UNCERTAIN','NOT_ASSESSED']
    reasonCode: LegacyReason | Literal['UNKNOWN_DOMAIN','UNDEFINED_SOURCE']
    mistakeCode: Mistake | None = None
    counterexampleStatus: Literal['AVAILABLE','COUNTEREXAMPLE_UNAVAILABLE','NOT_APPLICABLE'] = 'NOT_APPLICABLE'
    counterexample: Witness | None = None
    diagnosis: Diagnosis | None = None
    diff: StepDiff | None = None


def validate_wire_set(value,depth=0):
    if value is None and depth==0: return
    if not isinstance(value,dict) or depth>8: raise ValueError('set envelope')
    kind=value.get('kind')
    fields={'EMPTY_SET':{'kind'},'REALS':{'kind'},'FINITE_SET':{'kind','elements'},
        'INTERVAL':{'kind','start','end','leftOpen','rightOpen'},'UNION':{'kind','sets'},'INTERSECTION':{'kind','sets'}}
    if kind not in fields or set(value)!=fields[kind]: raise ValueError('set shape')
    def scalar(text):
        if not isinstance(text,str) or not 1<=len(text)<=512: raise ValueError('set scalar')
    if kind=='FINITE_SET':
        if not isinstance(value['elements'],list) or not 1<=len(value['elements'])<=24: raise ValueError('set bound')
        for element in value['elements']: scalar(element)
    elif kind=='INTERVAL':
        scalar(value['start']); scalar(value['end'])
        if type(value['leftOpen']) is not bool or type(value['rightOpen']) is not bool: raise ValueError('interval endpoints')
    elif kind in {'UNION','INTERSECTION'}:
        if not isinstance(value['sets'],list) or not 2<=len(value['sets'])<=24: raise ValueError('set bound')
        for child in value['sets']: validate_wire_set(child,depth+1)


class Result(FrozenModel):
    schemaVersion: Literal['attempt-analysis-v2'] = 'attempt-analysis-v2'
    verifierVersion: Literal['bounded-attempt-v2'] = 'bounded-attempt-v2'
    taxonomyVersion: Literal['attempt-taxonomy-v2'] = 'attempt-taxonomy-v2'
    executionId: UUID
    correlationId: str
    outcome: Literal['INCOMPLETE_VALID_SO_FAR','STEP_ERROR_FOUND','NEEDS_REVIEW']
    firstWrongStepIndex: int | None = None
    domain: Literal['REAL_DOMAIN','UNSUPPORTED']
    steps: list[ResultStep] = Field(min_length=1,max_length=12)
    naturalDomain: dict | None = None
    domainBasis: Literal['SOURCE_EXPLICIT','REVIEWED_REAL_TASK','UNRESOLVED']
    solutionSet: dict | None = None
    @model_validator(mode='after')
    def consistent(self):
        validate_wire_set(self.naturalDomain); validate_wire_set(self.solutionSet)
        if self.domainBasis=='UNRESOLVED' and self.domain=='REAL_DOMAIN': raise ValueError('unknown domain')
        stop=None; wrong=None
        for i,step in enumerate(self.steps,1):
            if step.index!=i: raise ValueError('index')
            if bool(stop)!=(step.verdict=='NOT_ASSESSED'): raise ValueError('prefix')
            if stop and step.reasonCode!=stop: raise ValueError('stop reason')
            if step.verdict=='VALID_EQUIVALENT' and (self.domain!='REAL_DOMAIN' or step.reasonCode not in {'EXACT_IDENTITY','SAME_SOLUTION_SET'}): raise ValueError('proof reason')
            if step.verdict=='UNCERTAIN' and step.reasonCode in {'EXACT_IDENTITY','SAME_SOLUTION_SET','NON_EQUIVALENT','EARLIER_DIVERGENCE','EARLIER_UNCERTAINTY'}: raise ValueError('uncertainty reason')
            if step.verdict!='FIRST_WRONG_STEP' and (step.counterexampleStatus!='NOT_APPLICABLE' or step.diff is not None or step.diagnosis is not None or step.mistakeCode is not None): raise ValueError('unproved evidence')
            if step.verdict=='FIRST_WRONG_STEP':
                if self.domain!='REAL_DOMAIN' or step.reasonCode!='NON_EQUIVALENT' or step.counterexampleStatus=='NOT_APPLICABLE': raise ValueError('disproof reason')
                wrong=i; stop='EARLIER_DIVERGENCE'
                if not step.mistakeCode or step.diagnosis is None: raise ValueError('missing diagnosis')
                diagnosis=step.diagnosis
                if diagnosis.code!=step.mistakeCode or diagnosis.evidenceId!=f'{self.executionId}:{i}' or diagnosis.explanationKey!='attempt.mistake.'+diagnosis.code: raise ValueError('diagnosis binding')
                if step.diff and (step.diff.supportingEvidenceId!=diagnosis.evidenceId or step.diff.changedExpressionPath!=diagnosis.changedExpressionPath or step.diff.displayFallback!=diagnosis.displayFallback): raise ValueError('diff binding')
            elif step.verdict=='UNCERTAIN': stop='EARLIER_UNCERTAINTY'
            elif step.mistakeCode or step.diagnosis: raise ValueError('unsupported diagnosis')
            if (step.counterexampleStatus=='AVAILABLE')!=(step.counterexample is not None): raise ValueError('witness')
            if step.counterexample is not None and step.verdict!='FIRST_WRONG_STEP': raise ValueError('witness without disproof')
        expected='STEP_ERROR_FOUND' if wrong else 'NEEDS_REVIEW' if stop else 'INCOMPLETE_VALID_SO_FAR'
        if self.firstWrongStepIndex!=wrong or self.outcome!=expected: raise ValueError('outcome')
        return self
    def validate_for(self,request):
        if self.executionId!=request.executionId or self.correlationId!=request.correlationId or len(self.steps)!=len(request.steps) or self.domainBasis!=request.domainBasis: raise ValueError('binding')
        return self


def unavailable(request,reason='WORKER_FAILURE'):
    return Result(executionId=request.executionId,correlationId=request.correlationId,outcome='NEEDS_REVIEW',
        domain='UNSUPPORTED',domainBasis=request.domainBasis,steps=[ResultStep(index=i,verdict='UNCERTAIN' if i==1 else 'NOT_ASSESSED',
        reasonCode=reason if i==1 else 'EARLIER_UNCERTAINTY') for i in range(1,len(request.steps)+1)])

"""Canonical real-domain attempt analysis, isolated by the existing process supervisor."""
import sympy as s
from app.api.schemas.attempt_v2 import Result, ResultStep, Diagnosis, Statement, unavailable
from app.verifiers.canonical_v2 import DomainConstraintGuard, Unsupported, concrete, relation_set, compare, probes, wire_set, RELATIONS
from app.verifiers.attempt_diagnosis import classify
from app.verifiers.attempt_diff import semantic_diff


def execute(request):
    if request.domainBasis=='UNRESOLVED' or any(v.domain!='REAL' for v in request.variables): return unavailable(request,'UNKNOWN_DOMAIN')
    x=s.Symbol(request.variables[0].symbol if request.variables else '_constant',real=True)
    guard=DomainConstraintGuard(x)
    try:
        restriction=s.S.Reals
        for r in request.restrictions:
            _,allowed,_=guard.statement(Statement(kind='RELATION',relation=r.relation,left=r.left,right=r.right))
            restriction=concrete(restriction.intersect(allowed))
        if restriction==s.S.EmptySet: return unavailable(request,"UNDEFINED_SOURCE")
        guard.scope=restriction
        _,source_set,natural=guard.statement(request.original)
        natural=concrete(natural.intersect(restriction))
        if natural==s.S.EmptySet: return unavailable(request,'UNDEFINED_SOURCE')
        solution=wire_set(source_set.intersect(restriction)) if request.original.kind=='RELATION' else None
    except (ValueError,NotImplementedError,TypeError,ZeroDivisionError): return unavailable(request,'UNSUPPORTED_MATH')
    steps=[]; previous=request.original; stop=None; first=None
    for i,line in enumerate(request.steps,1):
        if stop:
            steps.append(ResultStep(index=i,verdict='NOT_ASSESSED',reasonCode=stop)); continue
        try:
            if line.statement is None: raise Unsupported()
            equal,sa,sb,da,db=compare(guard,previous,line.statement,restriction)
            if equal:
                steps.append(ResultStep(index=i,verdict='VALID_EQUIVALENT',reasonCode='SAME_SOLUTION_SET' if previous.kind=='RELATION' else 'EXACT_IDENTITY'))
                previous=line.statement; continue
            try: code,path=classify(previous,line.statement,guard,sa,sb,da,db)
            except (ValueError,NotImplementedError,TypeError,ZeroDivisionError): code,path="INVALID_TRANSFORMATION","/"
            evidence=str(request.executionId)+':'+str(i)
            diff=semantic_diff(previous,line.statement,path,evidence,i,request.executionId)
            proof=None
            # A witness is optional; the disproof has already been established independently.
            candidates=probes()
            for values in (sa-sb,sb-sa):
                if isinstance(values,s.FiniteSet): candidates+=sorted(values,key=s.default_sort_key)
            for value in candidates:
                if value.is_Rational is not True or natural.contains(value) is not s.true: continue
                if previous.kind=='RELATION':
                    a,b=sa.contains(value),sb.contains(value)
                    if a not in (s.true,s.false) or b not in (s.true,s.false) or a==b: continue
                    av,bv=('true' if a else 'false'),('true' if b else 'false'); kind='EQUATION_TRUTH'
                else:
                    if da.contains(value) is not s.true or db.contains(value) is not s.true: continue
                    a=guard.expression(previous.expression)[0].subs(x,value); b=guard.expression(line.statement.expression)[0].subs(x,value)
                    if not a.is_Rational or not b.is_Rational or a==b: continue
                    av,bv=str(a),str(b); kind='EXPRESSION_VALUES'
                if max(len(av),len(bv),len(str(value.p)),len(str(value.q)))>320: continue
                proof={'schemaVersion':'attempt-witness-v1','witnessType':kind,'variableValues':{str(x):{'numerator':str(value.p),'denominator':str(value.q)}} if request.variables else {},
                    'previousStepEvaluation':av,'submittedStepEvaluation':bv,'claimRefuted':'EQUATION_EQUIVALENCE' if kind=='EQUATION_TRUTH' else 'EXPRESSION_IDENTITY',
                    'domainSatisfied':True,'verifierVersion':'bounded-attempt-v2','explanationCode':'DIFFERENT_TRUTH' if kind=='EQUATION_TRUTH' else 'UNEQUAL_VALUES'}
                break
            steps.append(ResultStep(index=i,verdict='FIRST_WRONG_STEP',reasonCode='NON_EQUIVALENT',mistakeCode=code,
                diagnosis=Diagnosis(code=code,evidenceId=evidence,explanationKey='attempt.mistake.'+code,changedExpressionPath=diff.changedExpressionPath,displayFallback=diff.displayFallback),diff=diff,
                counterexample=proof,counterexampleStatus='AVAILABLE' if proof else 'COUNTEREXAMPLE_UNAVAILABLE'))
            first=i; stop='EARLIER_DIVERGENCE'
        except (ValueError,NotImplementedError,TypeError,ZeroDivisionError):
            steps.append(ResultStep(index=i,verdict='UNCERTAIN',reasonCode='UNSUPPORTED_MATH')); stop='EARLIER_UNCERTAINTY'
    return Result(executionId=request.executionId,correlationId=request.correlationId,domain='REAL_DOMAIN',domainBasis=request.domainBasis,
        naturalDomain=wire_set(natural),solutionSet=solution,steps=steps,firstWrongStepIndex=first,
        outcome='STEP_ERROR_FOUND' if first else 'NEEDS_REVIEW' if stop else 'INCOMPLETE_VALID_SO_FAR')

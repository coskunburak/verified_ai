#!/usr/bin/env python3
"""Owner-only semantic golden evaluation. No provider or network operations."""
import hashlib
import json
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'services/math-verifier'))
from app.api.schemas.math_visualization import Request
from app.verifiers.math_visualization import execute, model
import sympy as s

def main():
    path=ROOT/'evaluations/golden-datasets/visualizations/bounded-real-v1.json'
    data=json.loads(path.read_text()); cases=[]
    for case in data['cases']:
        try:
            request=Request.model_validate(case['request']); result=execute(request); expected=case['expected']
            assert result.status==expected['status']
            assert result==execute(request)
            assert len(result.model_dump_json().encode())<=16384
            if 'reason' in expected: assert result.reasonCode==expected['reason']
            if 'roots' in expected: assert [f.x for f in result.features if f.code=='ROOT_INTERCEPT']==expected['roots']
            if 'solutionKind' in expected: assert result.solutionSet.kind==expected['solutionKind']
            if 'slope' in expected: assert next(f.y for f in result.features if f.code=='SLOPE')==expected['slope']
            if 'bands' in expected: assert [[b.lower,b.upper,b.leftOpen,b.rightOpen] for b in result.numberLine]==expected['bands']
            if result.status=='SUPPORTED':
                guard,expression,natural,restrictions,solution=model(request)
                assert guard.set(result.naturalDomain)==natural
                for hole in expected.get('excluded',[]):
                    v=s.Rational(hole); assert natural.contains(v) is s.false
                    assert all(not segment.points[0].x<=float(v)<=segment.points[-1].x for segment in result.segments)
                for segment in result.segments:
                    f=expression if segment.curve=='FUNCTION' else s.diff(expression,guard.x)
                    for p in segment.points: assert abs(p.y-float(f.subs(guard.x,s.Rational(str(p.x)))))<1e-8
                    if segment.curve=='DERIVATIVE' and 'derivative' in expected: assert all(p.y==float(expected['derivative']) for p in segment.points)
            cases.append({'id':case['id'],'status':'PASS'})
        except Exception:
            cases.append({'id':case['id'],'status':'FAIL','reason':'SEMANTIC_ASSERTION_FAILED'})
    result={'schemaVersion':'visualization-evaluation-v1','synthetic':True,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
            'caseCount':len(cases),'cases':cases,'status':'PASS' if cases and all(c['status']=='PASS' for c in cases) else 'FAIL'}
    Path(sys.argv[1]).write_text(json.dumps(result,indent=2)+'\n')
    return 0 if result['status']=='PASS' else 1
if __name__=='__main__': sys.exit(main())

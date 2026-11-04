#!/usr/bin/env python3
"""Owner-run deterministic synthetic evaluation. Every generation uses the isolated worker."""
import hashlib,json,sys
from pathlib import Path
from uuid import UUID
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'services/math-verifier'))

def main(output):
    from app.api.schemas.method_comparison import Request
    from app.policies.process_executor import ProcessExecutor
    from app.verifiers.canonical_v2 import DomainConstraintGuard
    import sympy as s
    path=ROOT/'evaluations/golden-datasets/methods/rational-method-comparison-v2.json'
    data=json.loads(path.read_text()); executor=ProcessExecutor(); results=[]
    for case in data['cases']:
        identity=UUID('00000000-0000-0000-0000-000000000068')
        request=Request(executionId=identity,correlationId=str(identity),source=case['source'])
        result=executor.execute(request); correct=result.status==case['status']
        if 'roots' in case:
            guard=DomainConstraintGuard(s.Symbol('x',real=True))
            for method in result.methods:
                roots=guard.statement(method.steps[-1])[1]
                correct &= {s.sstr(v) for v in roots}==set(case['roots'])
        if 'methodIds' in case: correct &= [m.methodId for m in result.methods]==case['methodIds']
        results.append({'id':case['id'],'status':'PASS' if correct else 'FAIL','actualOutcome':result.status,'methodCount':len(result.methods)})
    report={'schemaVersion':'method-comparison-evaluation-v2','status':'PASS' if all(r['status']=='PASS' for r in results) else 'FAIL',
        'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'synthetic':True,'caseCount':len(results),'cases':results,
        'liveInference':'NOT_USED','humanSpeed':'NOT_MEASURED'}
    Path(output).write_text(json.dumps(report,indent=2)+'\n')
    return 0 if report['status']=='PASS' else 1
if __name__=='__main__': raise SystemExit(main(sys.argv[1]))

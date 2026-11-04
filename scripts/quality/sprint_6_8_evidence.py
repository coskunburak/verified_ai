#!/usr/bin/env python3
"""Owner-run evidence collection. Read only allowlisted source roots, never secret stores."""
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SUITES = ['MethodComparisonEvidenceTest','AttemptAnalysisPolicyTest','StudentAttemptIntegrationTest','StudentAttemptControllerTest',
          'CanonicalManualStepNormalizerTest','AttemptVerifierClientTest','ModularityTest',
          'AccountPrivacyApplicationServiceTest','PrimarySolveIntegrationTest','VerificationPolicyTest',
          'VerificationExecutionServiceTest','TutorSessionControllerTest','ProgressiveHintIntegrationTest','LearnerMasteryPolicyTest','AttemptMathParserV2Test']


def select_mode(previous,out):
    previous=Path(previous).resolve()
    if not previous.is_relative_to(ROOT/'.generated/sprint-6-8-owner'): raise ValueError('Evidence outside workspace')
    prior=json.loads((previous/'summary.json').read_text())
    current=json.loads((out/'source-before.json').read_text())
    changed=prior.get('sourceFingerprint')!=current.get('sha256')
    previous_source=previous/'source-before.json'
    old_files=json.loads(previous_source.read_text()).get('files',{}) if previous_source.exists() else {}
    new_files=current.get('files',{})
    runtime_roots=('services/api/','services/math-verifier/','apps/ios/','packages/schemas/','packages/contracts/', 'scripts/quality/', 'evaluations/golden-datasets/')
    test_roots=('services/api/src/test/','services/math-verifier/tests/','apps/ios/VerifiedAITests/','apps/ios/VerifiedAIUITests/')
    changed_runtime=[name for name in sorted(set(old_files)|set(new_files))
        if name.startswith(runtime_roots) and not name.startswith(test_roots) and old_files.get(name)!=new_files.get(name)]
    # Any production/contract/runner/golden modification invalidates full acceptance,
    # even during a targeted failure repair. Test-only repairs may select failed cases.
    decision='PRODUCT_CHANGE' if changed_runtime else 'FAILED_TESTS_ONLY'
    (out/'selection-reason.json').write_text(json.dumps({'decision':decision,'sourceChanged':changed,
        'changedRuntimeFiles':changed_runtime,'previousEvidence':str(previous),'reason':'Canonical source, isolated verifier, migration and shared renderer dependency changes require current integration coverage' if decision=='PRODUCT_CHANGE' else 'Only unresolved failures are selected; prior passes remain historical'},indent=2)+'\n')
    print(decision)


def plan_retry(previous, out):
    previous = Path(previous).resolve()
    if not previous.is_relative_to(ROOT/'.generated/sprint-6-8-owner'):
        raise ValueError('Previous evidence must remain inside this workspace')
    prior = json.loads((previous/'summary.json').read_text())
    if prior.get('sprint') != '6.8':
        raise ValueError('Unexpected previous sprint evidence')
    old_plan_path = previous/'retry-plan.json'
    old_plan = json.loads(old_plan_path.read_text()) if old_plan_path.exists() else {}
    backend = set()
    if prior['gates'].get('C') != 'PASS':
        for label in prior.get('backend', {}).get('failures', []):
            name, method = label.rsplit('.', 1)
            backend.add((name.split('.')[-1], method))
        # Original runner did not capture Surefire XML. Recover only explicit test failures
        # from that run's Maven log, never from possibly stale target/surefire-reports.
        log = previous/'backend-tests.log'
        if log.exists():
            backend.update((name.split('.')[-1], method) for name, method in re.findall(
                r'\[ERROR\]\s+([A-Za-z0-9_.]+)\.([A-Za-z0-9_]+)\s+-- Time elapsed:.*<<< (?:FAILURE|ERROR)!', log.read_text(errors='replace')))
        report=prior.get('backend',{})
        passed=set(report.get('caseNames',[]))-set(report.get('failures',[]))-set(report.get('skipped',[]))
        backend.update(tuple(pair) for pair in old_plan.get('backend',[]) if '.'.join(pair) not in passed)
    ios = set()
    if prior['gates'].get('D') != 'PASS':
        ios.update(tuple(pair) for pair in prior.get('ios', {}).get('failedCases', []))
        passed={tuple(pair) for pair in prior.get('ios',{}).get('passedCases',[])}
        ios.update(tuple(pair) for pair in old_plan.get('ios',[]) if tuple(pair) not in passed)
    for name, method in backend:
        if name not in SUITES or not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', method):
            raise ValueError('Unsafe or unsupported Maven test selector')
    for name, method in ios:
        if not re.fullmatch(r'VerifiedAI(?:UI)?Tests\.[A-Za-z_][A-Za-z0-9_]*', name) or not re.fullmatch(r'test[A-Za-z0-9_]+', method):
            raise ValueError('Unsafe or unsupported XCTest selector')
    verifier_report=prior.get('verifier',{})
    verifier=set(verifier_report.get('failureSelectors',[])) if prior['gates'].get('B')!='PASS' else set()
    if prior['gates'].get('B')!='PASS':
        verifier.update(set(old_plan.get('verifier',[]))-set(verifier_report.get('passedSelectors',[])))
    for selector in verifier:
        path,separator,test=selector.partition('::')
        if not separator or not re.fullmatch(r'tests/(?:[A-Za-z0-9_]+/)*test_[A-Za-z0-9_]+\.py',path) or '\n' in test or '\r' in test:
            raise ValueError('Unsafe pytest selector')
    # A gate that never executed a test has no passing test to repeat. Retry its setup/build
    # and originally requested suite; never silently broaden a partial test result.
    recovery=[]
    for gate,key in [('B','verifier'),('C','backend'),('D','ios')]:
        report=prior.get(key,{})
        count=report.get('executed',len(report.get('passedCases',[]))+len(report.get('failedCases',[])))
        selected_cases={'B':verifier,'C':backend,'D':ios}[gate]
        attempted=(previous/(gate+'.blocked')).exists() or (previous/({'B':'verifier-tests.log','C':'backend-tests.log','D':'ios-tests.log'}[gate])).exists()
        if prior['gates'].get(gate) in {'FAIL','BLOCKED'} and attempted and not count and not selected_cases: recovery.append(gate)
    golden=prior.get('goldenEvaluation',{}).get('status') not in {'PASS','NOT_RUN',None}
    if 'B' in recovery: golden=prior.get('goldenEvaluation',{}).get('status')!='PASS'
    selected_gates=[gate for gate,selected in [('A',prior['gates'].get('A') in {'FAIL','BLOCKED'}),
        ('B',bool(verifier) or golden or 'B' in recovery),('C',bool(backend) or 'C' in recovery),('D',bool(ios) or 'D' in recovery)] if selected]
    plan = {'mode':'FAILED_TESTS_ONLY' ,'previousRun':str(previous),'previousSourceFingerprint':prior.get('sourceFingerprint'),
            'previousGates':prior['gates'],'backend':sorted(backend),'ios':sorted(ios),
            'verifier':sorted(verifier),'golden':golden,'gateRecovery':recovery,'selectedGates':selected_gates,
            'fullCurrentSourceAcceptance':False,
            'sourceChangeAssessment':json.loads((out/'selection-reason.json').read_text()) if (out/'selection-reason.json').exists() else None}
    (out/'retry-plan.json').write_text(json.dumps(plan,indent=2)+'\n')
    grouped = {}
    for name, method in sorted(backend): grouped.setdefault(name,[]).append(method)
    (out/'backend-selection.txt').write_text(','.join(name+'#'+'+'.join(methods) for name,methods in grouped.items()))
    (out/'ios-selection.txt').write_text(''.join(name.replace('.','/')+'/'+method+'\n' for name,method in sorted(ios)))
    (out/'verifier-selection.txt').write_text(''.join(value+'\n' for value in sorted(verifier)))
    for gate in selected_gates: (out/(gate+'.selected')).touch()
    for gate in recovery: (out/(gate+'.recovery')).touch()
    if golden: (out/'golden.selected').touch()
    for gate in ('A','B','C','D'):
        if gate not in selected_gates:
            state='BLOCKED' if prior['gates'].get(gate) in {'BLOCKED','FAIL'} else 'NOT_RERUN'
            (out/(gate+'.status')).write_text(state+'\n')
            (out/(gate+'.log')).write_text('No tests rerun. Prior evidence: '+str(previous)+
                '; prior gate status: '+str(prior['gates'].get(gate))+'\n')
    print(f'Failed-test selection: {len(verifier)} verifier, {len(backend)} backend, {len(ios)} iOS; setup recovery gates: {recovery}. Previously passing tests will not run.')


def fingerprint(out):
    roots = ['AGENTS.md','README.md','services/api','services/math-verifier','apps/ios','packages/contracts','packages/schemas',
             'scripts/quality','evaluations/golden-datasets/attempts','evaluations/golden-datasets/methods','docs/verified_ai_learning_platform_docs_v4']
    names = subprocess.check_output(['git','ls-files','-z','--cached','--others','--exclude-standard','--',*roots], cwd=ROOT).decode().split('\0')
    digest = hashlib.sha256(); files = {}
    for name in sorted(set(filter(None,names))):
        path = ROOT/name
        if any(p.startswith('.env') or p in {'.aws','.ssh','.config','.codex','.agents','.venv','__pycache__'} for p in path.parts): continue
        if path.suffix in {'.pem','.key','.p12','.pfx'}: continue
        if path.is_symlink() or not path.resolve().is_relative_to(ROOT) or not path.is_file():
            # Deleted tracked source participates in fingerprint instead of silently disappearing.
            files[name] = 'MISSING_OR_SYMLINK'
        else: files[name] = hashlib.sha256(path.read_bytes()).hexdigest()
        digest.update((name+'\0'+files[name]+'\n').encode())
    value = {'sha256':digest.hexdigest(),'files':files}
    Path(out).write_text(json.dumps(value,indent=2)+'\n')


def junit(paths, required):
    seen, failures, skipped, count, cases = set(), [], [], 0, []
    failure_selectors=[]; passed_selectors=[]
    for path in paths:
        root = ET.parse(path).getroot()
        for case in root.iter('testcase'):
            name = case.get('classname','').split('.')[-1]
            seen.add(name); count += 1; label = name+'.'+case.get('name',''); cases.append(label)
            if case.find('failure') is not None or case.find('error') is not None: failures.append(label)
            if case.find('skipped') is not None: skipped.append(label)
            parts=case.get('classname','').split('.')
            modules=[i for i,part in enumerate(parts) if part.startswith('test_')]
            if modules:
                module=modules[-1]; relative='/'.join(parts[:module+1])+'.py'
                if not relative.startswith('tests/'): relative='tests/'+relative
                selector=relative+'::'+'::'.join(parts[module+1:]+[case.get('name','')])
                if case.find('failure') is not None or case.find('error') is not None: failure_selectors.append(selector)
                elif case.find('skipped') is None: passed_selectors.append(selector)
    missing = sorted(set(required)-seen)
    status='FAIL' if not count or failures or missing else 'BLOCKED' if skipped else 'PASS'
    return {'status':status,
            'executed':count,'failureSelectors':failure_selectors,'passedSelectors':passed_selectors,'failures':failures,'skipped':skipped,'missing':missing,'caseNames':cases}


def reconcile(out, exit_code):
    def read(name, default):
        p=out/name
        try: return json.loads(p.read_text())
        except (OSError,ValueError): return default
    gates={name:(out/(name+'.status')).read_text().strip() for name in ('prerequisites','A','B','C','D','E','F','cleanup')}
    nr={'status':'NOT_RUN'}
    backend=read('backend-summary.json',nr); verifier=read('verifier-summary.json',nr); ios=read('ios-summary.json',nr); golden=read('golden.json',nr)
    before=read('source-before.json',{}); after=read('source-after.json',{})
    retry=read('retry-plan.json',None)
    missing=[name for name,data in [('backend',backend),('verifier',verifier),('ios',ios),('golden',golden)] if data.get('status')!='PASS']
    unchanged=bool(before.get('sha256')) and before.get('sha256')==after.get('sha256')
    if not unchanged: missing.append('current-source-fingerprint')
    if retry is not None: missing.append('full-current-source-coverage-not-rerun')
    local_pass=all(gates[g]=='PASS' for g in ('A','B','C','D','E','cleanup')) and not missing
    failures=[g for g,s in gates.items() if s=='FAIL']
    # Reconciliation can pass for the bounded local slice; full acceptance remains blocked by prerequisites.
    gates['F']='PASS' if local_pass else 'FAIL' if failures else 'BLOCKED'
    (out/'F.status').write_text(gates['F']+'\n')
    (out/'F.log').write_text(json.dumps({'status':gates['F'],'missingMandatoryEvidence':missing,'failedGates':failures,'sourceUnchanged':unchanged},indent=2)+'\n')
    overall='FAIL' if failures or gates['F']=='FAIL' else 'PASS' if local_pass else 'BLOCKED'
    code=exit_code if exit_code else (0 if local_pass else 1 if gates['F']=='FAIL' else 2)
    summary={'sprint':'6.8','productRevision':'method-comparison-v2','baselineSha':'fd7e0c3aae1a4b8154e71d628bd1a9bd47688fc9',
        'actualHead':(out/'head.txt').read_text().strip(),'branch':(out/'branch.txt').read_text().strip(),
        'sourceFingerprint':before.get('sha256'),'sourceUnchanged':unchanged,'overall':overall,'exitCode':code,'gates':gates,
        'localSlice':'LOCAL_ACCEPTANCE_PASSED' if local_pass else 'PENDING_OWNER_TEST',
        'ios':ios,'backend':backend,'verifier':verifier,'goldenEvaluation':golden,
        'migration':{'status':'LOCAL_FORWARD_CHAIN_PASSED' if local_pass else 'PENDING_OWNER','versions':['V030','V031','V032'],
            'location':'db/migration/platform','sharedUpgrade':'NOT_AUTHORIZED','isolatedEvidence':backend.get('status','NOT_RUN'),
            'reason':'No Sprint 6.8 migration. Existing forward chain runs only in disposable Testcontainers databases; no shared database authorization'},
        'platformSpecificValidation':verifier.get('platformSpecificValidation','PLATFORM_SPECIFIC_VALIDATION_PENDING'),
        'securityPrivacy':{'status':'LOCAL_SYNTHETIC_CHECKS_PASSED' if local_pass else 'NOT_VERIFIED','trainingEligible':False,'expectedAttemptProviderCalls':0,'liveProviderCalls':'NOT_MEASURED'},
        'missingMandatoryEvidence':missing,'unresolvedFailures':failures,
        'hostedCI':'NOT_VERIFIED','liveAiEvaluation':'NOT_EVALUATED','productionRelease':'BLOCKED',
        'limitations':['Code integration and local acceptance do not authorize production; provider activation, real handwriting coverage and platform-specific release evidence remain separate.',
            'Synthetic typed cohort is not handwriting accuracy evidence; no 85% handwritten recall or latency target is claimed.',
            'VoiceOver physical-device, Dynamic Type and expert pedagogy review remain owner work.',
            'Tests alone cannot authorize shared upgrade or production rollout.']}
    if retry is not None:
        selected=retry.get('selectedGates',[gate for gate,key in [('C','backend'),('D','ios')] if retry[key]])
        repaired=bool(selected) and unchanged and all(gates[g]=='PASS' for g in selected+['cleanup']) and not exit_code
        summary.update({'mode':'FAILED_TESTS_ONLY','previousEvidence':retry,'fullCurrentSourceAcceptance':False,
            'targetedRepair':'PASS' if repaired else 'FAIL' if failures or exit_code else 'BLOCKED' if selected else 'NO_TESTS_SELECTED',
            'localSlice':'PENDING_OWNER_TEST'})
        summary['migration']['isolatedEvidence']='NOT_RUN'
        summary['limitations'].append('Prior passing results remain historical; targeted retries do not re-establish full current-source acceptance.')
    summary['counts']={state:sum(value==state for value in gates.values()) for state in ('PASS','FAIL','BLOCKED','NOT_RUN','NOT_RERUN')}
    summary['skippedTests']=len(verifier.get('skipped',[]))+len(backend.get('skipped',[]))+(1 if ios.get('skipped') else 0)
    (out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    (out/'summary.md').write_text('# Sprint 6.8 owner acceptance\n\n'+
        'Overall: '+overall+'\n\nHEAD: '+str(summary.get('actualHead'))+'\n\nSource unchanged: '+str(unchanged)+'\n\n'+
        '\n'.join('- '+g+': '+state for g,state in gates.items())+'\n\nMissing evidence: '+', '.join(missing)+
        '\n\nHosted CI: NOT_VERIFIED. Production release: BLOCKED. Local acceptance is not release authorization.\n')
    return code


if __name__=='__main__':
    mode=sys.argv[1]
    if mode=='fingerprint': fingerprint(sys.argv[2])
    elif mode=='select-mode': select_mode(sys.argv[2],Path(sys.argv[3]))
    elif mode=='plan-retry': plan_retry(sys.argv[2],Path(sys.argv[3]))
    elif mode=='junit':
        out=Path(sys.argv[2]); kind=sys.argv[3]
        plan_path=out/'retry-plan.json'
        plan=json.loads(plan_path.read_text()) if plan_path.exists() else None
        gate='C' if kind=='backend' else 'B'
        selected=plan[kind] if plan and gate not in plan.get('gateRecovery',[]) else None
        required=(sorted({name for name,_ in selected}) if kind=='backend' else []) if selected is not None else SUITES if kind=='backend' else ['test_attempt_verification_and_counterexample','test_attempt_v2_product','test_method_comparison_v2']
        report=junit(list((out/('maven-reports' if kind=='backend' else 'verifier-reports')).glob('*.xml')),required)
        if kind=='verifier':
            linux_names={'test_execution_security.test_actual_memory_limit_exhaustion_is_a_nonproof_resource_failure',
                'test_calculus_security.test_linux_calculus_resource_failure_is_reaped_and_nonproof',
                'test_numeric_security.test_linux_memory_and_cpu_limits_apply_to_numeric'}
            platform_skips=[case for case in report['skipped'] if case.split('[')[0] in linux_names]
            unexpected=[case for case in report['skipped'] if case not in platform_skips]
            report['platformSpecificValidation']='PLATFORM_SPECIFIC_VALIDATION_PENDING' if platform_skips else 'OWNER_EXECUTED' if sys.platform.startswith('linux') else 'PLATFORM_SPECIFIC_VALIDATION_PENDING'
            report['platformSpecificSkipped']=platform_skips
            if report['status']=='BLOCKED' and not unexpected and not report['failures'] and not report['missing']:
                report['status']='PASS'
        if selected is not None:
            expected={name+'.'+method for name,method in selected} if kind=='backend' else set(selected)
            actual=set(report['caseNames']) if kind=='backend' else set(report['failureSelectors']+report['passedSelectors'])
            report.update({'scope':'FAILED_TESTS_ONLY','missingSelections':sorted(expected-actual),'unexpectedCases':sorted(actual-expected)})
            if expected!=actual: report['status']='FAIL'
        (out/(kind+'-summary.json')).write_text(json.dumps(report,indent=2)+'\n');sys.exit(0 if report['status']=='PASS' else 2 if report['status']=='BLOCKED' else 1)
    elif mode=='summary':sys.exit(reconcile(Path(sys.argv[2]),int(sys.argv[3])))

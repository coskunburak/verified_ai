import copy,hashlib,json,sys
from pathlib import Path
import pytest
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'evaluations/runners'))
import export_yks_phase13_clean_seed as exporter
@pytest.fixture
def record(): return json.loads((exporter.DEST/'seed_records.jsonl').read_text().splitlines()[0])
def test_integrity():
    data=(exporter.DEST/'seed_records.jsonl').read_bytes();rows=[json.loads(x) for x in data.splitlines()]
    manifest=json.loads((exporter.DEST/'manifest.json').read_text())
    assert len(rows)==manifest['recordCount']>=30
    assert len({r['seed_id'] for r in rows})==len(rows)
    assert hashlib.sha256(data).hexdigest()==manifest['sha256']
    assert {r['exam_code'] for r in rows}=={'TR_YKS_TYT_MATH','TR_YKS_AYT_MATH'}
    for row in rows:
        assert row['verification_status']=='STEP_VERIFIED' and row['contains_student_data'] is False
        assert exporter.validate_record_privacy_and_sympy(row)==row['sympy_digest']
@pytest.mark.parametrize('key',['user_id','student_id','email','ip_address','device_id','exam_sessions','exam_session_items','problem_attempts'])
def test_privacy(record,key):
    record['nested']={key:'synthetic'}
    with pytest.raises(ValueError,match='ADR-006 VIOLATION'): exporter.validate_record_privacy_and_sympy(record)
@pytest.mark.parametrize('status',['UNVERIFIED','PARTIALLY_VERIFIED',None])
def test_unverified(record,status):
    record['verification_status']=status
    with pytest.raises(ValueError): exporter.validate_record_privacy_and_sympy(record)
def test_broken_math(record):
    record.pop('sympy_digest');record['canonical_equations']=[{'left':'1+1','right':'3'}]
    record['verified_steps']=copy.deepcopy(record['canonical_equations'])
    with pytest.raises(ValueError,match='equality'): exporter.validate_record_privacy_and_sympy(record)
def test_wrong_answer(record):
    record.pop('sympy_digest');record['final_answer']='123456'
    with pytest.raises(ValueError): exporter.validate_record_privacy_and_sympy(record)
@pytest.mark.parametrize('value',["__import__('os').system('echo bad')",'x.__class__','2**100000','1/0','sqrt(-1)/0','(((2**12)**12)**12)**12','((x**12)**12)**12'])
def test_expression_security(value):
    with pytest.raises((ValueError,SyntaxError)): exporter.expression(value)
def test_stale_digest(record):
    record['problem_tr']='Değiştirilmiş sentetik soru'
    with pytest.raises(ValueError,match='digest'): exporter.validate_record_privacy_and_sympy(record)
def test_no_fabricated_qwen_metrics():
    r=json.loads((exporter.DEST/'qwen_tyt_ayt_baseline_summary.json').read_text())
    assert r['decision']=='REQUIRES_MORE_EVIDENCE' and r['modelRunPerformed'] is False
    assert r['sympyAnswerEquivalenceRate'] is None and r['phase13SftLoraEntryApproved'] is False

#!/usr/bin/env python3
"""Offline V060-only export. Never connects to a database or reads learner records.

Verification scope is answer/option equivalence, not proof of the natural-language
problem's entire solution. Golden evaluation-only examples are not relabeled for training.
"""
from __future__ import annotations
import ast
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT=Path(__file__).resolve().parents[2]
VENV_SITE=ROOT/'services/math-verifier/.venv/lib/python3.11/site-packages'
if VENV_SITE.exists(): sys.path.append(str(VENV_SITE))
import sympy as sp

SOURCE=ROOT/'services/api/src/main/resources/db/migration/platform/V060__yks_tyt_ayt_curriculum_expansion_and_question_bank.sql'
DEST=ROOT/'evaluations/exports/yks-tr-phase13-seed'
FORBIDDEN=re.compile(r'exam_sessions|exam_session_items|exam_scratchpad_assets|exam_session_scratchpad_assets|exam_session_item_attributions|problem_attempts|user[_-]?id|student[_-]?id|email|ip_address|device_id',re.I)
PRIVACY_ERROR='ADR-006 VIOLATION: Student data or PII detected in Phase 13 seed export'

def digest(value):
    return hashlib.sha256(json.dumps(value,sort_keys=True,ensure_ascii=False,separators=(',',':')).encode()).hexdigest()

def expression(text):
    """Bounded AST interpreter: no eval, sympify(string), attributes, or arbitrary calls."""
    if not isinstance(text,str) or len(text)>512: raise ValueError('Expression size bound')
    tree=ast.parse(text,mode='eval')
    if sum(1 for _ in ast.walk(tree))>128: raise ValueError('Expression complexity bound')
    names={'x':sp.Symbol('x',real=True),'pi':sp.pi,'I':sp.I,'E':sp.E}
    functions={'sqrt':sp.sqrt,'sin':sp.sin,'cos':sp.cos,'tan':sp.tan,'log':sp.log}
    def visit(node,depth=0):
        if depth>24: raise ValueError('Expression nesting bound')
        if isinstance(node,ast.Constant) and type(node.value) in (int,float):
            literal=ast.get_source_segment(text,node)
            if len(literal)>40: raise ValueError('Numeric bound')
            return sp.Rational(literal)
        if isinstance(node,ast.Name) and node.id in names: return names[node.id]
        if isinstance(node,ast.UnaryOp) and isinstance(node.op,(ast.USub,ast.UAdd)):
            value=visit(node.operand,depth+1);return -value if isinstance(node.op,ast.USub) else value
        if isinstance(node,ast.BinOp):
            a,b=visit(node.left,depth+1),visit(node.right,depth+1)
            if isinstance(node.op,ast.Add): return a+b
            if isinstance(node.op,ast.Sub): return a-b
            if isinstance(node.op,ast.Mult): return a*b
            if isinstance(node.op,ast.Div) and b!=0: return a/b
            if isinstance(node.op,ast.Pow) and b.is_number and abs(b)<=12:
                if a.is_Rational and b.is_Integer:
                    bits=max(abs(int(a.p)).bit_length(),int(a.q).bit_length())
                    if bits*abs(int(b))>4096: raise ValueError('Numeric result complexity bound')
                result=a**b
                if any(power.exp.is_number and abs(power.exp)>128 for power in result.atoms(sp.Pow)):
                    raise ValueError('Symbolic power complexity bound')
                return result
        if isinstance(node,ast.Call) and isinstance(node.func,ast.Name) and node.func.id in functions and not node.keywords and 1<=len(node.args)<=2:
            return functions[node.func.id](*(visit(a,depth+1) for a in node.args))
        raise ValueError('Unsupported mathematical expression')
    result=visit(tree.body)
    if result.has(sp.zoo,sp.nan,sp.oo,-sp.oo): raise ValueError('Nonfinite expression')
    return result

def normalize(text):
    text=str(text).strip().strip('$').replace('\\left','').replace('\\right','').replace('\\,','').replace('\\!','')
    text=text.replace('\\cdot','*').replace('\\times','*')
    for _ in range(8):
        updated=re.sub(r'\\frac\{([^{}]+)\}\{([^{}]+)\}',r'((\1)/(\2))',text)
        updated=re.sub(r'\\sqrt\{([^{}]+)\}',r'sqrt(\1)',updated)
        if updated==text: break
        text=updated
    for old,new in [('\\pi','pi'),('\\sin','sin'),('\\cos','cos'),('\\tan','tan'),('\\ln','log'),('\\log','log')]: text=text.replace(old,new)
    text=text.replace('{','(').replace('}',')').replace('^','**')
    text=re.sub(r'(?<=\d)\s*(?=x|pi|sqrt|sin|cos|tan|log|\()', '*',text)
    text=re.sub(r'(?<=\))\s*(?=x|\d|\()', '*',text)
    return text

def validate_record_privacy_and_sympy(record):
    encoded=json.dumps(record,ensure_ascii=False)
    if FORBIDDEN.search(encoded) or re.search(r'[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}',encoded) or re.search(r'\b(?:\d{1,3}\.){3}\d{1,3}\b',encoded):
        raise ValueError(PRIVACY_ERROR)
    if record.get('contains_student_data') is not False or record.get('data_origin')!='CLEAN_SYNTHETIC_SME_ONLY': raise ValueError(PRIVACY_ERROR)
    if record.get('training_eligible') is not True or record.get('verification_status')!='STEP_VERIFIED': raise ValueError('Unverified or ineligible seed')
    if record.get('license')!='VERIFIED_AI_INTERNAL_SYNTHETIC_V1': raise ValueError('Unexpected license')
    equations=record.get('canonical_equations')
    if not isinstance(equations,list) or not 1<=len(equations)<=24: raise ValueError('Missing bounded canonical equations')
    for eq in equations:
        if set(eq)!={'left','right'} or sp.simplify(expression(eq['left'])-expression(eq['right']))!=0:
            raise ValueError('Invalid mathematical equality')
    if record.get('verified_steps')!=equations: raise ValueError('Unverified step content')
    answer=expression(record['final_answer'])
    if sp.simplify(expression(equations[-1]['right'])-answer)!=0: raise ValueError('Final answer mismatch')
    for trap in record.get('trap_distractors',[]):
        if sp.simplify(expression(trap['expression'])-answer)==0: raise ValueError('Distractor equals final answer')
    expected=digest({k:v for k,v in record.items() if k!='sympy_digest'})
    if record.get('sympy_digest') not in (None,expected): raise ValueError('SymPy digest mismatch')
    return expected

def tuples(sql,table):
    match=re.search(r'INSERT INTO '+re.escape(table)+r'\s*\(([^)]+)\)\s*VALUES\s*(.*?)\s*ON CONFLICT',sql,re.S)
    if not match: raise ValueError('Missing static INSERT '+table)
    columns=[c.strip() for c in match[1].split(',')]
    text=match[2];rows=[];i=0
    while i<len(text):
        if text[i]!='(': i+=1;continue
        i+=1;fields=[]
        while i<len(text):
            while text[i].isspace() or text[i]==',': i+=1
            if text[i]==')': i+=1;break
            if text[i]=="'":
                i+=1;buf=[]
                while i<len(text):
                    if text[i:i+2]=="''": buf.append("'");i+=2
                    elif text[i]=="'": i+=1;break
                    else: buf.append(text[i]);i+=1
                value=''.join(buf)
                if text[i:i+2]=='::':
                    i+=2
                    while i<len(text) and (text[i].isalnum() or text[i]=='_'): i+=1
            else:
                start=i
                while i<len(text) and text[i] not in ',)': i+=1
                token=text[start:i].strip();value={'NULL':None,'TRUE':True,'FALSE':False}.get(token,token)
            fields.append(value)
        if len(fields)!=len(columns): raise ValueError('Static SQL field count mismatch')
        rows.append(dict(zip(columns,fields)))
    return rows

def export():
    sql=SOURCE.read_text();options={}
    for option in tuples(sql,'exam_question_options'): options.setdefault(option['question_item_id'],[]).append(option)
    records=[];excluded=[]
    for q in tuples(sql,'exam_question_bank_items'):
        rubric=json.loads(q['structured_rubric_json']);opts=options[q['id']]
        correct=[o for o in opts if o['is_correct'] is True]
        if len(correct)!=1: raise ValueError('Exactly one correct option required')
        eq={'left':normalize(correct[0]['content_latex'] or correct[0]['content_markdown']),'right':normalize(rubric['sympyCheckExpr'])}
        record=dict(seed_id=q['question_code'],exam_code='TR_YKS_TYT_MATH' if q['question_code'].startswith('TYT') else 'TR_YKS_AYT_MATH',topic_id=q['topic_code'],skill_id=q['subtopic_code'] or q['topic_code'],problem_tr=q['stem_markdown'],canonical_equations=[eq],verified_steps=[eq],final_answer=normalize(rubric['expectedAnswerLatex']),trap_distractors=[dict(expression=normalize(o['content_latex'] or o['content_markdown']),mistake_code=o['distractor_mistake_code']) for o in opts if not o['is_correct']],verification_status='STEP_VERIFIED',verification_scope='ANSWER_OPTION_EQUIVALENCE_ONLY',training_eligible=True,data_origin='CLEAN_SYNTHETIC_SME_ONLY',contains_student_data=False,license='VERIFIED_AI_INTERNAL_SYNTHETIC_V1')
        try: record['sympy_digest']=validate_record_privacy_and_sympy(record)
        except (ValueError,SyntaxError,TypeError,KeyError) as exc:
            excluded.append(dict(seed_id=q['question_code'],reason=str(exc)));continue
        records.append(record)
    if len(records)<30: raise ValueError(f'At least 30 verified seeds required; got {len(records)}')
    records.sort(key=lambda r:r['seed_id'])
    DEST.mkdir(parents=True,exist_ok=True)
    payload=''.join(json.dumps(r,ensure_ascii=False,sort_keys=True)+'\n' for r in records).encode()
    (DEST/'seed_records.jsonl').write_bytes(payload)
    manifest=dict(datasetId='yks-tr-phase13-seed-v1',trainingEligibility='CLEAN_SYNTHETIC_SME_ONLY',studentDataIncluded=False,adr006Compliant=True,recordCount=len(records),sha256=hashlib.sha256(payload).hexdigest(),sourceSha256=hashlib.sha256(SOURCE.read_bytes()).hexdigest(),verificationScope='ANSWER_OPTION_EQUIVALENCE_ONLY',excludedRecords=excluded,goldenCorpusPolicy='EXCLUDED_EVALUATION_ONLY_NOT_ELIGIBLE',source=str(SOURCE.relative_to(ROOT)))
    (DEST/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    from summarize_qwen_evaluation import phase13_baseline_gate
    (DEST/'qwen_tyt_ayt_baseline_summary.json').write_text(json.dumps(phase13_baseline_gate(manifest),ensure_ascii=False,indent=2)+'\n')
    print(f'Exported {len(records)} verified static records; excluded {len(excluded)}. Qwen model evidence remains unmeasured.')
    return manifest

if __name__=='__main__': export()

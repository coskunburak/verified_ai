"""Structural diagnoses only after independent exact disproof. Never infers mental intent."""
import sympy as s
from app.verifiers.canonical_v2 import Unsupported


def canonical(n):
    if isinstance(n,dict): return {k:canonical(v) for k,v in n.items() if k!='tokenRange'}
    if isinstance(n,list): return [canonical(v) for v in n]
    return n

def same(a,b): return a is not None and b is not None and canonical(a.model_dump())==canonical(b.model_dump())
def binary(n,op): return n is not None and n.kind=='BINARY' and n.operator==op
def function(n,f): return n is not None and n.kind=='FUNCTION' and n.function==f
def squared_term(candidate,base,power,guard):
    if binary(candidate,'POWER'):
        return same(candidate.left,base) and same(candidate.right,power)
    # Learners commonly evaluate the constant square, e.g. (x+2)^2 -> x^2+4.
    # Admit only an exact constant evaluation of that same term, not a symbolic
    # resemblance that could mislabel an unrelated incorrect transformation.
    if candidate.kind!='NUMBER': return False
    value,domain=guard.expression(base)
    submitted,_=guard.expression(candidate)
    return not value.free_symbols and domain==s.S.Reals and value.is_Rational is True and submitted==value**2

def nodes(n,path):
    if n is None: return
    yield n,path
    for key in ('operand','left','right'):
        value=getattr(n,key)
        if value is not None: yield from nodes(value,path+'/'+key)
    for i,value in enumerate(n.args or []): yield from nodes(value,path+'/args/'+str(i))


def classify(before,after,guard,old_set,new_set,old_domain,new_domain):
    # Explicit AST identities, not broad resemblance or a guessed misconception.
    if before.kind=='EXPRESSION' and after.kind=='EXPRESSION':
        a,b=before.expression,after.expression
        if a.kind=='UNARY' and binary(a.operand,'SUBTRACT') and binary(b,'SUBTRACT'):
            if b.left.kind=='UNARY' and same(b.left.operand,a.operand.left) and same(b.right,a.operand.right):
                return 'ALG_SIGN_FLIP_DISTRIBUTE','/expression'
        if binary(a,'POWER') and a.right.kind=='NUMBER' and a.right.value=='2' and binary(a.left,'ADD') and binary(b,'ADD'):
            if squared_term(b.left,a.left.left,a.right,guard) and squared_term(b.right,a.left.right,a.right,guard):
                return 'ALG_FRESHMAN_DREAM_POWER','/expression'
        if binary(a,'ADD') and binary(a.left,'DIVIDE') and binary(a.right,'DIVIDE') and binary(b,'DIVIDE'):
            if binary(b.left,'ADD') and binary(b.right,'ADD') and same(b.left.left,a.left.left) and same(b.left.right,a.right.left) and same(b.right.left,a.left.right) and same(b.right.right,a.right.right):
                return 'ALG_FRACTION_ADD_DENOM','/expression'
        if a.kind=='FUNCTION' and function(a.args[0],b.function if b.kind=='FUNCTION' else '') and b.kind=='FUNCTION' and function(b.args[0],a.function) and same(a.args[0].args[0],b.args[0].args[0]):
            return 'FUNC_COMPOSITION_ORDER','/expression'
        if function(a,'TOTAL') and (same(b,a.args[0]) or same(b,a.args[1])):
            return 'WORD_VARIABLE_MISASSIGNMENT','/expression'
        if function(a,'DIFF'):
            inner=a.args[0]
            submitted=guard.expression(b)[0]
            if inner.kind=='FUNCTION' and inner.function in ('SIN','COS','EXP','LOG'):
                u=guard.expression(inner.args[0])[0]
                if u.has(guard.x) and s.diff(u,guard.x)!=1:
                    outer={'SIN':s.cos,'COS':lambda v:-s.sin(v),'EXP':s.exp,'LOG':lambda v:1/v}[inner.function](u)
                    if s.simplify(submitted-outer)==0: return 'CALC_CHAIN_RULE_OMITTED','/expression'
            if binary(inner,'POWER'):
                u=guard.expression(inner.left)[0]; power=guard.expression(inner.right)[0]
                if power.is_Integer and u.has(guard.x) and s.diff(u,guard.x)!=1 and s.simplify(submitted-power*u**(power-1))==0:
                    return 'CALC_CHAIN_RULE_OMITTED','/expression'
            if binary(inner,'MULTIPLY') or binary(inner,'DIVIDE'):
                u=guard.expression(inner.left)[0]; v=guard.expression(inner.right)[0]
                du,dv=s.diff(u,guard.x),s.diff(v,guard.x)
                naive=du*dv if inner.operator=='MULTIPLY' else du/dv if dv!=0 else s.nan
                if naive not in (s.nan,s.zoo) and s.simplify(submitted-naive)==0:
                    return 'CALC_PRODUCT_QUOTIENT_NAIVE','/expression'
        if function(a,'LIMIT') and binary(a.args[0],'DIVIDE'):
            target=guard.expression(a.args[1])[0]
            numerator=guard.expression(a.args[0].left)[0].subs(guard.x,target)
            denominator=guard.expression(a.args[0].right)[0].subs(guard.x,target)
            # Specifically the invalid 0/0 -> 0 rule, not arbitrary wrong limits.
            if numerator==denominator==0 and guard.expression(b)[0]==0:
                return 'CALC_LIMIT_INDETERMINATE_SUB','/expression'
        if function(a,'INTEGRAL'):
            if function(b,'INTEGRAL') and same(a.args[0],b.args[0]) and same(a.args[1],b.args[2]) and same(a.args[2],b.args[1]):
                return 'CALC_INTEGRAL_BOUNDS_SWAPPED','/expression'
    if before.kind=='RELATION' and after.kind=='RELATION':
        added=new_set-old_set; lost=old_set-new_set
        roots=list(added) if isinstance(added,s.FiniteSet) else []
        for side in ('left','right'):
            for node,path in nodes(getattr(before,side),'/'+side):
                if binary(node,'DIVIDE') and any(guard.expression(node.right)[0].subs(guard.x,r).is_zero is True for r in roots):
                    return 'DOM_EXTRANEOUS_ROOT_DENOM',path
                if function(node,'LOG') and any(s.Le(guard.expression(node.args[0])[0].subs(guard.x,r),0) is s.true for r in roots):
                    return 'DOM_LOG_NON_POSITIVE',path
                if function(node,'SQRT'):
                    other=before.right if side=='left' else before.left
                    if any(s.Lt(guard.expression(other)[0].subs(guard.x,r),0) is s.true for r in roots): return 'DOM_EVEN_ROOT_NEGATIVE',path
        if before.relation=='EQUALS' and after.relation=='MEMBER_OF' and function(before.left,'SIN') and before.right.kind=='NUMBER' and before.right.value=='0':
            if after.right.kind=='FINITE_SET' and isinstance(new_set,s.FiniteSet) and new_set!=s.S.EmptySet and added==s.S.EmptySet and lost!=s.S.EmptySet:
                # Finite explicit source interval only: prove a nonzero-period sibling was omitted.
                argument=guard.expression(before.left.args[0])[0]
                if argument==guard.x and isinstance(lost,s.FiniteSet) and any(s.simplify((r-k)/s.pi).is_integer is True and r!=k for r in lost for k in new_set):
                    return 'TRIG_PERIOD_OMITTED','/left'
        if before.relation==after.relation=='EQUALS':
            if function(before.left,'ABS') and same(before.left.args[0],after.left) and same(before.right,after.right) and lost!=s.S.EmptySet:
                return 'ABS_SINGLE_BRANCH_ONLY','/left'
            # Both sides divided by the same explicit variable factor, with a proven lost root.
            if binary(before.left,'MULTIPLY') and binary(before.right,'MULTIPLY') and same(before.left.left,before.right.left) and same(after.left,before.left.right) and same(after.right,before.right.right) and lost!=s.S.EmptySet:
                factor=guard.expression(before.left.left)[0]
                if factor.has(guard.x) and isinstance(lost,s.FiniteSet) and all(factor.subs(guard.x,r)==0 for r in lost): return 'ALG_ROOT_LOST_BY_DIVISION','/left/left'
        if before.relation in ('LESS_THAN','LESS_THAN_OR_EQUAL','GREATER_THAN','GREATER_THAN_OR_EQUAL') and after.relation==before.relation:
            a=guard.expression(before.left)[0]-guard.expression(before.right)[0]
            b=guard.expression(after.left)[0]-guard.expression(after.right)[0]
            ratio=s.cancel(b/a)
            if not ratio.free_symbols and ratio.is_negative is True: return 'INEQ_NEGATIVE_MULT_NO_FLIP','/relation'
            if binary(before.left,'DIVIDE') and same(after.left,before.left.left) and binary(after.right,'MULTIPLY') and same(after.right.left,before.right) and same(after.right.right,before.left.right):
                denominator=guard.expression(before.left.right)[0]
                if denominator.has(guard.x): return 'INEQ_CROSS_MULT_VARIABLE','/left/right'
    return 'INVALID_TRANSFORMATION','/'

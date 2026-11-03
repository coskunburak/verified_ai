"""Exact real-set algebra over bounded typed syntax. Run only inside ProcessExecutor.

No string evaluator or numeric equivalence proof. Unknown/implicit sets are refused.
Natural domains are inspected before SymPy cancellation; statement domains remain part
of expression identity and root sets are computed separately for each equation.
"""
import re
import sympy as s
from sympy.calculus.util import continuous_domain
from app.api.schemas.attempt_v2 import Statement

class Unsupported(ValueError): pass

RELATIONS={'EQUALS':s.Eq,'NOT_EQUALS':s.Ne,'LESS_THAN':s.Lt,'LESS_THAN_OR_EQUAL':s.Le,'GREATER_THAN':s.Gt,'GREATER_THAN_OR_EQUAL':s.Ge}


def concrete(value):
    if value in (s.S.EmptySet,s.S.Reals): return value
    if isinstance(value,s.FiniteSet):
        if len(value)>24 or any(x.is_real is not True or x.has(s.Float) for x in value): raise Unsupported()
    elif isinstance(value,s.Interval):
        if any(x not in (-s.oo,s.oo) and x.is_real is not True for x in (value.start,value.end)): raise Unsupported()
    elif isinstance(value,(s.Union,s.Intersection)):
        for arg in value.args: concrete(arg)
    else: raise Unsupported('unresolved solution/domain set')
    return value


def relation_set(relation,x,scope=s.S.Reals):
    if relation is s.true: return s.S.Reals
    if relation is s.false: return s.S.EmptySet
    if isinstance(relation,s.Equality): return concrete(s.solveset(relation.lhs-relation.rhs,x,domain=scope))
    if isinstance(relation,s.Unequality): return concrete(s.Complement(scope,s.solveset(relation.lhs-relation.rhs,x,domain=scope)))
    return concrete(s.solve_univariate_inequality(relation,x,relational=False).intersect(scope))


class DomainConstraintGuard:
    def __init__(self,symbol,scope=s.S.Reals): self.x=symbol; self.scope=scope
    def expression(self,n,depth=0):
        if depth>24: raise Unsupported('depth')
        def child(v): return self.expression(v,depth+1)
        k=n.kind
        if k=='NUMBER':
            if not re.fullmatch(r'-?(0|[1-9][0-9]*)(\.[0-9]+)?',n.value) or len(n.value)>64: raise Unsupported()
            return s.Rational(n.value),s.S.Reals
        if k=='VARIABLE':
            if n.symbol!=str(self.x): raise Unsupported('undeclared symbol')
            return self.x,s.S.Reals
        if k=='CONSTANT':
            if n.value!='PI': raise Unsupported('infinite expression')
            return s.pi,s.S.Reals
        if k=='UNARY':
            a,d=child(n.operand); return -a,d
        if k=='BINARY':
            a,da=child(n.left); b,db=child(n.right); d=concrete(da.intersect(db))
            if n.operator=='ADD': value=a+b
            elif n.operator=='SUBTRACT': value=a-b
            elif n.operator=='MULTIPLY': value=a*b
            elif n.operator=='DIVIDE':
                d=concrete(d.intersect(relation_set(s.Ne(b,0),self.x,self.scope))); value=a/b
            else:
                if b.is_Integer is not True or abs(b)>12: raise Unsupported('power')
                if b<=0: d=concrete(d.intersect(relation_set(s.Ne(a,0),self.x,self.scope)))
                value=a**b
            if value.has(s.zoo,s.nan) or s.count_ops(value)>120: raise Unsupported()
            return value,d
        if k=='FUNCTION':
            a,d=child(n.args[0]); f=n.function
            if f=='TOTAL':
                # Explicit unit-cost word model: total(unit_price, item_count), not inferred prose.
                count,cd=child(n.args[1])
                if a.free_symbols or count.free_symbols or a.is_nonnegative is not True or count.is_Integer is not True or count<0 or count>1000: raise Unsupported('unit cost model')
                return a*count,concrete(d.intersect(cd))
            if f in ('DIFF','LIMIT','INTEGRAL'):
                if a.has(s.Piecewise) or s.count_ops(a)>40: raise Unsupported('calculus scope')
                if f=='DIFF':
                    value=s.diff(a,self.x)
                    d=concrete(d.intersect(continuous_domain(value,self.x,self.scope)))
                else:
                    endpoints=[]
                    for argument in n.args[1:]:
                        endpoint,ed=child(argument)
                        if endpoint.free_symbols or endpoint.is_real is not True or ed!=s.S.Reals: raise Unsupported()
                        endpoints.append(endpoint)
                    if f=='LIMIT':
                        left=s.limit(a,self.x,endpoints[0],dir='-'); right=s.limit(a,self.x,endpoints[0],dir='+')
                        if left!=right or left.is_finite is not True or left.is_real is not True: raise Unsupported('two-sided finite limit')
                        value=left; d=s.S.Reals
                    else:
                        lo,hi=endpoints
                        interval=s.Interval(s.Min(lo,hi),s.Max(lo,hi))
                        # No improper integrals or singularity crossings in this version.
                        if interval.is_subset(d) is not True or not a.is_polynomial(self.x): raise Unsupported('proper polynomial integral only')
                        value=s.integrate(a,(self.x,lo,hi)); d=s.S.Reals
                if value.has(s.Derivative,s.Integral,s.Limit,s.zoo,s.nan): raise Unsupported()
                return value,d
            if f=='LOG' and len(n.args)==2:
                base,bd=child(n.args[1]); d=concrete(d.intersect(bd).intersect(relation_set(s.Gt(base,0),self.x,self.scope)).intersect(relation_set(s.Ne(base,1),self.x,self.scope)))
                value=s.log(a,base)
            else: value={'SQRT':s.sqrt,'ABS':s.Abs,'SIN':s.sin,'COS':s.cos,'TAN':s.tan,'LOG':s.log,'EXP':s.exp}[f](a)
            if f=='SQRT': d=concrete(d.intersect(relation_set(s.Ge(a,0),self.x,self.scope)))
            if f=='LOG': d=concrete(d.intersect(relation_set(s.Gt(a,0),self.x,self.scope)))
            if f=='TAN': d=concrete(d.intersect(continuous_domain(value,self.x,self.scope)))
            if value.has(s.zoo,s.nan) or value.is_real is False: raise Unsupported()
            return value,d
        if k=='PIECEWISE':
            pairs=[]; total=s.S.EmptySet; covered=s.S.EmptySet
            for i,branch in enumerate(n.branches):
                if branch.condition is None:
                    if i!=len(n.branches)-1: raise Unsupported()
                    condition=s.S.Reals
                else: condition=self.statement(branch.condition)[1]
                active=concrete(condition-covered); value,domain=child(branch.expression)
                total=concrete(total.union(active.intersect(domain))); covered=concrete(covered.union(condition))
                pairs.append((value,active.as_relational(self.x)))
            return s.Piecewise(*pairs),total
        raise Unsupported('not an expression')

    def set(self,n):
        if n.kind=='REALS': return s.S.Reals
        if n.kind=='EMPTY_SET': return s.S.EmptySet
        if n.kind=='FINITE_SET':
            values=[]
            for item in n.args or []:
                v,d=self.expression(item)
                if v.free_symbols or d!=s.S.Reals: raise Unsupported()
                values.append(v)
            return concrete(s.FiniteSet(*values))
        if n.kind=='INTERVAL':
            def endpoint(v):
                if v.kind=='CONSTANT' and v.value in ('POSITIVE_INFINITY','NEGATIVE_INFINITY'): return s.oo if v.value=='POSITIVE_INFINITY' else -s.oo
                value,d=self.expression(v)
                if value.free_symbols or d!=s.S.Reals: raise Unsupported()
                return value
            return concrete(s.Interval(endpoint(n.left),endpoint(n.right),left_open=n.leftOpen,right_open=n.rightOpen))
        if n.kind in ('UNION','INTERSECTION'):
            return concrete((s.Union if n.kind=='UNION' else s.Intersection)(*[self.set(v) for v in n.args]))
        raise Unsupported('not a set')

    def statement(self,statement):
        if statement.kind=='EXPRESSION':
            value,domain=self.expression(statement.expression); return value,domain,domain
        a,da=self.expression(statement.left)
        if statement.relation=='MEMBER_OF':
            if a!=self.x: raise Unsupported()
            values=self.set(statement.right); return None,values,s.S.Reals
        b,db=self.expression(statement.right); domain=concrete(da.intersect(db))
        relation=RELATIONS[statement.relation](a,b)
        solutions=concrete(relation_set(relation,self.x,self.scope).intersect(domain))
        return relation,solutions,domain


def wire_set(value):
    if value==s.S.EmptySet: return {'kind':'EMPTY_SET'}
    if value==s.S.Reals: return {'kind':'REALS'}
    if isinstance(value,s.FiniteSet): return {'kind':'FINITE_SET','elements':[s.sstr(v) for v in sorted(value,key=s.default_sort_key)]}
    if isinstance(value,s.Interval): return {'kind':'INTERVAL','start':s.sstr(value.start),'end':s.sstr(value.end),'leftOpen':bool(value.left_open),'rightOpen':bool(value.right_open)}
    if isinstance(value,(s.Union,s.Intersection)): return {'kind':'UNION' if isinstance(value,s.Union) else 'INTERSECTION','sets':[wire_set(a) for a in value.args]}
    raise Unsupported()


def piecewise_identity(difference,domain):
    """Prove zero on every exact branch, including complete domain coverage."""
    if not difference.has(s.Piecewise,s.Abs): return False
    folded=s.piecewise_fold(difference.rewrite(s.Piecewise))
    if folded==0: return True
    if not isinstance(folded,s.Piecewise) or len(folded.args)>64: return False
    covered=s.S.EmptySet
    for expression,active in folded.as_expr_set_pairs(domain=domain):
        active=concrete(active)
        if active==s.S.EmptySet: continue
        if s.simplify(expression)!=0: return False
        covered=concrete(covered.union(active))
    return concrete(domain-covered)==s.S.EmptySet


def compare(guard,before,after,restrictions):
    a,sa,da=guard.statement(before); b,sb,db=guard.statement(after)
    if before.kind!=after.kind: raise Unsupported('mixed claims')
    if before.kind=='RELATION':
        sa=concrete(sa.intersect(restrictions)); sb=concrete(sb.intersect(restrictions))
        delta=concrete(s.SymmetricDifference(sa,sb))
        return delta==s.S.EmptySet,sa,sb,da,db
    da=concrete(da.intersect(restrictions)); db=concrete(db.intersect(restrictions))
    if da==s.S.EmptySet or db==s.S.EmptySet: raise Unsupported('empty expression domain')
    if concrete(s.SymmetricDifference(da,db))!=s.S.EmptySet: return False,da,db,da,db
    difference=s.simplify(a-b)
    if difference==0: return True,da,db,da,db
    # Piecewise and Abs can describe the same function while simplify leaves a
    # conditional residual. Prove all branches instead of treating probe agreement
    # as identity. Unsupported branch sets retain the conservative fallback below.
    try:
        if piecewise_identity(difference,da): return True,da,db,da,db
    except (ValueError,NotImplementedError,TypeError):
        pass
    # Nonzero symbolic residue is not itself a proof. Exact unequal value required.
    for value in probes():
        if da.contains(value) is s.true:
            result=s.simplify(difference.subs(guard.x,value))
            if result.is_zero is False and result.is_real is True: return False,da,db,da,db
    raise Unsupported('identity undecided')


def probes(): return [s.Rational(v) for v in (0,1,-1,2,-2,3,-3,5,-5)]+[s.Rational(1,2),s.Rational(-1,2)]

"""Bounded real rational projections, executed only by the isolated worker.

Exact domains/roots never come from samples. Every joined sample pair is inside
one proven continuous component. Clipping and steep jumps break the series.
"""
import math
import sympy as s
from app.api.schemas.math_visualization import outcome, Band, Feature, Segment, Sample
from app.api.schemas.attempt_v2 import Statement
from app.verifiers.canonical_v2 import DomainConstraintGuard, Unsupported, concrete
from app.verifiers.attempt_learning import _rational_syntax, _rational_poly, _set_ast, ast


def finite(value):
    result=float(value)
    if not math.isfinite(result) or abs(result)>1e12: raise Unsupported('coordinate bound')
    return result


def bands(values):
    values=concrete(values)
    if values == s.S.EmptySet: return []
    if values == s.S.Reals: values=s.Interval(-s.oo,s.oo)
    if isinstance(values,s.Union):
        result=[b for part in values.args for b in bands(part)]
    elif isinstance(values,s.FiniteSet):
        result=[Band(lower=str(v),upper=str(v),lowerValue=finite(v),upperValue=finite(v),leftOpen=False,rightOpen=False)
                for v in sorted(values,key=s.default_sort_key)]
    elif isinstance(values,s.Interval):
        result=[Band(lower=str(values.start),upper=str(values.end),
                     lowerValue=None if values.start == -s.oo else finite(values.start),
                     upperValue=None if values.end == s.oo else finite(values.end),
                     leftOpen=bool(values.left_open),rightOpen=bool(values.right_open))]
    else: raise Unsupported('unresolved display set')
    if len(result)>24: raise Unsupported('set budget')
    return result


def model(request):
    source=request.source
    if source.domainBasis == 'UNRESOLVED' or len(source.variables)!=1 or source.variables[0].symbol!='x' or source.variables[0].domain!='REAL':
        raise Unsupported('known real x required')
    guard=DomainConstraintGuard(s.Symbol('x',real=True)); x=guard.x
    restrictions=s.S.Reals
    for r in source.restrictions:
        for node in (r.left,r.right):
            _rational_syntax(node)
            value,_=guard.expression(node)
            a,b=s.fraction(s.cancel(value)); _rational_poly(a,x,4); _rational_poly(b,x,4)
        _,allowed,natural=guard.statement(Statement(kind='RELATION',relation=r.relation,left=r.left,right=r.right))
        restrictions=concrete(restrictions.intersect(allowed).intersect(natural))
    original=source.original
    if original.kind=='RELATION' and original.relation=='MEMBER_OF':
        if request.type!='NUMBER_LINE': raise Unsupported('set only')
        _,solution,natural=guard.statement(original)
        return guard,None,natural,restrictions,concrete(solution.intersect(restrictions))
    nodes=[original.expression] if original.kind=='EXPRESSION' else [original.left,original.right]
    values=[]; natural=s.S.Reals
    for node in nodes:
        _rational_syntax(node)
        value,domain=guard.expression(node)
        a,b=s.fraction(s.cancel(value)); _rational_poly(a,x,4); _rational_poly(b,x,4)
        values.append(value); natural=concrete(natural.intersect(domain))
    expression=values[0] if len(values)==1 else values[0]-values[1]
    a,b=s.fraction(s.cancel(expression)); _rational_poly(a,x,4); _rational_poly(b,x,4)
    if original.kind=='RELATION':
        if request.type not in {'EQUATION','NUMBER_LINE'} or request.type=='EQUATION' and original.relation!='EQUALS': raise Unsupported('relation scope')
        # Canonical guard owns exact solving and endpoint closure.
        _,solution,_=guard.statement(original)
        solution=concrete(solution.intersect(restrictions))
    else:
        if request.type=='EQUATION': raise Unsupported('expression is not equation')
        solution=None
    return guard,expression,natural,restrictions,solution


def sample(expression,domain,request,curve):
    v=request.viewport; x=s.Symbol('x',real=True)
    lo=s.Rational(str(v.xMin)); hi=s.Rational(str(v.xMax))
    segments=[]; current=[]; previous=None
    def flush():
        nonlocal current
        if current:
            segments.append(Segment(id=('d' if curve=='DERIVATIVE' else 'f')+str(len(segments)),curve=curve,points=current))
            current=[]
    for i in range(request.resolution):
        point=lo+(hi-lo)*s.Rational(i,request.resolution-1)
        if domain.contains(point) is not s.true:
            flush(); previous=None; continue
        value=expression.subs(x,point)
        if value.is_real is not True or value.is_finite is not True:
            flush(); previous=None; continue
        y=finite(value); px=finite(point)
        if not v.yMin<=y<=v.yMax:
            flush(); previous=None; continue
        if previous is not None:
            # A narrow hole between adjacent samples must break even when neither
            # sampled point coincides with the excluded value.
            connected=s.Interval(previous,point).is_subset(domain) is True
            if not connected or abs(y-current[-1].y)>(v.yMax-v.yMin)/4: flush()
        current.append(Sample(x=px,y=y)); previous=point
    flush()
    return segments


def generate(request):
    guard,expression,natural,restrictions,solution=model(request)
    domain=concrete(natural.intersect(restrictions)); x=guard.x
    features=[]; concepts=[]; segments=[]; derivative=None
    if natural!=s.S.Reals: concepts.append('DOMAIN_HOLE')
    displayed=solution if solution is not None else domain
    number_line=bands(displayed)
    if any(b.leftOpen or b.rightOpen for b in number_line): concepts.append('OPEN_BOUNDARY')
    if any(not b.leftOpen or not b.rightOpen for b in number_line): concepts.append('CLOSED_BOUNDARY')
    if expression is not None:
        roots=concrete(s.solveset(expression,x,domain=s.S.Reals).intersect(domain))
        if isinstance(roots,s.FiniteSet):
            features.extend(Feature(code='ROOT_INTERCEPT',x=str(r),y='0') for r in sorted(roots,key=s.default_sort_key))
            if roots: concepts.append('ROOT_INTERCEPT')
        if domain.contains(0) is s.true:
            features.append(Feature(code='Y_INTERCEPT',x='0',y=str(expression.subs(x,0))))
        if expression.is_polynomial(x):
            poly=s.Poly(expression,x)
            if poly.degree()==1:
                slope=poly.nth(1)
                features.append(Feature(code='SLOPE',x='0',y=str(slope)))
                concepts.append('POSITIVE_SLOPE' if slope>0 else 'NEGATIVE_SLOPE')
            if poly.degree()==2:
                vertex=-poly.nth(1)/(2*poly.nth(2))
                if domain.contains(vertex) is s.true:
                    features.append(Feature(code='QUADRATIC_VERTEX',x=str(vertex),y=str(expression.subs(x,vertex))))
                    concepts.append('QUADRATIC_VERTEX')
        if request.type!='NUMBER_LINE':
            segments=sample(expression,domain,request,'FUNCTION'); concepts.append('APPROXIMATE_SAMPLE')
            if request.type=='DERIVATIVE':
                # Rational functions are differentiable on their original open natural
                # domain. Restriction endpoints only display the ambient derivative.
                derivative=s.diff(expression,x)
                if derivative==0: concepts.append('ZERO_DERIVATIVE')
                segments+=sample(derivative,domain,request,'DERIVATIVE')
    return outcome(request,'SUPPORTED','BOUNDED_EXACT_MODEL',expression=ast(expression) if expression is not None else None,
                   derivativeExpression=ast(derivative) if derivative is not None else None,naturalDomain=_set_ast(natural),sourceDomain=_set_ast(restrictions),
                   solutionSet=_set_ast(solution) if solution is not None else None,
                   numberLine=number_line,features=features,segments=segments,concepts=concepts)


def execute(request):
    if request.type=='STORY_BRIDGE': return outcome(request,'UNSUPPORTED','PROVENANCE_UNAVAILABLE')
    try:
        result=generate(request)
        # Retain the existing worker ceiling including verbose typed set nodes.
        if len(result.model_dump_json().encode())>16384: return outcome(request,reason='RESOURCE_FAILURE')
        return result
    except (Unsupported, ValueError, TypeError, NotImplementedError, OverflowError):
        return outcome(request,'UNSUPPORTED','UNSUPPORTED_SOURCE')

"""Deterministic templates, independently checked in the existing isolated symbolic worker."""
import sympy as s
from app.api.schemas.attempt_v2 import Node, Statement
from app.api.schemas.attempt_learning import Result, Twin, Method
from app.verifiers.canonical_v2 import DomainConstraintGuard, compare, concrete, Unsupported


def num(v): return Node(kind='NUMBER',value=str(v),numericType='INTEGER')
def variable(): return Node(kind='VARIABLE',symbol='x')
def binary(op,a,b): return Node(kind='BINARY',operator=op,left=a,right=b)
def fn(name,*args): return Node(kind='FUNCTION',function=name,args=list(args))
def expr(n): return Statement(kind='EXPRESSION',expression=n)
def eq(a,b,relation='EQUALS'): return Statement(kind='RELATION',relation=relation,left=a,right=b)
def members(*values): return eq(variable(),Node(kind='FINITE_SET',args=list(values)) if values else Node(kind='EMPTY_SET'),'MEMBER_OF')
def ast(v):
    if v==s.pi: return Node(kind='CONSTANT',value='PI')
    if v.is_Integer: return num(v)
    if v.is_Rational: return binary('DIVIDE',num(v.p),num(v.q))
    if v.is_Symbol and str(v)=='x': return variable()
    if v.is_Add or v.is_Mul:
        args=list(v.args); result=ast(args[0])
        for a in args[1:]: result=binary('ADD' if v.is_Add else 'MULTIPLY',result,ast(a))
        return result
    if v.is_Pow:
        if v.exp==s.Rational(1,2): return fn('SQRT',ast(v.base))
        return binary('POWER',ast(v.base),ast(v.exp))
    raise Unsupported('presentation AST')


def template(code,seed):
    x=variable(); n=2+seed%5; a=num(n); one=num(1)
    add=lambda l,r:binary('ADD',l,r)
    sub=lambda l,r:binary('SUBTRACT',l,r)
    mul=lambda l,r:binary('MULTIPLY',l,r)
    power=lambda l,r:binary('POWER',l,r)
    div=lambda l,r:binary('DIVIDE',l,r)
    scope=s.S.Reals; domain='x ∈ ℝ'
    if code=='ALG_SIGN_FLIP_DISTRIBUTE':
        problem=expr(Node(kind='UNARY',operator='NEGATE',operand=sub(x,a)))
        options=[expr(sub(a,x)),expr(sub(Node(kind='UNARY',operator='NEGATE',operand=x),a)),expr(add(x,a)),expr(sub(x,a))]
    elif code=='ALG_FRESHMAN_DREAM_POWER':
        problem=expr(power(add(x,a),num(2)))
        options=[expr(add(add(power(x,num(2)),mul(num(2*n),x)),num(n*n))),expr(add(power(x,num(2)),num(n*n))),expr(power(x,num(2))),expr(add(x,num(n*n)))]
    elif code=='ALG_FRACTION_ADD_DENOM':
        problem=expr(add(div(one,a),div(one,num(n+1))))
        options=[expr(div(num(2*n+1),num(n*(n+1)))),expr(div(num(2),num(2*n+1))),expr(div(one,num(n*(n+1)))),expr(num(2))]
    elif code in ('ALG_ROOT_LOST_BY_DIVISION','ABS_SINGLE_BRANCH_ONLY'):
        problem=eq(mul(x,sub(x,a)),num(0)) if code=='ALG_ROOT_LOST_BY_DIVISION' else eq(fn('ABS',x),a)
        options=[members(num(0),a) if code=='ALG_ROOT_LOST_BY_DIVISION' else members(num(-n),a),members(a),members(num(-n)),members()]
    elif code=='DOM_EXTRANEOUS_ROOT_DENOM':
        problem=eq(div(sub(power(x,num(2)),num(n*n)),sub(x,a)),num(2*n))
        options=[members(),members(a),members(num(-n)),members(num(-n),a)]
    elif code=='DOM_EVEN_ROOT_NEGATIVE':
        problem=eq(fn('SQRT',x),num(-n)); options=[members(),members(num(n*n)),members(num(-n*n)),members(num(0))]
    elif code=='DOM_LOG_NON_POSITIVE':
        problem=eq(fn('LOG',x),num(0)); options=[members(one),members(num(0)),members(num(-1)),members(one,num(-1))]
    elif code in ('INEQ_NEGATIVE_MULT_NO_FLIP','INEQ_CROSS_MULT_VARIABLE'):
        problem=eq(mul(num(-n),x),num(0),'GREATER_THAN') if code=='INEQ_NEGATIVE_MULT_NO_FLIP' else eq(div(one,x),num(0),'LESS_THAN')
        options=[eq(x,num(0),'LESS_THAN'),eq(x,num(0),'GREATER_THAN'),eq(x,num(0),'LESS_THAN_OR_EQUAL'),eq(x,num(0),'GREATER_THAN_OR_EQUAL')]
    elif code=='FUNC_COMPOSITION_ORDER':
        problem=expr(fn('LOG',fn('EXP',mul(a,x))))
        options=[expr(mul(a,x)),expr(fn('EXP',fn('LOG',mul(a,x)))),expr(add(a,x)),expr(x)]
    elif code=='TRIG_PERIOD_OMITTED':
        scope=s.Interval(0,2*s.pi); domain='0 ≤ x ≤ 2π'
        pi=Node(kind='CONSTANT',value='PI'); problem=eq(fn('SIN',x),num(0))
        options=[members(num(0),pi,mul(num(2),pi)),members(num(0)),members(pi),members(num(0),pi)]
    elif code=='CALC_LIMIT_INDETERMINATE_SUB':
        problem=expr(fn('LIMIT',div(sub(power(x,num(2)),num(n*n)),sub(x,a)),a))
        options=[expr(num(2*n)),expr(num(0)),expr(a),expr(num(-2*n))]
    elif code=='CALC_CHAIN_RULE_OMITTED':
        problem=expr(fn('DIFF',power(add(mul(a,x),one),num(2))))
        options=[expr(mul(num(2*n),add(mul(a,x),one))),expr(mul(num(2),add(mul(a,x),one))),expr(num(2*n)),expr(power(add(mul(a,x),one),num(2)))]
    elif code=='CALC_PRODUCT_QUOTIENT_NAIVE':
        problem=expr(fn('DIFF',mul(x,add(x,a))))
        options=[expr(add(mul(num(2),x),a)),expr(one),expr(add(x,a)),expr(mul(x,add(x,a)))]
    elif code=='CALC_INTEGRAL_BOUNDS_SWAPPED':
        problem=expr(fn('INTEGRAL',x,num(0),a)); options=[expr(div(num(n*n),num(2))),expr(div(num(-n*n),num(2))),expr(num(n*n+1)),expr(num(0))]
    elif code=='WORD_VARIABLE_MISASSIGNMENT':
        problem=expr(fn('TOTAL',a,num(n+1)))
        options=[expr(num(n*(n+1))),expr(a),expr(num(n+1)),expr(num(2*n+1))]
    else: raise Unsupported('no relevant verified template')
    return problem,options,scope,domain


def twin(request):
    problem,options,scope,domain=template(request.mistakeCode,request.executionId.int)
    guard=DomainConstraintGuard(s.Symbol('x',real=True),scope)
    verdicts=[compare(guard,problem,option,scope)[0] for option in options]
    if verdicts!=[True,False,False,False]: raise Unsupported('template gate')
    # Ensure distinct distractors as well, not merely four separately incorrect labels.
    for i in range(4):
        for j in range(i+1,4):
            if compare(guard,options[i],options[j],scope)[0]: raise Unsupported('duplicate option')
    offset=request.executionId.int%4
    rotated=options[offset:]+options[:offset]
    return Twin(mistakeCode=request.mistakeCode,problem=problem,options=rotated,correctOption=(-offset)%4,
        explanationKey='attempt.mistake.'+request.mistakeCode,domainLabel=domain)


def methods(request):
    source=request.source
    if source is None or source.domainBasis=='UNRESOLVED' or any(v.domain!='REAL' for v in source.variables): raise Unsupported()
    # Single-symbol representation is normalized locally without reinterpreting stored source.
    if len(source.variables)!=1 or source.variables[0].symbol!='x': raise Unsupported()
    guard=DomainConstraintGuard(s.Symbol('x',real=True)); scope=s.S.Reals
    for r in source.restrictions:
        _,allowed,_=guard.statement(eq(r.left,r.right,r.relation)); scope=concrete(scope.intersect(allowed))
    if scope==s.S.EmptySet: raise Unsupported()
    guard.scope=scope; original=source.original
    _,solutions,domain=guard.statement(original)
    result=[]
    if original.kind=='RELATION' and original.relation=='EQUALS':
        left,_=guard.expression(original.left); right,_=guard.expression(original.right)
        polynomial=s.Poly(left-right,guard.x)
        if polynomial.degree()!=2: raise Unsupported('quadratic methods only')
        a,b,c=polynomial.all_coeffs()
        if any(v.is_Rational is not True for v in (a,b,c)): raise Unsupported()
        roots=concrete(s.solveset(polynomial.as_expr(),guard.x,domain=s.S.Reals).intersect(scope))
        if not isinstance(roots,s.FiniteSet) or len(roots)!=2: raise Unsupported('two real roots only')
        solution=members(*[ast(v) for v in sorted(roots,key=s.default_sort_key)])
        ordered=sorted(roots,key=s.default_sort_key)
        factored=binary('MULTIPLY',ast(a),binary('MULTIPLY',binary('SUBTRACT',variable(),ast(ordered[0])),binary('SUBTRACT',variable(),ast(ordered[1]))))
        candidates=[('FACTORING',eq(factored,num(0))),
            ('COMPLETING_SQUARE',eq(binary('POWER',ast(guard.x+b/(2*a)),num(2)),ast((b*b-4*a*c)/(4*a*a))))]
        discriminant=ast(b*b-4*a*c)
        radical=fn('SQRT',discriminant)
        denominator=ast(2*a)
        formula=members(binary('DIVIDE',binary('ADD',ast(-b),radical),denominator),
            binary('DIVIDE',binary('SUBTRACT',ast(-b),radical),denominator))
        if compare(guard,original,formula,scope)[0] and compare(guard,formula,solution,scope)[0]: result.append(Method(methodId='QUADRATIC_FORMULA',steps=[formula,solution]))
        for identity,step in candidates:
            if compare(guard,original,step,scope)[0] and compare(guard,step,solution,scope)[0]:
                result.append(Method(methodId=identity,steps=[step,solution]))
    elif original.kind=='EXPRESSION':
        value,_=guard.expression(original.expression)
        step=expr(ast(s.factor(value)))
        if compare(guard,original,step,scope)[0]: result.append(Method(methodId='SYMBOLIC_SIMPLIFICATION',steps=[step]))
    if not result: raise Unsupported()
    return result


def execute(request):
    try:
        artifact=twin(request) if request.action=='TWIN' else None
        alternatives=methods(request) if request.action=='METHODS' else []
        return Result(executionId=request.executionId,correlationId=request.correlationId,action=request.action,status='VERIFIED',twin=artifact,methods=alternatives).validate_for(request)
    except (ValueError,TypeError,NotImplementedError,ZeroDivisionError):
        return Result(executionId=request.executionId,correlationId=request.correlationId,action=request.action,status='UNSUPPORTED')


def _method_structure(statement):
    """Ignore annotations/nulls and commutative order, not mathematical operations.

    Expanding/factoring every candidate here would erase the derivation distinction.
    The common normalized source and final outcome are removed separately.
    """
    def normalize(v):
        if isinstance(v,dict):
            v={k:normalize(c) for k,c in v.items() if c is not None and k!='tokenRange'}
            if v.get('operator') in {'ADD','MULTIPLY'}:
                v['left'],v['right']=sorted([v['left'],v['right']],key=repr)
            if v.get('kind')=='FINITE_SET': v['args']=sorted(v['args'],key=repr)
            return v
        if isinstance(v,list): return [normalize(c) for c in v]
        return v
    return repr(normalize(statement.model_dump()))


def _polynomial_syntax(node):
    # Do not allow cancellation to turn unsupported functions/rational-domain traps
    # into an apparently ordinary polynomial. Constant rational division is allowed.
    if node.kind in {'NUMBER','VARIABLE'}: return
    if node.kind=='UNARY': return _polynomial_syntax(node.operand)
    if node.kind!='BINARY': raise Unsupported('polynomial vocabulary only')
    _polynomial_syntax(node.left); _polynomial_syntax(node.right)
    if node.operator=='POWER':
        if node.right.kind!='NUMBER' or node.right.value not in {'0','1','2','3','4'}: raise Unsupported('bounded degree')
    if node.operator=='DIVIDE':
        def variable_in(n):
            return n.kind=='VARIABLE' or any(variable_in(v) for v in (n.left,n.right,n.operand) if v is not None)
        if variable_in(node.right): raise Unsupported('variable denominator')


def _set_ast(values):
    """Exact bounded set serialization, including source-domain holes and identities."""
    values=concrete(values)
    if values==s.S.Reals: return Node(kind='REALS')
    if values==s.S.EmptySet: return Node(kind='EMPTY_SET')
    if isinstance(values,s.FiniteSet): return Node(kind='FINITE_SET',args=[ast(v) for v in sorted(values,key=s.default_sort_key)])
    if isinstance(values,s.Interval):
        def endpoint(v):
            if v in (s.oo,-s.oo): return Node(kind='CONSTANT',value='POSITIVE_INFINITY' if v==s.oo else 'NEGATIVE_INFINITY')
            return ast(v)
        return Node(kind='INTERVAL',left=endpoint(values.start),right=endpoint(values.end),leftOpen=bool(values.left_open),rightOpen=bool(values.right_open))
    if isinstance(values,(s.Union,s.Intersection)):
        return Node(kind='UNION' if isinstance(values,s.Union) else 'INTERSECTION',args=[_set_ast(v) for v in values.args])
    raise Unsupported('set presentation')


def _rational_syntax(node):
    if node.kind in {'NUMBER','VARIABLE'}: return
    if node.kind=='UNARY': return _rational_syntax(node.operand)
    if node.kind!='BINARY': raise Unsupported('rational vocabulary')
    _rational_syntax(node.left); _rational_syntax(node.right)
    if node.operator=='POWER' and (node.right.kind!='NUMBER' or node.right.value not in {'0','1','2','3','4'}):
        raise Unsupported('bounded power')


def _rational_poly(value,x,degree):
    poly=s.Poly(value,x)
    if poly.degree()>degree or any(c.is_Rational is not True for c in poly.all_coeffs()): raise Unsupported('bounded rational polynomial')
    return poly


def comparison_methods(request):
    from app.api.schemas.method_comparison import Method as MethodV2, Comparison, POLICY
    source=request.source
    if source.domainBasis=='UNRESOLVED' or len(source.variables)!=1 or source.variables[0].symbol!='x' or source.variables[0].domain!='REAL':
        raise Unsupported('known real single variable required')
    original=source.original
    guard=DomainConstraintGuard(s.Symbol('x',real=True)); restrictions=s.S.Reals
    for r in source.restrictions:
        _polynomial_syntax(r.left); _polynomial_syntax(r.right)
        _,allowed,_=guard.statement(eq(r.left,r.right,r.relation)); restrictions=concrete(restrictions.intersect(allowed))
    if restrictions==s.S.EmptySet: raise Unsupported('infeasible assumptions')
    # Compute the natural domain BEFORE cancellation and outside source restrictions.
    absolute=None
    if original.kind=='EXPRESSION': _polynomial_syntax(original.expression)
    elif original.relation=='EQUALS':
        for side,other in ((original.left,original.right),(original.right,original.left)):
            if side.kind=='FUNCTION' and side.function=='ABS':
                _polynomial_syntax(side.args[0]); _polynomial_syntax(other)
                inside,_=guard.expression(side.args[0]); target,_=guard.expression(other)
                _rational_poly(inside,guard.x,1)
                if target.free_symbols or target.is_Rational is not True: raise Unsupported('constant absolute target')
                absolute=(inside,target)
                break
        if absolute is None: _rational_syntax(original.left); _rational_syntax(original.right)
    else: raise Unsupported('equation/expression only')
    _,source_solutions,domain=guard.statement(original)
    scope=concrete(restrictions.intersect(domain))
    if scope==s.S.EmptySet: raise Unsupported('empty source domain')
    guard.scope=scope
    candidates=[]
    if original.kind=='RELATION':
        # Independent source solution set, never a collection of merely valid roots.
        solution=eq(variable(),_set_ast(concrete(source_solutions.intersect(restrictions))),'MEMBER_OF')
        if absolute is not None:
            inside,target=absolute
            from app.api.schemas.attempt_v2 import Branch
            cases=Node(kind='PIECEWISE',branches=[
                Branch(expression=ast(inside),condition=eq(ast(inside),num(0),'GREATER_THAN_OR_EQUAL')),
                Branch(expression=ast(-inside),condition=None)])
            candidates.append(('ABSOLUTE_CASES',[eq(cases,ast(target)),solution],['SPLIT_ABSOLUTE_CASES','COMPLETE_REAL_SET'],'ABSOLUTE_AFFINE','SPLIT_ABSOLUTE_CASES'))
        else:
            left,_=guard.expression(original.left); right,_=guard.expression(original.right)
            numerator,denominator=s.fraction(s.together(left-right))
            _rational_poly(denominator,guard.x,4)
            poly=_rational_poly(numerator,guard.x,2)
            normalized=eq(ast(poly.as_expr()),num(0))
            rational=domain!=s.S.Reals or denominator.has(guard.x)
            if rational:
                # Every displayed equation is scoped by the visible original domain.
                # Independently prove the final set against the original WITHOUT narrowing.
                reduced=eq(ast(s.factor(poly.as_expr())),num(0))
                candidates.append(('RATIONAL_CLEARING',[normalized,reduced,solution],['CLEAR_DENOMINATORS','ZERO_PRODUCT','COMPLETE_REAL_SET'],'RATIONAL_EQUATION','PRESERVE_DOMAIN_HOLES'))
            elif poly.degree()!=2:
                steps=[normalized]
                if poly.degree()==1:
                    a,b=poly.all_coeffs(); steps.append(eq(variable(),ast(-b/a)))
                steps.append(solution)
                candidates.append(('DIRECT_ALGEBRA',steps,['NORMALIZE_ZERO']+(['ISOLATE_VARIABLE'] if poly.degree()==1 else [])+['COMPLETE_REAL_SET'],
                    'RATIONAL_LINEAR' if poly.degree()==1 else 'CONSTANT_EQUATION','ISOLATE_LINEAR' if poly.degree()==1 else 'RESOLVE_CONSTANT'))
            else:
                a,b,c=poly.all_coeffs(); discriminant=b*b-4*a*c
                roots=concrete(s.solveset(poly.as_expr(),guard.x,domain=s.S.Reals))
                if roots!=s.S.EmptySet and not isinstance(roots,s.FiniteSet): raise Unsupported('finite roots')
                if roots!=s.S.EmptySet:
                    ordered=sorted(roots,key=s.default_sort_key)
                    first=binary('SUBTRACT',variable(),ast(ordered[0])); second=binary('SUBTRACT',variable(),ast(ordered[-1]))
                    factored=eq(binary('MULTIPLY',ast(a),binary('MULTIPLY',first,second)),num(0))
                    # Irrational roots are valid, but discovering them by factoring is not
                    # a useful independent alternative to the formula/square derivation.
                    if all(v.is_Rational for v in roots):
                        candidates.append(('FACTORING',[normalized,factored,solution],['NORMALIZE_ZERO','ZERO_PRODUCT','COMPLETE_REAL_SET'],'RATIONAL_QUADRATIC',
                            'REPEATED_ROOT_STRUCTURE' if discriminant==0 else 'FACTORABLE_OVER_RATIONALS'))
                square=eq(binary('POWER',ast(guard.x+b/(2*a)),num(2)),ast(discriminant/(4*a*a)))
                candidates.append(('COMPLETING_SQUARE',[normalized,square,solution],['NORMALIZE_ZERO','SQUARE_IDENTITY','COMPLETE_REAL_SET'],'RATIONAL_QUADRATIC',
                    'NEGATIVE_DISCRIMINANT' if discriminant<0 else 'DIRECT_SQUARE' if b==0 else 'VERTEX_FORM'))
                if discriminant>=0:
                    radical=fn('SQRT',ast(discriminant)); denominator=ast(2*a)
                    formula=members(binary('DIVIDE',binary('ADD',ast(-b),radical),denominator),binary('DIVIDE',binary('SUBTRACT',ast(-b),radical),denominator))
                    candidates.append(('QUADRATIC_FORMULA',[normalized,formula,solution],['NORMALIZE_ZERO','DISCRIMINANT_FORMULA','COMPLETE_REAL_SET'],'RATIONAL_QUADRATIC',
                        'EXACT_RADICAL_FORMULA' if s.sqrt(discriminant).is_Rational is not True else 'EQUALLY_VALID'))
    else:
        value,_=guard.expression(original.expression); _rational_poly(value,guard.x,4)
        for identity,transformed,reason,recommendation in [
            ('SYMBOLIC_SIMPLIFICATION',s.factor(value),'EXACT_SIMPLIFICATION','EXPOSE_FACTORS'),
            ('POLYNOMIAL_EXPANSION',s.expand(value),'EXPAND_COLLECT','COLLECT_TERMS')]:
            if s.srepr(value)==s.srepr(transformed): continue
            candidates.append((identity,[expr(ast(transformed))],[reason],'RATIONAL_POLYNOMIAL',recommendation))
    published=[]; seen=set()
    for identity,steps,reasons,applicability,recommendation in candidates:
        previous=original; kept=[]; why=[]
        for step,reason in zip(steps,reasons):
            if _method_structure(previous)==_method_structure(step): continue
            if not compare(guard,previous,step,scope)[0]: raise Unsupported('transition proof')
            kept.append(step); why.append(reason); previous=step
        if not kept: continue
        # No hidden domain narrowing can justify a missing or extraneous final root.
        if not compare(guard,original,kept[-1],restrictions)[0]: raise Unsupported('complete source outcome')
        signature=tuple(s.srepr(guard.statement(v)[0]) for v in kept) if identity in {'FACTORING','COMPLETING_SQUARE'} else tuple(_method_structure(v) for v in kept)
        if signature in seen: continue
        seen.add(signature)
        reason,prerequisite,advantage,limitation,level=POLICY[identity]
        published.append(MethodV2(methodId=identity,steps=kept,stepReasons=why,
            outcomeStatus='COMPLETE_SOLUTION_SET' if original.kind=='RELATION' else 'EXPRESSION_IDENTITY',
            comparison=Comparison(applicability=applicability,reasonCode=reason,prerequisite=prerequisite,advantage=advantage,limitation=limitation,
                domainCautions=['REAL_DOMAIN']+(['SOURCE_RESTRICTIONS'] if source.restrictions else [])+(['NATURAL_DOMAIN_EXCLUSIONS'] if domain!=s.S.Reals else []),
                structuralStepCount=len(kept),compactStepIndices=list(range(max(0,len(kept)-2),len(kept))),manipulationLevel=level,recommendation=recommendation)))
    return published,_set_ast(domain)


def execute_comparison(request):
    from app.api.schemas.method_comparison import Result as ComparisonResult
    try:
        alternatives,domain=comparison_methods(request)
        return ComparisonResult(executionId=request.executionId,correlationId=request.correlationId,source=request.source,
            status='VERIFIED' if alternatives else 'NO_ALTERNATIVE',reasonCode='EXACT_METHODS' if alternatives else 'NO_DISTINCT_DERIVATION',
            methods=alternatives,naturalDomain=domain).validate_for(request)
    except (ValueError,TypeError,NotImplementedError,ZeroDivisionError,s.PolynomialError):
        return ComparisonResult(executionId=request.executionId,correlationId=request.correlationId,source=request.source,
            status='UNSUPPORTED',reasonCode='UNSUPPORTED_SOURCE')

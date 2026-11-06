package com.verifiedai.problem.application.canonicalization;

import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.*;

/** Bounded syntax parser, never a mathematical truth authority. Conventional unary/power precedence. */
final class AttemptMathParserV2 {
    private static final Pattern TOKEN_PATTERN=Pattern.compile("\\s*(<=|>=|!=|[A-Za-z][A-Za-z0-9_]{0,15}|(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?|[-+*/^(){}<>=,])");
    private final ObjectMapper mapper=new ObjectMapper();
    private final Set<String> variables;
    private final List<String> tokens=new ArrayList<>();
    private final List<Integer> starts=new ArrayList<>(), ends=new ArrayList<>();
    private int position,nodes,depth;
    AttemptMathParserV2(Set<String> variables) { this.variables=Set.copyOf(variables); }
    String parse(String input) {
        if(input==null || input.isBlank() || input.length()>256 || variables.size()>1) throw new IllegalArgumentException();
        int offset=0;
        while(offset<input.length()) {
            if(input.substring(offset).isBlank()) break;
            var matcher=TOKEN_PATTERN.matcher(input); matcher.region(offset,input.length());
            if(!matcher.lookingAt() || tokens.size()>=160) throw new IllegalArgumentException();
            tokens.add(matcher.group(1)); starts.add(matcher.start(1)); ends.add(matcher.end(1)); offset=matcher.end();
        }
        var left=expression(0); var statement=mapper.createObjectNode();
        if(position==tokens.size()) statement.put("kind","EXPRESSION").set("expression",left);
        else {
            String relation=take();
            String name=switch(relation) { case "="->"EQUALS"; case "!="->"NOT_EQUALS"; case "<"->"LESS_THAN"; case "<="->"LESS_THAN_OR_EQUAL"; case ">"->"GREATER_THAN"; case ">="->"GREATER_THAN_OR_EQUAL"; case "in"->"MEMBER_OF"; default->throw new IllegalArgumentException(); };
            statement.put("kind","RELATION").put("relation",name).set("left",left);
            statement.set("right",name.equals("MEMBER_OF")?set():expression(0));
        }
        if(position!=tokens.size()) throw new IllegalArgumentException();
        return statement.toString();
    }
    private ObjectNode node(String kind) { if(++nodes>120) throw new IllegalArgumentException(); return mapper.createObjectNode().put("kind",kind); }
    private ObjectNode expression(int minimum) {
        if(++depth>24) throw new IllegalArgumentException();
        int first=position; String token=take(); ObjectNode left;
        if(token.equals("-")) { left=node("UNARY").put("operator","NEGATE"); left.set("operand",expression(30)); }
        else if(token.equals("+")) left=expression(30);
        else if(token.equals("(")) { left=expression(0); expect(")"); }
        else if(token.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")) {
            if(token.length()>64) throw new IllegalArgumentException();
            left=node("NUMBER").put("numericType",token.contains(".")?"DECIMAL":"INTEGER").put("value",token);
        } else if(token.equals("pi")) left=node("CONSTANT").put("value","PI");
        else if(token.equals("piecewise") && peek().equals("(")) {
            take(); left=node("PIECEWISE"); var branches=left.putArray("branches");
            while(true) {
                if(branches.size()>=6) throw new IllegalArgumentException();
                var value=expression(0); expect(","); var branch=branches.addObject(); branch.set("expression",value);
                if(peek().equals("otherwise")) { take(); branch.putNull("condition"); expect(")"); break; }
                var conditionLeft=expression(0); String op=take();
                String relation=switch(op) {case "="->"EQUALS";case "!="->"NOT_EQUALS";case "<"->"LESS_THAN";case "<="->"LESS_THAN_OR_EQUAL";case ">"->"GREATER_THAN";case ">="->"GREATER_THAN_OR_EQUAL";default->throw new IllegalArgumentException();};
                var condition=mapper.createObjectNode().put("kind","RELATION").put("relation",relation);
                condition.set("left",conditionLeft); condition.set("right",expression(0)); branch.set("condition",condition);
                if(peek().equals(")")) { take(); break; } expect(",");
            }
        }
        else if(peek().equals("(")) {
            String function=token.toUpperCase(Locale.ROOT);
            if(!Set.of("SQRT","ABS","SIN","COS","TAN","LOG","EXP","DIFF","LIMIT","INTEGRAL","TOTAL").contains(function)) throw new IllegalArgumentException();
            take(); left=node("FUNCTION").put("function",function); var args=left.putArray("args"); args.add(expression(0));
            while(peek().equals(",")) { if(args.size()>=3) throw new IllegalArgumentException(); take(); args.add(expression(0)); } expect(")");
            int count=args.size(); if(!(function.equals("LOG")?(count==1 || count==2):count==((function.equals("LIMIT") || function.equals("TOTAL"))?2:function.equals("INTEGRAL")?3:1))) throw new IllegalArgumentException();
        } else if(variables.contains(token)) left=node("VARIABLE").put("symbol",token);
        else throw new IllegalArgumentException();
        range(left,first,position);
        while(true) {
            String op=peek(); int binding=switch(op) { case "+","-"->10; case "*","/"->20; case "^"->30; default->-1; };
            if(binding<minimum) break;
            take(); var right=expression(op.equals("^")?binding:binding+1);
            var next=node("BINARY").put("operator",switch(op) {case "+"->"ADD"; case "-"->"SUBTRACT"; case "*"->"MULTIPLY"; case "/"->"DIVIDE"; default->"POWER";});
            next.set("left",left); next.set("right",right); range(next,first,position); left=next;
        }
        range(left,first,position); depth--; return left;
    }
    private void range(ObjectNode node,int first,int last) {
        if(first<last) node.putObject("tokenRange").put("start",starts.get(first)).put("end",ends.get(last-1));
    }
    private ObjectNode set() {
        if(++depth>24) throw new IllegalArgumentException();
        try { return setValue(); } finally { depth--; }
    }
    private ObjectNode setValue() {
        if(peek().equals("Union") || peek().equals("Intersection")) {
            String kind=take().equals("Union")?"UNION":"INTERSECTION"; expect("(");
            var result=node(kind); var args=result.putArray("args");
            do { if(args.size()>=12) throw new IllegalArgumentException(); args.add(set()); if(!peek().equals(",")) break; take(); } while(true);
            expect(")"); return result;
        }
        if(peek().equals("Interval")) {
            take(); expect("("); var result=node("INTERVAL");
            result.set("left",endpoint()); expect(","); result.set("right",endpoint()); expect(","); result.put("leftOpen",open()); expect(","); result.put("rightOpen",open()); expect(")");
            return result;
        }
        if(peek().equals("Reals")) { take(); return node("REALS"); }
        expect("{");
        if(peek().equals("}")) { take(); return node("EMPTY_SET"); }
        var value=node("FINITE_SET"); var args=value.putArray("args");
        do { if(args.size()>=12) throw new IllegalArgumentException(); args.add(expression(0)); if(!peek().equals(",")) break; take(); } while(true);
        expect("}"); return value;
    }
    private boolean open() { String value=take(); if(!Set.of("true","false").contains(value)) throw new IllegalArgumentException(); return value.equals("true"); }
    private ObjectNode endpoint() {
        if(peek().equals("inf")) { take(); return node("CONSTANT").put("value","POSITIVE_INFINITY"); }
        if(peek().equals("-") && position+1<tokens.size() && tokens.get(position+1).equals("inf")) { take(); take(); return node("CONSTANT").put("value","NEGATIVE_INFINITY"); }
        return expression(0);
    }
    private String peek() { return position<tokens.size()?tokens.get(position):""; }
    private String take() { if(position>=tokens.size()) throw new IllegalArgumentException(); return tokens.get(position++); }
    private void expect(String token) { if(!take().equals(token)) throw new IllegalArgumentException(); }
}

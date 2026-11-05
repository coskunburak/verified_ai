package com.verifiedai.visualization.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import java.util.*;

/** Structural and cross-field validation before public transport. No verification verdict. */
public final class VisualizationContract {
    private VisualizationContract() { }
    public record Source(UUID canonicalId,int canonicalRevision,UUID parseId,int parseRevision) { }
    public record Viewport(double xMin,double xMax,double yMin,double yMax) { }
    public enum Kind { FUNCTION, EQUATION, DERIVATIVE, NUMBER_LINE, STORY_BRIDGE }
    public enum Status { SUPPORTED, UNSUPPORTED, UNAVAILABLE }
    public record Point(double x,double y) { }
    public record Segment(String id,String curve,List<Point> points) { }
    public record Band(String lower,String upper,Double lowerValue,Double upperValue,boolean leftOpen,boolean rightOpen) { }
    public record Feature(String code,String x,String y) { }
    public record Result(String schemaVersion,String policyVersion,UUID executionId,String correlationId,Source identity,
        Kind type,Viewport viewport,int resolution,Status status,String reasonCode,JsonNode expression,JsonNode derivativeExpression,JsonNode naturalDomain,JsonNode sourceDomain,
        JsonNode solutionSet,List<Band> numberLine,List<Feature> features,List<Segment> segments,List<String> concepts,
        String axes,String sampleSemantics,boolean trainingEligible) { }
    public record PublicProjection(String schemaVersion,UUID problemId,Source source,String freshness,boolean trainingEligible,Result result) { }
    public record Query(String schemaVersion,Source source,String type,Viewport viewport,int resolution) { }
    public static JsonNode request(String json) {
        var value=VersionedJsonContract.validate("math-visualization-source-v1",json);
        viewport(value.path("viewport")); return value;
    }
    public static void viewport(JsonNode v) {
        for(String axis:List.of("x","y")) {
            double span=v.path(axis+"Max").asDouble()-v.path(axis+"Min").asDouble();
            if(!Double.isFinite(span) || span<.01 || span>2000) throw new IllegalArgumentException("Viewport rejected");
        }
    }
    public static JsonNode result(String json,JsonNode request) {
        var r=VersionedJsonContract.validate("math-visualization-result-v1",json);
        for(String field:List.of("executionId","correlationId","identity","type","viewport","resolution"))
            if(!r.path(field).equals(request.path(field))) invalid();
        if(!r.path("correlationId").equals(r.path("executionId"))) invalid();
        var reasons=Map.of("SUPPORTED",Set.of("BOUNDED_EXACT_MODEL"),"UNSUPPORTED",Set.of("UNSUPPORTED_SOURCE","PROVENANCE_UNAVAILABLE"),
            "UNAVAILABLE",Set.of("TIMEOUT","RESOURCE_FAILURE","WORKER_FAILURE","ADMISSION_LIMIT","INVALID_RESPONSE","DISABLED"));
        if(!reasons.get(r.path("status").asText()).contains(r.path("reasonCode").asText())) invalid();
        boolean supported=r.path("status").asText().equals("SUPPORTED");
        if(supported && (r.path("naturalDomain").isNull() || r.path("sourceDomain").isNull())) invalid();
        if(supported) {
            String kind=r.path("type").asText();
            if(kind.equals("STORY_BRIDGE")||!kind.equals("NUMBER_LINE")&&r.path("expression").isNull()) invalid();
            if(kind.equals("NUMBER_LINE")&&!r.path("segments").isEmpty()) invalid();
            if(!r.path("derivativeExpression").isNull()!=kind.equals("DERIVATIVE")) invalid();
        }
        if(!supported) {
            for(String field:List.of("expression","derivativeExpression","naturalDomain","sourceDomain","solutionSet")) if(!r.path(field).isNull()) invalid();
            for(String field:List.of("numberLine","features","segments","concepts")) if(!r.path(field).isEmpty()) invalid();
        }
        int count=0; var ids=new HashSet<String>(); var v=r.path("viewport");
        for(var segment:r.path("segments")) {
            if(!ids.add(segment.path("id").asText())) invalid();
            if(segment.path("curve").asText().equals("DERIVATIVE") && !r.path("type").asText().equals("DERIVATIVE")) invalid();
            double previous=Double.NEGATIVE_INFINITY;
            for(var point:segment.path("points")) {
                count++; double x=point.path("x").asDouble(),y=point.path("y").asDouble();
                if(!Double.isFinite(x)||!Double.isFinite(y)||x<=previous||x<v.path("xMin").asDouble()||x>v.path("xMax").asDouble()
                    ||y<v.path("yMin").asDouble()||y>v.path("yMax").asDouble()) invalid();
                previous=x;
            }
        }
        if(count>2*r.path("resolution").asInt()) invalid();
        var concepts=new HashSet<String>();
        for(var concept:r.path("concepts")) if(!concepts.add(concept.asText())) invalid();
        for(var band:r.path("numberLine")) {
            boolean left=band.path("lowerValue").isNull(),right=band.path("upperValue").isNull();
            if(left!=band.path("lower").asText().equals("-oo") || right!=band.path("upper").asText().equals("oo")) invalid();
            if(left&&!band.path("leftOpen").asBoolean() || right&&!band.path("rightOpen").asBoolean()) invalid();
            if(!left&&!right) {
                double a=band.path("lowerValue").asDouble(),b=band.path("upperValue").asDouble();
                if(a>b || a==b&&(band.path("leftOpen").asBoolean()||band.path("rightOpen").asBoolean())) invalid();
            }
        }
        try { new com.fasterxml.jackson.databind.ObjectMapper().treeToValue(r,Result.class); }
        catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalArgumentException("Typed visualization rejected"); }
        return r;
    }
    private static void invalid() { throw new IllegalArgumentException("Visualization contract rejected"); }
}

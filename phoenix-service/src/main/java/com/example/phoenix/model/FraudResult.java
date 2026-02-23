package com.example.phoenix.model;

public class FraudResult {

    private final int score;
    private final String analysis;
    private final String rationale;
    private final String thought;

    public FraudResult(int score, String analysis, String rationale,  String thought) {
        this.score = score;
        this.analysis = analysis;
        this.rationale = rationale;
        this.thought = thought;
    }

    public int getScore() { return score; }
    public String getAnalysis() { return analysis; }
    public String getRationale() { return rationale; }
    public String getThought() { return thought; }
}

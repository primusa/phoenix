package com.example.phoenix.model;

public class FraudResult {

    private final int score;
    private final String analysis;
    private final String rationale;

    public FraudResult(int score, String analysis, String rationale) {
        this.score = score;
        this.analysis = analysis;
        this.rationale = rationale;
    }

    public int getScore() { return score; }
    public String getAnalysis() { return analysis; }
    public String getRationale() { return rationale; }
}

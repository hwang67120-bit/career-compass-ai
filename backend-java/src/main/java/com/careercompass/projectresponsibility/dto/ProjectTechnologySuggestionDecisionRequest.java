package com.careercompass.projectresponsibility.dto;

public record ProjectTechnologySuggestionDecisionRequest(long expectedVersion, Decision decision) {
    public enum Decision { ADD, IGNORE }
}

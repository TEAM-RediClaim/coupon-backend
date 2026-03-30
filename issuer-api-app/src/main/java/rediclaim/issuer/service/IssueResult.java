package rediclaim.issuer.service;

public enum IssueResult {
    SUCCESS,
    NOT_IN_ACTIVE_QUEUE,
    ALREADY_ISSUED,
    OUT_OF_STOCK
}
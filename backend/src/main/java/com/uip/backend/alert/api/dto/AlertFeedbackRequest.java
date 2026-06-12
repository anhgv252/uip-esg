package com.uip.backend.alert.api.dto;

import lombok.Data;

/**
 * Request body for operator feedback on an AI-generated alert decision.
 */
@Data
public class AlertFeedbackRequest {

    /** {@code true} = AI was correct; {@code false} = AI was wrong. */
    private Boolean correct;

    /** Free-text operator comment explaining the decision. */
    private String comment;

    /** Operator-suggested action if AI was wrong (e.g. "DISMISS", "ESCALATE_MANUALLY"). */
    private String suggestedAction;
}

package de.benjaminaaron.ontoserver.routing.websocket.messages;

import lombok.Data;

@Data
public class TripleMessage {
    private String subjectUri;
    private String predicateUri;
    private String objectUriOrLiteralValue;
    private boolean objectIsLiteral;
}

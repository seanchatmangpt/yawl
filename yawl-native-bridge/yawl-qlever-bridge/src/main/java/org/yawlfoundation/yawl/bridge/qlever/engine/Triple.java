/**
 * Triple - Represents an RDF triple
 *
 * A triple consists of a subject, predicate, and object,
 * which are the fundamental building blocks of RDF graphs.
 */
public final record Triple(
    String subject,
    String predicate,
    String object
) {

    /**
     * Gets the subject of the triple
     */
    public String subject() {
        return subject;
    }

    /**
     * Gets the predicate of the triple
     */
    public String predicate() {
        return predicate;
    }

    /**
     * Gets the object of the triple
     */
    public String object() {
        return object;
    }

    /**
     * Checks if this triple contains a blank node (node ID starting with "_:")
     */
    public boolean containsBlankNode() {
        return subject.startsWith("_:") || predicate.startsWith("_:") || object.startsWith("_:");
    }

    /**
     * Checks if this triple contains a literal (object in quotes)
     */
    public boolean isLiteralTriple() {
        return object.startsWith("\"") && object.endsWith("\"");
    }

    /**
     * Checks if this triple contains an IRI (all components are IRIs)
     */
    public boolean isIriTriple() {
        return subject.startsWith("http") &&
               predicate.startsWith("http") &&
               (object.startsWith("http") || object.startsWith("mailto:"));
    }

    /**
     * Converts this triple to a Turtle format string
     */
    public String toTurtle() {
        StringBuilder sb = new StringBuilder();
        sb.append(escapeTurtle(subject))
          .append(" ")
          .append(escapeTurtle(predicate))
          .append(" ")
          .append(escapeTurtle(object))
          .append(" .");
        return sb.toString();
    }

    private String escapeTurtle(String value) {
        if (value.startsWith("http://") || value.startsWith("https://") ||
            value.startsWith("mailto:") || value.startsWith("urn:")) {
            return "<" + value + ">";
        } else if (value.startsWith("_:")) {
            return value; // BNode
        } else {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
    }

    @Override
    public String toString() {
        return "Triple[" + subject + " " + predicate + " " + object + "]";
    }
}
import java.util.List;

/**
 * ConstructResult - Result of a CONSTRUCT query
 *
 * Represents the RDF graph result of a SPARQL CONSTRUCT query.
 */
public final record ConstructResult(
    List<Triple> triples
) implements QueryResult {

    /**
     * Checks if the query executed successfully
     */
    @Override
    public boolean isSuccessful() {
        return true; // ConstructResult only exists for successful queries
    }

    /**
     * Gets the list of RDF triples in the result
     */
    public List<Triple> getTriples() {
        return triples;
    }

    /**
     * Gets the result as Turtle format
     */
    public String getTurtleResult() {
        if (triples == null || triples.isEmpty()) {
            throw new UnsupportedOperationException(
                "getTurtleResult() called on empty result. " +
                "Check hasContent() before calling this method."
            );
        }

        StringBuilder sb = new StringBuilder();

        // Add prefix declaration
        sb.append("@prefix : <http://example.org/> .\n");
        sb.append("@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n");
        sb.append("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n\n");

        // Add triples
        for (Triple triple : triples) {
            sb.append(" ").append(escapeTurtle(triple.subject))
              .append(" ").append(escapeTurtle(triple.predicate))
              .append(" ").append(escapeTurtle(triple.object))
              .append(" .\n");
        }

        return sb.toString();
    }

    /**
     * Checks if the result has content
     */
    public boolean hasContent() {
        return triples != null && !triples.isEmpty();
    }

    /**
     * Gets the number of triples in the result
     */
    public int getTripleCount() {
        return triples == null ? 0 : triples.size();
    }

    private String escapeTurtle(String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return "<" + value + ">";
        } else if (value.startsWith("_:")) {
            return value; // BNode
        } else {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
    }

    @Override
    public String toString() {
        String content = hasContent() ? "(" + getTripleCount() + " triples)" : "(empty)";
        return "ConstructResult[" + content + "]";
    }
}
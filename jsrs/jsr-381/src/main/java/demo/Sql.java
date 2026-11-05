package demo;

/** All SQL in one place. %d is replaced by the embedding size. */
final class Sql {
    private Sql() {}

    static final String CREATE_SEQUENCE =
            "CREATE SEQUENCE IF NOT EXISTS example_id";

    static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS examples (
                id        INTEGER DEFAULT nextval('example_id') PRIMARY KEY,
                label     VARCHAR NOT NULL,
                source    VARCHAR,
                embedding FLOAT[%d] NOT NULL
            )""";

    static final String INSERT = """
            INSERT INTO examples (label, source, embedding)
            VALUES (?, ?, ?::FLOAT[%d])""";

    /** k nearest neighbours vote; closer neighbours count more. */
    static final String CLASSIFY = """
            WITH nn AS (
                SELECT label,
                       array_cosine_distance(embedding, ?::FLOAT[%d]) AS d
                FROM examples
                ORDER BY d
                LIMIT ?
            )
            SELECT label,
                   (SUM(1 - d) / (SELECT SUM(1 - d) FROM nn))::FLOAT AS score
            FROM nn
            GROUP BY label
            ORDER BY score DESC""";

    static String sized(String sql, int dims) {
        return sql.formatted(dims);
    }
}

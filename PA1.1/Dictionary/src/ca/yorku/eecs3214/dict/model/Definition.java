package ca.yorku.eecs3214.dict.model;

import java.util.Objects;

public class Definition {

    private final String word;
    private final String databaseName;
    private String definition;

    public Definition(String word, String database) {
        this.word = word;
        this.databaseName = database;
    }

    public String getWord() {
        return word;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        // Remove any spaces at the end of each line, but keep the line-feed line break
        this.definition = definition.replaceAll("[ \t\r]*\n", "\n");
    }

    public void appendDefinition(String definition) {
        if (this.definition == null)
            this.setDefinition(definition);
        else if (definition != null)
            this.setDefinition(this.definition + "\n" + definition);
    }

    @Override
    public String toString() {
        return "('" + word + '\'' +
                "@'" + databaseName + '\'' +
                ": '" + definition + "')";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Definition that = (Definition) o;
        return word.equals(that.word) && databaseName.equals(that.databaseName) &&
                Objects.equals(definition == null ? null : definition.trim(),
                        that.definition == null ? null : that.definition.trim());
    }

    @Override
    public int hashCode() {
        return Objects.hash(word, databaseName, definition == null ? null : definition.trim());
    }
}

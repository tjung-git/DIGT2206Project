package ca.yorku.eecs3214.dict.model;

import java.util.Objects;

public class Database {

    public static final Database DATABASE_ANY = new Database("*", "Any database");
    public static final Database DATABASE_FIRST_MATCH = new Database("!", "First database with a match");

    private final String name;
    private final String description;

    public Database(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Database database = (Database) o;
        return Objects.equals(name, database.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public String toString() {
        return this.name+": "+this.description;
    }
}

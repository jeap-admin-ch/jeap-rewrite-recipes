package ch.admin.bit.jeap.openrewrite.recipe.hibernate;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateHibernate7RemovedAnnotationsTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new MigrateHibernate7RemovedAnnotations())
                // No Hibernate 7 classes on classpath — simulates migration scenario
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesWhereToSqlRestriction() {
        rewriteRun(java(
                """
                import jakarta.persistence.Entity;
                import org.hibernate.annotations.Where;

                @Entity
                @Where(clause = "deletion_date is null")
                class MyEntity {
                    String name;
                }
                """,
                """
                import jakarta.persistence.Entity;
                import org.hibernate.annotations.SQLRestriction;

                @Entity
                @SQLRestriction("deletion_date is null")
                class MyEntity {
                    String name;
                }
                """
        ));
    }

    @Test
    void migratesWhereOnField() {
        rewriteRun(java(
                """
                import jakarta.persistence.Entity;
                import jakarta.persistence.OneToMany;
                import org.hibernate.annotations.Where;
                import java.util.List;

                @Entity
                class MyEntity {
                    @OneToMany(mappedBy = "owner")
                    @Where(clause = "status <> 'DELETED'")
                    List<String> items;
                }
                """,
                """
                import jakarta.persistence.Entity;
                import jakarta.persistence.OneToMany;
                import org.hibernate.annotations.SQLRestriction;
                import java.util.List;

                @Entity
                class MyEntity {
                    @OneToMany(mappedBy = "owner")
                    @SQLRestriction("status <> 'DELETED'")
                    List<String> items;
                }
                """
        ));
    }

    @Test
    void migratesWhereJoinTableToSqlJoinTableRestriction() {
        rewriteRun(java(
                """
                import jakarta.persistence.Entity;
                import jakarta.persistence.ManyToMany;
                import org.hibernate.annotations.WhereJoinTable;
                import java.util.List;

                @Entity
                class MyEntity {
                    @ManyToMany
                    @WhereJoinTable(clause = "status = 'ACTIVE'")
                    List<String> collaborators;
                }
                """,
                """
                import jakarta.persistence.Entity;
                import jakarta.persistence.ManyToMany;
                import org.hibernate.annotations.SQLJoinTableRestriction;
                import java.util.List;

                @Entity
                class MyEntity {
                    @ManyToMany
                    @SQLJoinTableRestriction("status = 'ACTIVE'")
                    List<String> collaborators;
                }
                """
        ));
    }

    @Test
    void removesLoaderAnnotation() {
        rewriteRun(java(
                """
                import jakarta.persistence.Entity;
                import jakarta.persistence.NamedQuery;
                import org.hibernate.annotations.Loader;
                import org.hibernate.annotations.SQLDelete;

                @Entity
                @SQLDelete(sql = "UPDATE my_entity SET deletion_date = now() WHERE id = ?")
                @Loader(namedQuery = "findMyEntityById")
                @NamedQuery(name = "findMyEntityById", query = "SELECT e FROM MyEntity e WHERE e.id = ?1 AND e.deletionDate is null")
                class MyEntity {
                    String name;
                }
                """,
                """
                import jakarta.persistence.Entity;
                import jakarta.persistence.NamedQuery;
                import org.hibernate.annotations.SQLDelete;

                @Entity
                @SQLDelete(sql = "UPDATE my_entity SET deletion_date = now() WHERE id = ?")
                @NamedQuery(name = "findMyEntityById", query = "SELECT e FROM MyEntity e WHERE e.id = ?1 AND e.deletionDate is null")
                class MyEntity {
                    String name;
                }
                """
        ));
    }

    @Test
    void migratesFullSoftDeletePattern() {
        rewriteRun(java(
                """
                import jakarta.persistence.Entity;
                import jakarta.persistence.NamedQuery;
                import org.hibernate.annotations.Loader;
                import org.hibernate.annotations.SQLDelete;
                import org.hibernate.annotations.Where;

                @Entity
                @SQLDelete(sql = "UPDATE hallmarking SET deletion_date = now() WHERE id = ?")
                @Loader(namedQuery = "findHallmarkingById")
                @NamedQuery(name = "findHallmarkingById", query = "SELECT h FROM Hallmarking h WHERE h.id = ?1 AND h.deletionDate is null")
                @Where(clause = "deletion_date is null")
                class Hallmarking {
                    String name;
                }
                """,
                """
                import jakarta.persistence.Entity;
                import jakarta.persistence.NamedQuery;
                import org.hibernate.annotations.SQLDelete;
                import org.hibernate.annotations.SQLRestriction;

                @Entity
                @SQLDelete(sql = "UPDATE hallmarking SET deletion_date = now() WHERE id = ?")
                @NamedQuery(name = "findHallmarkingById", query = "SELECT h FROM Hallmarking h WHERE h.id = ?1 AND h.deletionDate is null")
                @SQLRestriction("deletion_date is null")
                class Hallmarking {
                    String name;
                }
                """
        ));
    }

    @Test
    void doesNotChangeWhenNoMatchingImports() {
        rewriteRun(java(
                """
                import jakarta.persistence.Entity;
                import org.hibernate.annotations.SQLDelete;

                @Entity
                @SQLDelete(sql = "UPDATE my_entity SET deleted = true WHERE id = ?")
                class MyEntity {
                    String name;
                }
                """
        ));
    }
}

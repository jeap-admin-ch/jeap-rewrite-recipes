package ch.admin.bit.jeap.openrewrite.recipe.testing;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class AddMockitoExtensionToSpringTestsTest implements RewriteTest {

    @Override
    public void defaults(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(new AddMockitoExtensionToSpringTests())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void addsMockitoExtensionToSpringJUnitConfigTest() {
        rewriteRun(java(
                """
                import org.mockito.Mock;
                import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

                @SpringJUnitConfig
                class ListenerTest {
                    @Mock Object acknowledgment;
                }
                """,
                """
                import org.mockito.Mock;
                import org.mockito.junit.jupiter.MockitoSettings;
                import org.mockito.quality.Strictness;
                import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

                @SpringJUnitConfig
                @MockitoSettings(strictness = Strictness.LENIENT)
                class ListenerTest {
                    @Mock Object acknowledgment;
                }
                """
        ));
    }

    @Test
    void addsMockitoExtensionAlongsideSpringExtension() {
        rewriteRun(java(
                """
                import org.junit.jupiter.api.extension.ExtendWith;
                import org.mockito.Mock;
                import org.springframework.test.context.junit.jupiter.SpringExtension;

                @ExtendWith(SpringExtension.class)
                class MapperTest {
                    @Mock Object collaborator;
                }
                """,
                """
                import org.junit.jupiter.api.extension.ExtendWith;
                import org.mockito.Mock;
                import org.mockito.junit.jupiter.MockitoSettings;
                import org.mockito.quality.Strictness;
                import org.springframework.test.context.junit.jupiter.SpringExtension;

                @ExtendWith(SpringExtension.class)
                @MockitoSettings(strictness = Strictness.LENIENT)
                class MapperTest {
                    @Mock Object collaborator;
                }
                """
        ));
    }

    @Test
    void addsMockitoExtensionForCaptorInSpringBootTest() {
        rewriteRun(java(
                """
                import org.mockito.ArgumentCaptor;
                import org.mockito.Captor;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest
                class ControllerTest {
                    @Captor ArgumentCaptor<String> captor;
                }
                """,
                """
                import org.mockito.ArgumentCaptor;
                import org.mockito.Captor;
                import org.mockito.junit.jupiter.MockitoSettings;
                import org.mockito.quality.Strictness;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest
                @MockitoSettings(strictness = Strictness.LENIENT)
                class ControllerTest {
                    @Captor ArgumentCaptor<String> captor;
                }
                """
        ));
    }

    @Test
    void addsMockitoExtensionForCaptorInDataJpaTest() {
        rewriteRun(java(
                """
                import org.mockito.ArgumentCaptor;
                import org.mockito.Captor;
                import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

                @DataJpaTest
                class RepositoryTest {
                    @Captor ArgumentCaptor<String> captor;
                }
                """,
                """
                import org.mockito.ArgumentCaptor;
                import org.mockito.Captor;
                import org.mockito.junit.jupiter.MockitoSettings;
                import org.mockito.quality.Strictness;
                import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

                @DataJpaTest
                @MockitoSettings(strictness = Strictness.LENIENT)
                class RepositoryTest {
                    @Captor ArgumentCaptor<String> captor;
                }
                """
        ));
    }

    @Test
    void leavesExistingMockitoExtensionUntouched() {
        rewriteRun(java(
                """
                import org.junit.jupiter.api.extension.ExtendWith;
                import org.mockito.Mock;
                import org.mockito.junit.jupiter.MockitoExtension;
                import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

                @ExtendWith(MockitoExtension.class)
                @SpringJUnitConfig
                class ListenerTest {
                    @Mock Object acknowledgment;
                }
                """
        ));
    }

    @Test
    void leavesPlainMockitoUnitTestUntouched() {
        rewriteRun(java(
                """
                import org.mockito.Mock;

                class UnitTest {
                    @Mock Object collaborator;
                }
                """
        ));
    }

    @Test
    void leavesSpringTestWithoutPlainMockUntouched() {
        rewriteRun(java(
                """
                import org.springframework.test.context.bean.override.mockito.MockitoBean;
                import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

                @SpringJUnitConfig
                class ListenerTest {
                    @MockitoBean Object collaborator;
                }
                """
        ));
    }
}

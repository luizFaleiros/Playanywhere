package br.com.playanywhere.playanywhere.install;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtractionDependencyCheckerTest {

    @Test
    @DisplayName("Deve verificar dependências de extração e gerar relatório completo")
    void shouldCheckDependenciesAndGenerateReport() {
        ExtractionDependencyChecker.ExtractionReport report = ExtractionDependencyChecker.checkDependencies();
        assertNotNull(report);
        assertNotNull(report.tools());
        assertFalse(report.tools().isEmpty());
        assertNotNull(report.missingTools());
        assertNotNull(report.osFamily());
        assertNotNull(report.suggestedCommand());
        assertNotNull(report.downloadLinks());
        assertNotNull(report.summaryText());

        assertTrue(report.summaryText().contains("Status das Ferramentas de Descompressão"));
        assertTrue(report.downloadLinks().size() >= 2);
    }

    @Test
    @DisplayName("Deve conter ferramentas esperadas no relatório")
    void shouldContainExpectedToolsInReport() {
        ExtractionDependencyChecker.ExtractionReport report = ExtractionDependencyChecker.checkDependencies();
        boolean hasInnoExtractEntry = report.tools().stream().anyMatch(t -> t.name().equals("innoextract"));
        boolean has7zEntry = report.tools().stream().anyMatch(t -> t.name().startsWith("7-Zip"));
        boolean hasUnarEntry = report.tools().stream().anyMatch(t -> t.name().equals("unar"));

        assertTrue(hasInnoExtractEntry);
        assertTrue(has7zEntry);
        assertTrue(hasUnarEntry);
    }
}

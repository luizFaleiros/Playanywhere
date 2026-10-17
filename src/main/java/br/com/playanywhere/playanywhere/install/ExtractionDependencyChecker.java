package br.com.playanywhere.playanywhere.install;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * Verificador abrangente de dependências e ferramentas de descompressão/unzip para o PlayAnywhere.
 * Valida a presença de utilitários essenciais (innoextract, 7z, unar, cabextract, unzip)
 * e fornece instruções personalizadas por sistema operacional / distribuição Linux.
 */
public class ExtractionDependencyChecker {
    private static final Logger LOGGER = Logger.getLogger(ExtractionDependencyChecker.class.getName());

    public record ToolStatus(String name, boolean available, String resolvedPath) {
    }

    public record ExtractionReport(
            boolean isReady,
            boolean isOptimal,
            List<ToolStatus> tools,
            List<String> missingTools,
            DistroDetector.OsFamily osFamily,
            DistroDetector.DistroInfo distroInfo,
            String suggestedCommand,
            Map<String, String> downloadLinks,
            String summaryText
    ) {
    }

    /**
     * Executa a verificação completa das ferramentas de extração e descompressão.
     */
    public static ExtractionReport checkDependencies() {
        DistroDetector.OsFamily osFamily = DistroDetector.detectOsFamily();
        DistroDetector.DistroInfo distroInfo = (osFamily == DistroDetector.OsFamily.LINUX)
                ? DistroDetector.detectLinuxDistro()
                : null;

        List<ToolStatus> tools = new ArrayList<>();
        List<String> missingTools = new ArrayList<>();

        // 1. innoextract
        Optional<Path> inno = InnoExtractProvisioner.findSystemInnoExtract();
        if (inno.isPresent()) {
            tools.add(new ToolStatus("innoextract", true, inno.get().toAbsolutePath().toString()));
        } else {
            String innoPath = findBinary("innoextract", "innoextract.exe");
            if (innoPath != null) {
                tools.add(new ToolStatus("innoextract", true, innoPath));
            } else {
                tools.add(new ToolStatus("innoextract", false, null));
                missingTools.add("innoextract");
            }
        }

        // 2. 7-Zip (7z, 7za, 7zr)
        String sevenZip = findBinary("7z", "7za", "7zr", "7z.exe");
        if (sevenZip != null) {
            tools.add(new ToolStatus("7-Zip (7z)", true, sevenZip));
        } else {
            tools.add(new ToolStatus("7-Zip (7z)", false, null));
            missingTools.add("7-Zip (7z / p7zip)");
        }

        // 3. unar
        String unar = findBinary("unar", "unar.exe");
        if (unar != null) {
            tools.add(new ToolStatus("unar", true, unar));
        } else {
            tools.add(new ToolStatus("unar", false, null));
            missingTools.add("unar");
        }

        // 4. cabextract
        String cab = findBinary("cabextract", "cabextract.exe");
        if (cab != null) {
            tools.add(new ToolStatus("cabextract", true, cab));
        } else {
            tools.add(new ToolStatus("cabextract", false, null));
        }

        // 5. unzip
        String unzip = findBinary("unzip", "unzip.exe");
        if (unzip != null) {
            tools.add(new ToolStatus("unzip", true, unzip));
        } else {
            tools.add(new ToolStatus("unzip", false, null));
        }

        boolean hasInnoExtract = tools.stream().anyMatch(t -> t.name().startsWith("innoextract") && t.available());
        boolean has7z = tools.stream().anyMatch(t -> t.name().startsWith("7-Zip") && t.available());
        boolean hasUnar = tools.stream().anyMatch(t -> t.name().startsWith("unar") && t.available());

        // Pronto para uso básico: se ao menos uma ferramenta principal de extração estiver disponível
        boolean isReady = hasInnoExtract || has7z || hasUnar;

        // Ótimo: se tanto o innoextract quanto 7z (ou unar) estiverem instalados
        boolean isOptimal = hasInnoExtract && (has7z || hasUnar);

        Map<String, String> downloadLinks = getDownloadLinks(osFamily);
        String suggestedCommand = resolveSuggestedCommand(osFamily, distroInfo);
        String summary = buildSummary(osFamily, distroInfo, tools, missingTools, suggestedCommand, downloadLinks, isOptimal);

        LOGGER.info(() -> "Verificação de ferramentas de descompressão concluída. isReady=" + isReady + ", isOptimal=" + isOptimal);

        return new ExtractionReport(
                isReady,
                isOptimal,
                tools,
                missingTools,
                osFamily,
                distroInfo,
                suggestedCommand,
                downloadLinks,
                summary
        );
    }

    private static Map<String, String> getDownloadLinks(DistroDetector.OsFamily osFamily) {
        Map<String, String> links = new LinkedHashMap<>();
        if (osFamily == DistroDetector.OsFamily.WINDOWS) {
            links.put("7-Zip (Windows Oficial)", "https://www.7-zip.org/download.html");
            links.put("innoextract (Windows Portable)", "https://constexpr.org/innoextract/");
        } else if (osFamily == DistroDetector.OsFamily.MACOS) {
            links.put("Homebrew (Recomendado)", "https://brew.sh/");
            links.put("innoextract (macOS)", "https://constexpr.org/innoextract/");
            links.put("The Unarchiver / unar", "https://theunarchiver.com/command-line");
            links.put("7-Zip para macOS", "https://www.7-zip.org/download.html");
        } else {
            links.put("innoextract (Linux / Código-fonte)", "https://constexpr.org/innoextract/");
            links.put("7-Zip / p7zip (Linux)", "https://www.7-zip.org/download.html");
            links.put("unar (The Unarchiver CLI)", "https://theunarchiver.com/command-line");
        }
        return links;
    }

    private static String resolveSuggestedCommand(DistroDetector.OsFamily osFamily, DistroDetector.DistroInfo distroInfo) {
        if (osFamily == DistroDetector.OsFamily.LINUX) {
            return (distroInfo != null) ? distroInfo.installCommand() : "sudo apt install -y innoextract p7zip-full unar || sudo dnf install -y innoextract p7zip unar";
        } else if (osFamily == DistroDetector.OsFamily.MACOS) {
            return "brew install innoextract p7zip unar";
        } else if (osFamily == DistroDetector.OsFamily.WINDOWS) {
            return "winget install 7zip.7zip && winget install constexpr.innoextract";
        }
        return "";
    }

    private static String buildSummary(
            DistroDetector.OsFamily osFamily,
            DistroDetector.DistroInfo distroInfo,
            List<ToolStatus> tools,
            List<String> missingTools,
            String suggestedCommand,
            Map<String, String> downloadLinks,
            boolean isOptimal
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 Status das Ferramentas de Descompressão (PlayAnywhere):\n\n");

        if (osFamily == DistroDetector.OsFamily.LINUX && distroInfo != null) {
            sb.append("🐧 Sistema Operacional: ").append(distroInfo.name()).append(" (").append(distroInfo.id()).append(")\n");
            sb.append("📦 Gerenciador de Pacotes: ").append(distroInfo.packageManager()).append("\n\n");
        } else if (osFamily == DistroDetector.OsFamily.MACOS) {
            sb.append("🍎 Sistema Operacional: macOS\n\n");
        } else if (osFamily == DistroDetector.OsFamily.WINDOWS) {
            sb.append("🪟 Sistema Operacional: Microsoft Windows\n\n");
        } else {
            sb.append("💻 Sistema Operacional: ").append(System.getProperty("os.name")).append("\n\n");
        }

        sb.append("Ferramentas Detectadas:\n");
        for (ToolStatus t : tools) {
            if (t.available()) {
                sb.append("  ✅ ").append(t.name()).append(": ").append(t.resolvedPath()).append("\n");
            } else {
                sb.append("  ❌ ").append(t.name()).append(": Não instalado\n");
            }
        }
        sb.append("\n");

        if (!missingTools.isEmpty()) {
            sb.append("⚠️ Ferramentas recomendadas ausentes: ").append(String.join(", ", missingTools)).append("\n\n");

            if (osFamily == DistroDetector.OsFamily.LINUX || osFamily == DistroDetector.OsFamily.MACOS) {
                sb.append("👉 Comando recomendado para instalação no terminal:\n");
                sb.append("   ").append(suggestedCommand).append("\n\n");
            } else if (osFamily == DistroDetector.OsFamily.WINDOWS) {
                sb.append("👉 Comando via terminal (Winget):\n");
                sb.append("   ").append(suggestedCommand).append("\n\n");
            }

            if (!downloadLinks.isEmpty()) {
                sb.append("🔗 Links para download manual:\n");
                downloadLinks.forEach((name, url) -> sb.append("   • ").append(name).append(": ").append(url).append("\n"));
            }
        } else {
            sb.append("✨ Todas as ferramentas essenciais de descompressão estão instaladas e configuradas!\n");
        }

        return sb.toString();
    }

    private static String findBinary(String... names) {
        for (String name : names) {
            String pathEnv = System.getenv("PATH");
            if (pathEnv != null) {
                for (String dir : pathEnv.split(File.pathSeparator)) {
                    if (dir.isBlank()) continue;
                    Path p = Paths.get(dir, name);
                    if (isValidExecutable(p)) {
                        return p.toAbsolutePath().toString();
                    }
                }
            }

            List<Path> candidateDirs = List.of(
                    Paths.get(System.getProperty("user.home"), ".playanywhere", "bin"),
                    Paths.get(System.getProperty("user.home"), ".local", "bin"),
                    Paths.get("/usr/local/bin"),
                    Paths.get("/usr/bin"),
                    Paths.get("/bin"),
                    Paths.get("/opt/homebrew/bin"),
                    Paths.get("/home/linuxbrew/.linuxbrew/bin"),
                    Paths.get("C:\\Program Files\\7-Zip"),
                    Paths.get("C:\\Program Files (x86)\\7-Zip")
            );

            for (Path dir : candidateDirs) {
                Path p = dir.resolve(name);
                if (isValidExecutable(p)) {
                    return p.toAbsolutePath().toString();
                }
            }
        }
        return null;
    }

    private static boolean isValidExecutable(Path path) {
        if (path == null || !Files.exists(path)) return false;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return Files.isRegularFile(path);
        }
        return Files.isExecutable(path) || Files.isRegularFile(path);
    }
}

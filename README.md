# 🎮 PlayAnywhere GOG Edition

**Aplicação para instalar e rodar jogos da sua biblioteca GOG em CD, DVD, Pen Drive, HD externo com boot automático!**

---

## 📋 Funcionalidades Principais

| ✅ | Feature |
|-----|---------|
| 🔐 | Autenticação real via OAuth2 API GOG |
| 📚 | Lista de jogos alfabética da sua biblioteca |
| 💿 | Instalação em CD, DVD, USB, HD Externo, diretório local |
| 🚀 | Driver de boot automático para iniciar o jogo ao inserir dispositivo |
| 🎨 | Interface moderna estilo GOG (dark theme) |

---

## 🛠️ Requisitos

- **Java 21+** (JDK 23 recomendado)
- **Client ID da GOG** - obtido em [Portal do Cliente](https://www.gog.com/en/apps/opening_your_own_client)

---

## 📦 Estrutura Unificada

Tudo consolidado no pacote `playanywhere`:

```
src/main/java/br/com/playanywhere/playanywhere/
├── Launcher.java              ← Entry point (console ou GUI)
├── api/GOGApi.java            ← API OAuth2 + lista biblioteca
├── controller/
│   ├── LoginController.java   ← Login OAuth2
│   ├── GameGridController.java← Grade alfabética de jogos
│   ├── InstallController.java ← Instalação em dispositivos
│   └── BootDriverController.java ← Driver boot automático
└── model/
    ├── Product.java           ← Dados do produto GOG
    ├── GalleryImage.java      ← Capas
    ├── Screenshot.java        ← Screenshots
    ├── Price.java             ← Preço/desconto
    ├── StorageDevice.java     ← Dispositivos físicos
    └── StorageType.java       ← Enum: CD, DVD, USB

src/main/resources/br/com/playanywhere/playanywhere/view/
├── login-view.fxml
├── game-grid-view.fxml
├── install-view.fxml
└── boot-driver-view.fxml
```

---

## 🚀 Como Rodar

### 1. Configurar Client ID da GOG

Abra `Launcher.java` ou `GOGApi.java` e substitua:

```java
private static final String GOG_CLIENT_ID = "YOUR_CLIENT_ID_HERE";
```

Por seu **Client ID real** obtido no [portal do cliente GOG](https://www.gog.com/en/apps/opening_your_own_client)

### 2. Compilar o Projeto

```bash
# Criar diretório target
mkdir -p target/classes/br/com/playanywhere/playanywhere/{api,controller,model}

# Compilar Java sources (requisita JavaFX SDK e Jackson no classpath)
javac -d target/classes \
    src/main/java/br/com/playanywhere/playanywhere/*.java \
    src/main/java/br/com/playanywhere/playanywhere/api/*.java \
    src/main/java/br/com/playanywhere/playanywhere/controller/*.java \
    src/main/java/br/com/playanywhere/playanywhere/model/*.java

# Criar JAR executável
jar -cf target/PlayAnywhere-1.0-SNAPSHOT.jar \
    -C target/classes br/com/playanywhere/playanywhere
```

### 3. Executar

#### Com JavaFX (Recomendado)

```bash
# Baixe JavaFX SDK: https://openjfx.io
java -jar target/PlayAnywhere-1.0-SNAPSHOT.jar \
     --module-path /caminho/java-fx/lib \
     --add-modules javafx.controls,javafx.fxml
```

#### Versão Console (Java 21+)

```bash
java -cp target/classes br.com.playanywhere.playanywhere.Launcher
```

---

## 🎯 Uso Passo a Passo

### 1. Login na Conta GOG
Insira email e senha da sua conta GOG para autenticação OAuth2.

### 2. Lista de Jogos
A API GOG retorna todos os seus jogos com:
- Nome do jogo
- Capa (primeira imagem da galeria)
- Developer, Publisher
- Rating, Preço atual com desconto
- **Ordenados alfabeticamente** ✨

### 3. Selecionar Jogo para Instalar
Clique em um jogo na grade → Tela de instalação aparece.

### 4. Escolher Dispositivo de Instalação
Opções disponíveis:
- 💿 CD (~1 GB)
- DVD-5 (~4.7 GB)
- DVD-9 (~8.5 GB)  
- USB Pen Drive (32GB+)
- 📁 HD Externo (navegar dispositivos)
- 🖥️ Diretório do PC

### 5. Instalação
O sistema simula o processo de instalação com progresso visual.

### 6. Criar Driver Boot Automático (Opcional)
Após instalar, pode criar `AUTOBOOT.BAT` para que o jogo inicie automaticamente ao inserir o drive!

---

## 🔐 API OAuth2 Implementada

A aplicação usa a **API oficial da GOG** conforme documentação:  
https://gogapidocs.readthedocs.io/en/latest/

### Flows OAuth2 Implementados:

1. **Authorization Code Grant**:
   ```java
   // Build URL de autorização
   String authUrl = "https://gog.com/oauth/authorize?" +
       "client_id=" + clientId + "&" +
       "scope=library%20products";
   
   // Troca code por token
   String accessToken = exchangeCodeForToken(code, clientId);
   ```

2. **Validação de Token** (expira em 1 hora):
   ```java
   if (!api.isValidToken()) {
       // Faça login novamente
   }
   ```

3. **Listar Biblioteca**:
   ```java
   List<Product> games = api.getLibraryProducts();
   // endpoint: https://content.gog.com/api/library/v2/products
   ```

### Modelos GOG Mapeados:

#### Product (Jogo)
```java
{
  "title": "Stranger of Paradise",
  "developer": "ATLUS", 
  "publisher": "GOG.com",
  "rating": 4.2,
  "images": [...],
  "price": {
    "finalPrice": 5990,
    "discount": 85
  }
}
```

#### GalleryImage (Capa)
- Primeira imagem da galeria usada como capa
- URLs reais da API GOG

---

## 📊 Dispositivos Suportados

| Tipo | Capacidade | Uso Ideal |
|------|-----------|-----------|
| CD-ROM | ~1 GB | Jogos compactados leves |
| DVD-5 | ~4.7 GB | Jogos padrão 2005-2010 |
| DVD-9 | ~8.5 GB | Jogos com assets completos |
| USB Drive | 8-128 GB | Portabilidade máxima |
| HD Externo | 250GB-4TB | Bibliotecas completas |

---

## 🚧 Próximas Funcionalidades

- [ ] Download real do instalador GOG
- [ ] Progress bar visual de instalação
- [ ] Navegador de pastas para diretórios locais
- [ ] Suporte a múltiplas contas GOG
- [ ] Modo offline com cache local
- [ ] Backup/restauração da biblioteca

---

## 📝 Troubleshooting

| Problema | Solução |
|----------|---------|
| Erro de autenticação OAuth2 | Substitua `YOUR_CLIENT_ID_HERE` pelo Client ID real no portal GOG |
| Token inválido/expirado | Faça login novamente (tokens expiram em 1 hora) |
| Nenhum jogo listado | Verifique conexão e Client ID configurado |
| Erro de compilação JavaFX | Baixe JavaFX SDK: https://openjfx.io |

---

## 📄 Documentação Completa

- [GOG API Docs](https://gogapidocs.readthedocs.io/en/latest/)
- [Portal Cliente GOG](https://www.gog.com/en/apps/opening_your_own_client)
- [OpenJFX Downloads](https://openjfx.io/)

---

<div align="center">
    🎮 <strong>PlayAnywhere v1.0-SNAPSHOT</strong><br/>
    <span style="color:#888;">"Um jogo em qualquer lugar!"</span>
</div>
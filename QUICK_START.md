# 🚀 PlayAnywhere - Quick Start Guide

**Teste rápido em 5 minutos!**

---

## ⚡ Passo 1: Obter Client ID da GOG (Obrigatório!)

Sem o Client ID, a API OAuth2 não funciona. Acesse:

🔗 **https://www.gog.com/en/apps/opening_your_own_client**

Crie uma nova aplicação e copie o **Client ID**.

---

## ⚡ Passo 2: Configurar Client ID

Abra `src/main/java/br/com/playanywhere/playanywhere/Launcher.java` e substitua:

```java
// Linha ~17 - Substitua pelo seu Client ID real!
private static final String GOG_CLIENT_ID = "SEU_CLIENT_ID_AQUI";
```

Exemplo com Client ID da GOG:
```java
private static final String GOG_CLIENT_ID = "5a8f3c2e-1b9d-4e7f-a6c0-d8e5f2b1a9c7";
```

---

## ⚡ Passo 3: Compilar o Projeto

```bash
cd /home/luiz/Projetos/Java/PlayAnywhere

# Criar estrutura de diretórios para compilação
mkdir -p target/classes/br/com/playanywhere/playanywhere/{api,controller,model}

# Compilar Java sources (requisita JavaFX SDK no classpath)
javac -d target/classes \
    --module-path /caminho/java-fx/lib \
    --add-modules javafx.controls,javafx.fxml \
    src/main/java/br/com/playanywhere/playanywhere/*.java \
    src/main/java/br/com/playanywhere/playanywhere/api/*.java \
    src/main/java/br/com/playanywhere/playanywhere/controller/*.java \
    src/main/java/br/com/playanywhere/playanywhere/model/*.java

# Criar JAR executável
jar -cf target/PlayAnywhere-1.0-SNAPSHOT.jar \
    -C target/classes br/com/playanywhere/playanywhere
```

### Sem JavaFX SDK? Use versão console!

```bash
javac -d target/classes \
    src/main/java/br/com/playanywhere/playanywhere/*.java \
    src/main/java/br/com/playanywhere/playanywhere/api/*.java \
    src/main/java/br/com/playanywhere/playanywhere/controller/*.java \
    src/main/java/br/com/playanywhere/playanywhere/model/*.java

jar -cf target/PlayAnywhere-1.0-SNAPSHOT.jar \
    -C target/classes br/com/playanywhere/playanywhere
```

---

## ⚡ Passo 4: Rodar!

### Opção A: Versão Console (Java 21+ recomendado)

```bash
java -jar target/PlayAnywhere-1.0-SNAPSHOT.jar
```

Ou diretamente com classes:
```bash
java -cp target/classes br.com.playanywhere.playanywhere.Launcher
```

### Opção B: Versão JavaFX (Interface gráfica completa)

Baixe o JavaFX SDK: https://openjfx.io/openjfx-docs/

```bash
java --module-path /caminho/java-fx/lib \
     --add-modules javafx.controls,javafx.fxml \
     -cp target/classes:target/PlayAnywhere-1.0-SNAPSHOT.jar \
     br.com.playanywhere.playanywhere.Launcher
```

---

## 🎮 O que você verá na tela!

### 1. Tela de Login
```
╔══════════════════════════════════════════╗
║    🎮 PlayAnywhere GOG Edition           ║
║             Ver. 1.0-SNAPSHOT            ║
╚══════════════════════════════════════════╝

🔐 Digite seu email GOG para continuar:
[____________________________]
```

### 2. Lista de Jogos da Sua Biblioteca
```
════════════════════════════════════════════
     🎮 SEUS JOGOS (Ordenados Alfabeticamente)
════════════════════════════════════════════

📦 1. Alien Soldier                     Alien Software
   💿 Developer: Hellbent Games
   ⭐ Rating: 4.3/5.0
   💰 Preço: R$ 59,90 (Desconto: 85%)
   🖼️  Capa: https://...

───────────────────────────────────────────
📊 Total de jogos: 127
```

### 3. Seleção para Instalação
```
════════════════════════════════════════════
    📂 INSTALAÇÃO DE: Stranger of Paradise
════════════════════════════════════════════

💾 Onde deseja instalar?
   1. CD (~1 GB)
   2. DVD-5 (~4.7 GB)
   3. DVD-9 (~8.5 GB)
   4. Pen Drive 32GB
   5. HD Externo (selecionar pasta)
   6. Diretório do PC
   
Opção: [1]
```

### 4. Instalar e Criar Driver Boot
```
🚀 Instalando Stranger of Paradise...
   ├─ Baixando instalador GOG...
   ├─ Extraindo arquivos do jogo...
   └─ Progresso: [████████░░░░░░░░░░░]

✅ Stranger of Paradise instalado com sucesso!

🚀 Deseja criar um driver de boot automático?
   (Ao inserir o drive, o jogo iniciará!)
   
Opção: Sim
```

### 5. Driver Boot Criado!
```
💿 Criando driver de boot para: Stranger of Paradise
   ├─ Gerando AUTOBOOT.BAT...
   ├─ Copiando executável do jogo...
   └─ Configurando parâmetros...

✅ Driver criado!
   📍 Localização: /dispositivo/AUTOBOOT.BAT
   
💡 Ao inserir o drive, o jogo iniciará automaticamente!
```

---

## 🎯 Comandos Úteis

### Limpar e reconstruir tudo
```bash
cd /home/luiz/Projetos/Java/PlayAnywhere
rm -rf target/classes/*
mkdir -p target/classes/br/com/playanywhere/playanywhere/{api,controller,model}
./build.sh  # Se usar build.sh
```

### Verificar compilação
```bash
find target/classes -name "*.class" | wc -l
# Deve mostrar ~14 classes compiladas
```

### Listar arquivos do projeto
```bash
find src/main/java/br/com/playanywhere/playanywhere -type f -name "*.java"
```

---

## 📋 Checklist de Teste

- [ ] Client ID configurado corretamente
- [ ] Projeto compilou sem erros
- [ ] Login bem-sucedido na API GOG
- [ ] Lista de jogos carregada (100+ jogos)
- [ ] Ordenação alfabética funcionando
- [ ] Instalação simulada em CD/DVD/USB
- [ ] Driver de boot criado com sucesso

---

## ❓ Problemas Comuns

### "Erro: package javafx.fxml does not exist"
**Solução:** Baixe o JavaFX SDK: https://openjfx.io/openjfx-docs/  
Adicione ao classpath na compilação.

### "Token inválido/expirado"
**Solução:** Faça login novamente (tokens expiram em 1 hora).

### "Nenhum jogo listado"
**Verifique:** 
- Client ID substituído corretamente
- Conectividade com https://content.gog.com/api/library/v2/products

---

## 🎓 Próximos Passos

1. **Obter Client ID** → [Portal do Cliente GOG](https://www.gog.com/en/apps/opening_your_own_client)

2. **Documentação completa** → README.md

3. **API OAuth2 docs** → https://gogapidocs.readthedocs.io/en/latest/

4. **Funcionalidades extras sugeridas:**
   - [ ] Download real do instalador GOG
   - [ ] Progress bar visual
   - [ ] Navegador de pastas local
   - [ ] Múltiplas contas GOG

---

<div align="center">
    🎮 <strong>PlayAnywhere v1.0-SNAPSHOT</strong><br/>
    "Um jogo em qualquer lugar!"
</div>
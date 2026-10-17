#!/bin/bash
# Script de build para PlayAnywhere - JavaFX GOG Edition

set -e

cd "$(dirname "$0")"

echo "🔨 Compilando e empacotando PlayAnywhere via Maven..."
./mvnw clean package

echo ""
echo "✅ JAR executável criado: target/PlayAnywhere-1.0-SNAPSHOT.jar"
echo ""
echo "🎮 Para executar:"
echo "   java -jar target/PlayAnywhere-1.0-SNAPSHOT.jar"
echo "   ou: ./mvnw javafx:run"
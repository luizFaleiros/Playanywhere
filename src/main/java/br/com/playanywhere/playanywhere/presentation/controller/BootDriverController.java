package br.com.playanywhere.playanywhere.presentation.controller;

import br.com.playanywhere.playanywhere.domain.model.Product;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Window;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Controller para gerenciamento e criação de drivers de auto-boot para jogos portáteis.
 */
public class BootDriverController {

    @FXML private Label driverGameTitle;
    @FXML private Button createDriverButton;
    @FXML private Button removeDriverButton;
    @FXML private Label driverStatusLabel;
    @FXML private TextField driverPathField;

    private Product selectedProduct;

    @FXML
    public void initialize() {
        if (driverStatusLabel != null) {
            driverStatusLabel.setText("Aguardando ação...");
        }
    }

    public void setProduct(Product product) {
        this.selectedProduct = product;
        if (driverGameTitle != null && product != null) {
            driverGameTitle.setText(product.getTitle());
        }
    }

    @FXML
    public void handleCreateDriver(ActionEvent event) {
        if (driverStatusLabel != null) {
            driverStatusLabel.setText("Criando driver de boot...");
        }

        try {
            File bootScript = new File(System.getProperty("user.home"), "auto-boot.bat");
            try (FileWriter writer = new FileWriter(bootScript)) {
                writer.write("@echo off\n");
                writer.write("echo Iniciando " + (selectedProduct != null ? selectedProduct.getTitle() : "Jogo") + "...\n");
                writer.write("start game.exe\n");
            }

            if (driverPathField != null) {
                driverPathField.setText(bootScript.getAbsolutePath());
            }
            if (driverStatusLabel != null) {
                driverStatusLabel.setText("✅ Driver criado com sucesso em: " + bootScript.getAbsolutePath());
            }
        } catch (IOException e) {
            if (driverStatusLabel != null) {
                driverStatusLabel.setText("❌ Erro ao criar driver: " + e.getMessage());
            }
        }
    }

    @FXML
    public void handleRemoveDriver(ActionEvent event) {
        if (driverPathField != null && !driverPathField.getText().isEmpty()) {
            File file = new File(driverPathField.getText());
            if (file.exists() && file.delete()) {
                driverPathField.setText("");
                if (driverStatusLabel != null) {
                    driverStatusLabel.setText("🗑️ Driver removido com sucesso.");
                }
            } else {
                if (driverStatusLabel != null) {
                    driverStatusLabel.setText("⚠️ Arquivo do driver não encontrado.");
                }
            }
        }
    }
}

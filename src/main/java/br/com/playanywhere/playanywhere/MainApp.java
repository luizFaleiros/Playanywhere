package br.com.playanywhere.playanywhere;

import br.com.playanywhere.playanywhere.drive.MountMonitor;
import br.com.playanywhere.playanywhere.install.ExtractionDependencyDialog;
import br.com.playanywhere.playanywhere.presentation.controller.LoginController;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.logging.Logger;

public class MainApp extends Application {
    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());

    @Override
    public void start(Stage primaryStage) {
        LOGGER.info("Iniciando PlayAnywhere GOG Edition...");

        // Iniciar monitor de montagem de drives
        MountMonitor.getInstance().start();
        
        LoginController loginController = new LoginController();
        loginController.setStage(primaryStage);
        Parent root = loginController.getView();
        
        Scene scene = new Scene(root, 1024, 768);
        primaryStage.setTitle("PlayAnywhere GOG Edition");
        primaryStage.setScene(scene);
        primaryStage.show();
        LOGGER.info("Janela principal exibida com sucesso.");

        // Verificar ferramentas de descompressão/unzip necessárias no início do projeto
        ExtractionDependencyDialog.checkAndShowIfNeeded(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

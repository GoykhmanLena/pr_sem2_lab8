package ru.lenok.client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import ru.lenok.common.CommandResponse;
import ru.lenok.common.models.LabWorkWithKey;

import java.io.File;
import java.util.List;

public class MainForm {
    private final LanguageManager languageManager = LanguageManager.getInstance();
    private final ClientService clientService = ClientService.getInstance();
    private final ObservableList<LabWorkWithKey> labWorks = FXCollections.observableArrayList();
    private final FilteredList<LabWorkWithKey> filteredLabWorks = new FilteredList<>(labWorks, s -> true);
    private final Stage stage;
    private final BorderPane root = new BorderPane();
    private final Scene scene = new Scene(root, 1200, 800);
    private boolean initialized = false;
    private Button editButton;
    private Button deleteButton;

    public MainForm(List<LabWorkWithKey> labWorkList, Stage stage) {
        this.stage = stage;
        labWorks.addAll(labWorkList);
        clientService.registerNotificationListener(this::notifyListChanged);

        this.stage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });
    }

    public void notifyListChanged(List<LabWorkWithKey> list) {
        Platform.runLater(() -> {
            labWorks.setAll(list);
            start();
        });
    }

    public void start() {
        stage.setTitle(languageManager.get("title.main"));
        double[] oldPositions = null;
        if (!initialized) {
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();
            initialized = true;
        }
        else {
            SplitPane oldSplitPane = (SplitPane) root.getCenter();
            if (oldSplitPane != null) {
                oldPositions = oldSplitPane.getDividerPositions();
            }
        }

        root.setTop(createTopBar());
        root.setCenter(createSplitPane());

        if (oldPositions != null) {
            ((SplitPane) root.getCenter()).setDividerPositions(oldPositions);
        }
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.TOP_RIGHT);
        topBar.setStyle("-fx-background-color: #f0f0f0");

        Label userLabel = new Label(languageManager.get("user_label") + ": " + clientService.getUser().getUsername());
        ComboBox<String> langBox = new ComboBox<>();
        langBox.getItems().addAll(languageManager.getAllLanguages());
        langBox.getSelectionModel().select(languageManager.getCurrentLanguageName());

        langBox.setOnAction(e -> {
            languageManager.setLanguage(langBox.getSelectionModel().getSelectedItem());
            start();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topBar.getChildren().addAll(userLabel, spacer, langBox);
        return topBar;
    }

    private SplitPane createSplitPane() {
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);

        VBox leftPane = new VBox(10);
        leftPane.setPadding(new Insets(10));

        LabWorkTableView tableView = new LabWorkTableView(filteredLabWorks);

        Button addButton = new Button(languageManager.get("button.create"));
        addButton.setOnAction(e -> {
            LabWorkForm form = new LabWorkForm(null);
            form.showAndWait();
        });

        editButton = new Button(languageManager.get("button.edit"));
        editButton.setOnAction(e -> {
            LabWorkWithKey selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                LabWorkForm form = new LabWorkForm(selected);
                form.showAndWait();
            }
        });

        deleteButton = new Button(languageManager.get("button.delete"));
        Button clearButton = new Button(languageManager.get("button.clear"));
        Button historyButton = new Button(languageManager.get("button.history"));
        Button helpButton = new Button(languageManager.get("button.help"));
        deleteButton.setDisable(true);
        editButton.setDisable(true);

        Button infoButton = new Button(languageManager.get("button.info"));
        Button scriptButton = new Button(languageManager.get("button.script"));

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(24, 24);

        deleteButton.setOnAction(e -> runAsyncWithProgress(progressIndicator, () -> {
            LabWorkWithKey selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Exception error = clientService.deleteLabWork(selected.getKey());
                if (error != null) {
                    Platform.runLater(() -> new Alert(Alert.AlertType.ERROR,
                            languageManager.get("error.delete") + ": " + error).showAndWait());
                }
            }
        }));

        clearButton.setOnAction(e -> runAsyncWithProgress(progressIndicator, () -> {
            Exception error = clientService.clearLabWorks();
            if (error != null) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR,
                        languageManager.get("error.clear") + ": " + error).showAndWait());
            }
        }));

        historyButton.setOnAction(e -> runAsyncWithProgress(progressIndicator, () -> {
            CommandResponse response = clientService.getHistory();
            Platform.runLater(() -> {
                if (response.getError() == null) {
                    showListDialog(languageManager.get("button.history"), response.getOutput());
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle(languageManager.get("button.history"));
                    alert.setHeaderText(null);
                    alert.setContentText(response.getError().toString());
                    alert.showAndWait();
                }
            });
        }));

        helpButton.setOnAction(e -> runAsyncWithProgress(progressIndicator, () -> {
            CommandResponse response = clientService.getHelp();
            Platform.runLater(() -> {
                if (response.getError() == null) {
                    showListDialog(languageManager.get("button.help"), response.getOutput());
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle(languageManager.get("button.help"));
                    alert.setHeaderText(null);
                    alert.setContentText(response.getError().toString());
                    alert.showAndWait();
                }
            });
        }));

        infoButton.setOnAction(e -> runAsyncWithProgress(progressIndicator, () -> {
            CommandResponse response = clientService.getInfo();
            Platform.runLater(() -> {
                if (response.getError() == null) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle(languageManager.get("button.info"));
                    alert.setHeaderText(null);
                    alert.setContentText(response.getOutput());
                    alert.showAndWait();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle(languageManager.get("button.info"));
                    alert.setHeaderText(null);
                    alert.setContentText(response.getError().toString());
                    alert.showAndWait();
                }
            });
        }));

        scriptButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();

            fileChooser.setInitialDirectory(new File("."));
            File selectedFile = fileChooser.showOpenDialog(scriptButton.getScene().getWindow());

            if (selectedFile != null) {
                runAsyncWithProgress(progressIndicator, () -> {
                        clientService.executeScript(selectedFile.getAbsolutePath());
                });
            }
        });

        HBox buttonBar = new HBox(10, addButton, editButton, deleteButton, clearButton, historyButton, helpButton, infoButton, scriptButton, progressIndicator);
        buttonBar.setAlignment(Pos.CENTER_LEFT);

        leftPane.getChildren().addAll(tableView, buttonBar);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        //LabWorkCanvasPane labCanvas = new LabWorkCanvasPane(labWorks);
        LabWorkCanvasPaneLowLevel labCanvas = new LabWorkCanvasPaneLowLevel(labWorks);
        StackPane rightPane = new StackPane(labCanvas);

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                labCanvas.highlight(selected);
                deleteButton.setDisable(false);
                editButton.setDisable(false);
            }
            else {
                deleteButton.setDisable(true);
                editButton.setDisable(true);
            }
        });

        labCanvas.setOnLabWorkSelected(labWork -> {
            tableView.getSelectionModel().select(labWork);
            tableView.scrollTo(labWork);
            deleteButton.setDisable(false);
            editButton.setDisable(false);
        });

        splitPane.getItems().addAll(leftPane, rightPane);
        return splitPane;
    }

    private void runAsyncWithProgress(ProgressIndicator progressIndicator, Runnable runnable) {
        progressIndicator.setVisible(true);

        new Thread(() -> {
            try {
                runnable.run();
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    new Alert(Alert.AlertType.ERROR, ex.toString()).showAndWait();
                });
            }
        }).start();

    }

    private void showListDialog(String title, String multilineText) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);

        ListView<String> listView = new ListView<>();
        String[] lines = multilineText.split("\\R");
        listView.getItems().addAll(lines);
        listView.setPrefSize(600, 400);

        VBox content = new VBox(listView);
        content.setPadding(new Insets(10));

        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }
}

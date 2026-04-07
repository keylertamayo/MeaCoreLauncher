package com.experimento.launcher;

import com.experimento.launcher.model.JvmPresetKind;
import com.experimento.launcher.model.LauncherProfile;
import com.experimento.launcher.model.ServerEntry;
import com.experimento.launcher.mojang.ManifestVersionEntry;
import com.experimento.launcher.paths.LauncherDirectories;
import com.experimento.launcher.service.AutoOptimizerService;
import com.experimento.launcher.service.HardwareProbe;
import com.experimento.launcher.service.JvmPresetService;
import com.experimento.launcher.service.LauncherFacade;
import com.experimento.launcher.util.OfflineUuid;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LauncherApp extends Application {

    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final List<ManifestVersionEntry> allManifestEntries = FXCollections.observableArrayList();
    
    private boolean syncingVersionUi;
    private boolean syncingIdentityUi;

    private LauncherFacade facade;
    private List<LauncherProfile> profiles;
    private LauncherProfile selected;

    // Componentes UI
    private ListView<LauncherProfile> profileList;
    private TextField displayNameField;
    private TextField usernameField;
    private TextField uuidField;
    private ComboBox<ManifestVersionEntry> versionCombo;
    private ComboBox<String> versionFilter;
    private ComboBox<JvmPresetKind> presetCombo;
    private TextArea jvmArea;
    private CheckBox globalMcCheck;
    private TableView<ServerEntry> serverTable;
    private TextArea logArea;
    private Label modHintLabel;
    private Label aternosHint;

    // Botones (para deshabilitar durante procesos)
    private Button installBtn;
    private Button playBtn;
    private Button saveBtn;
    private Button newProfileBtn;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        initData();
        
        BorderPane root = new BorderPane();
        root.setLeft(createProfileSidebar());
        root.setCenter(createMainContent());
        root.setBottom(createActionAndLogSection());
        
        BorderPane.setMargin(root.getCenter(), new Insets(8));

        Scene scene = new Scene(root, 980, 720);
        stage.setTitle(LauncherMetadata.DISPLAY_NAME);
        stage.setScene(scene);
        
        // Inicialización post-UI
        profileList.getSelectionModel().selectFirst();
        stage.show();
        loadVersionManifestAsync();

        stage.setOnCloseRequest(ev -> workers.shutdownNow());
    }

    private void initData() throws Exception {
        LauncherDirectories dirs = LauncherDirectories.fromDefault();
        dirs.ensureBaseDirs();
        facade = new LauncherFacade(dirs);
        profiles = new ArrayList<>(facade.profiles().loadOrCreateDefault());
        for (LauncherProfile p : profiles) {
            LauncherFacade.maybeImportTlauncherJvm(p);
        }
        facade.profiles().save(profiles);
    }

    private ListView<LauncherProfile> createProfileSidebar() {
        profileList = new ListView<>(FXCollections.observableList(profiles));
        profileList.setPrefWidth(240);
        profileList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(LauncherProfile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName + " (" + item.username + ")");
            }
        });
        profileList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> bindProfile(n));
        return profileList;
    }

    private VBox createMainContent() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(10));
        
        VBox centerContent = new VBox(8, 
            createFormSection(), 
            new Separator(), 
            new Label("Servidores (NBT Gzip)"), 
            createServerTableSection(),
            createHintSection()
        );
        
        container.getChildren().add(centerContent);
        VBox.setVgrow(centerContent, Priority.ALWAYS);
        return container;
    }

    private GridPane createFormSection() {
        displayNameField = new TextField();
        usernameField = new TextField();
        uuidField = new TextField();
        uuidField.setPromptText("UUID offline");
        uuidField.setEditable(false); // Recomendado para evitar inconsistencias manuales

        versionFilter = new ComboBox<>(FXCollections.observableArrayList(
                "Todas", "Solo releases", "Solo snapshots", "Clásicas (beta/alpha)"));
        versionFilter.setValue("Todas");
        versionFilter.setOnAction(e -> applyVersionFilter());

        versionCombo = new ComboBox<>();
        versionCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(versionCombo, Priority.ALWAYS);
        setupVersionComboCellFactories();

        presetCombo = new ComboBox<>(FXCollections.observableArrayList(JvmPresetKind.values()));
        presetCombo.setOnAction(e -> handlePresetChange());

        jvmArea = new TextArea();
        jvmArea.setPrefRowCount(2);
        
        globalMcCheck = new CheckBox("Usar ~/.minecraft global (avanzado)");
        globalMcCheck.setOnAction(e -> { if(selected != null) selected.useGlobalMinecraftFolder = globalMcCheck.isSelected(); });

        Button syncUuidBtn = new Button("🔄 Sync UUID");
        syncUuidBtn.setOnAction(e -> syncUuidFromUsername());

        Button refreshManifestBtn = new Button("Actualizar lista");
        refreshManifestBtn.setOnAction(e -> loadVersionManifestAsync());

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        
        int r = 0;
        grid.add(new Label("Nombre Perfil:"), 0, r); grid.add(displayNameField, 1, r);
        r++;
        grid.add(new Label("Usuario (Offline):"), 0, r); grid.add(usernameField, 1, r);
        r++;
        grid.add(new Label("UUID:"), 0, r); grid.add(new HBox(8, uuidField, syncUuidBtn), 1, r);
        r++;
        grid.add(new Label("Versión Juego:"), 0, r); grid.add(new HBox(8, versionFilter, versionCombo, refreshManifestBtn), 1, r);
        r++;
        grid.add(new Label("Optimización:"), 0, r); grid.add(presetCombo, 1, r);
        grid.add(new Label("Argumentos Java:"), 0, r+1); grid.add(jvmArea, 1, r+1);
        grid.add(globalMcCheck, 1, r+2);

        ColumnConstraints col1 = new ColumnConstraints(130);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        setupFieldListeners();

        // Estilo para corregir legibilidad en Linux
        grid.setStyle("-fx-font-family: 'Segoe UI', Helvetica, Arial, sans-serif;");
        
        return grid;
    }

    private void setupFieldListeners() {
        displayNameField.textProperty().addListener((obs, o, n) -> {
            if (selected != null && !syncingIdentityUi) {
                selected.displayName = n;
                syncIdentityFromDisplayName(n);
                profileList.refresh();
            }
        });

        usernameField.textProperty().addListener((obs, o, n) -> {
            if (selected != null && !syncingIdentityUi) {
                selected.username = n;
                syncUuidFromUsername();
            }
        });

        jvmArea.textProperty().addListener((obs, o, n) -> {
            if (selected != null) selected.customJvmArgs = n;
        });

        versionCombo.valueProperty().addListener((obs, o, n) -> {
            if (!syncingVersionUi && selected != null && n != null) {
                selected.lastVersionId = n.id();
            }
        });
    }

    private VBox createServerTableSection() {
        serverTable = new TableView<>();
        serverTable.setPrefHeight(200);
        serverTable.setEditable(true);

        TableColumn<ServerEntry, String> colName = new TableColumn<>("Nombre");
        colName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().name));
        colName.setCellFactory(TextFieldTableCell.forTableColumn());
        colName.setOnEditCommit(ev -> { if(ev.getRowValue() != null) ev.getRowValue().name = ev.getNewValue(); });

        TableColumn<ServerEntry, String> colAddr = new TableColumn<>("IP:Puerto");
        colAddr.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().address));
        colAddr.setCellFactory(TextFieldTableCell.forTableColumn());
        colAddr.setOnEditCommit(ev -> { if(ev.getRowValue() != null) ev.getRowValue().address = ev.getNewValue(); });

        TableColumn<ServerEntry, Boolean> colCracked = new TableColumn<>("Cracked/Aternos");
        colCracked.setCellFactory(cd -> new TableCell<>() {
            @Override protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); }
                else {
                    ServerEntry se = getTableRow().getItem();
                    CheckBox cb = new CheckBox();
                    cb.setSelected(se.crackedServer);
                    cb.setOnAction(ev -> { se.crackedServer = cb.isSelected(); refreshAternosRowHint(se); });
                    setGraphic(cb);
                }
            }
        });

        serverTable.getColumns().addAll(List.of(colName, colAddr, colCracked));
        
        Button addSrv = new Button("➕ Añadir");
        addSrv.setOnAction(e -> addServerToSelected());
        Button delSrv = new Button("➖ Quitar");
        delSrv.setOnAction(e -> removeSelectedServer());

        return new VBox(5, serverTable, new HBox(8, addSrv, delSrv));
    }

    private VBox createHintSection() {
        aternosHint = new Label();
        aternosHint.setWrapText(true);
        aternosHint.setStyle("-fx-text-fill: #888888;");
        
        modHintLabel = new Label();
        modHintLabel.setWrapText(true);
        modHintLabel.setStyle("-fx-font-weight: bold;");

        return new VBox(8, aternosHint, modHintLabel);
    }

    private VBox createActionAndLogSection() {
        newProfileBtn = new Button("Nuevo Perfil");
        newProfileBtn.setOnAction(e -> createNewProfile());

        saveBtn = new Button("Guardar");
        saveBtn.setOnAction(e -> saveProfiles());

        installBtn = new Button("Instalar");
        installBtn.setOnAction(e -> runTask(createInstallTask()));

        playBtn = new Button("¡Jugar!");
        playBtn.setDefaultButton(true);
        playBtn.setStyle("-fx-base: #2d8134;");
        playBtn.setOnAction(e -> runTask(createLaunchTask()));

        HBox topButtons = new HBox(10, newProfileBtn, saveBtn, installBtn, playBtn);
        topButtons.setPadding(new Insets(10));

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(6);

        return new VBox(topButtons, new Label("  Consola de Registro"), logArea);
    }

    // --- Lógica de Negocio y Helpers ---

    private void bindProfile(LauncherProfile p) {
        selected = p;
        if (p == null) {
            clearFields();
            return;
        }

        syncingIdentityUi = true;
        try {
            displayNameField.setText(p.displayName);
            usernameField.setText(p.username);
            uuidField.setText(p.offlineUuid);
        } finally {
            syncingIdentityUi = false;
        }

        presetCombo.setValue(p.jvmPreset);
        jvmArea.setText(p.customJvmArgs != null ? p.customJvmArgs : "");
        globalMcCheck.setSelected(p.useGlobalMinecraftFolder);
        
        if (p.servers == null) p.servers = new ArrayList<>();
        serverTable.setItems(FXCollections.observableList(p.servers));
        
        applyVersionFilter();
        if (p.offlineUuid == null || p.offlineUuid.isBlank()) syncUuidFromUsername();
        refreshHints();
    }

    private void clearFields() {
        displayNameField.clear();
        usernameField.clear();
        uuidField.clear();
        versionCombo.setItems(FXCollections.emptyObservableList());
    }

    private void handlePresetChange() {
        if (selected != null && presetCombo.getValue() != null) {
            selected.jvmPreset = presetCombo.getValue();
            refreshHints();
        }
    }

    private void createNewProfile() {
        LauncherProfile p = LauncherProfile.createDefault();
        profiles.add(p);
        profileList.setItems(FXCollections.observableList(profiles));
        profileList.getSelectionModel().select(p);
        saveProfiles();
    }

    private void saveProfiles() {
        try {
            if (selected != null && (selected.offlineUuid == null || selected.offlineUuid.isBlank())) syncUuidFromUsername();
            facade.profiles().save(profiles);
            log("Configuración guardada correctamente.");
        } catch (Exception ex) {
            log("Error al guardar: " + ex.getMessage());
        }
    }

    private javafx.concurrent.Task<Void> createInstallTask() {
        return new javafx.concurrent.Task<>() {
            @Override protected Void call() throws Exception {
                if (selected == null) return null;
                updateMessage("Instalando " + selected.lastVersionId + "...");
                facade.installVersion(selected.lastVersionId, s -> Platform.runLater(() -> log(s)));
                return null;
            }
            @Override protected void succeeded() {
                log("Instalación completada.");
                refreshHints();
            }
            @Override protected void failed() {
                log("FALLO en instalación: " + getException().getMessage());
            }
        };
    }

    private javafx.concurrent.Task<Void> createLaunchTask() {
        return new javafx.concurrent.Task<>() {
            @Override protected Void call() throws Exception {
                if (selected == null) return null;
                if (!OfflineUuid.uuidMatchesUsername(selected.username, selected.offlineUuid)) {
                    throw new RuntimeException("UUID no válido para el usuario actual.");
                }
                updateMessage("Iniciando Minecraft...");
                long ram = HardwareProbe.totalPhysicalRamMiB();
                facade.startGame(selected, ram, s -> Platform.runLater(() -> log(s)));
                return null;
            }
            @Override protected void failed() {
                log("Error de lanzamiento: " + getException().getMessage());
            }
        };
    }

    private void runTask(javafx.concurrent.Task<Void> task) {
        setUilock(true);
        task.setOnSucceeded(e -> setUilock(false));
        task.setOnFailed(e -> setUilock(false));
        task.setOnCancelled(e -> setUilock(false));
        workers.execute(task);
    }

    private void setUilock(boolean lock) {
        installBtn.setDisable(lock);
        playBtn.setDisable(lock);
        saveBtn.setDisable(lock);
        newProfileBtn.setDisable(lock);
        profileList.setDisable(lock);
    }

    private void setupVersionComboCellFactories() {
        versionCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(ManifestVersionEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.label());
                    setStyle("-fx-text-fill: #333333; -fx-background-color: white;"); // Forzar visibilidad en Linux
                }
            }
        });
        versionCombo.setButtonCell(new ListCell<>() {
             @Override protected void updateItem(ManifestVersionEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        });
    }

    // --- Métodos heredados/auxiliares originales (mantenidos o simplificados) ---

    private void loadVersionManifestAsync() {
        workers.submit(() -> {
            try {
                List<ManifestVersionEntry> list = facade.fetchManifestVersions();
                Platform.runLater(() -> {
                    allManifestEntries.clear();
                    allManifestEntries.addAll(list);
                    if (selected != null) ensureProfileVersionInBackingList(selected.lastVersionId);
                    applyVersionFilter();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> log("Error manifest: " + ex.getMessage()));
            }
        });
    }

    private void ensureProfileVersionInBackingList(String id) {
        if (id == null || id.isBlank()) return;
        if (allManifestEntries.stream().noneMatch(e -> id.equals(e.id()))) {
            allManifestEntries.add(new ManifestVersionEntry(id, "perfil"));
        }
    }

    private void applyVersionFilter() {
        if (selected != null) ensureProfileVersionInBackingList(selected.lastVersionId);
        String filter = versionFilter.getValue();
        String profileId = (selected != null && selected.lastVersionId != null) ? selected.lastVersionId : "";

        List<ManifestVersionEntry> filtered = allManifestEntries.stream()
            .filter(e -> matchesFilter(e, filter) || e.id().equals(profileId))
            .toList();

        syncingVersionUi = true;
        try {
            versionCombo.setItems(FXCollections.observableArrayList(filtered));
            versionCombo.getItems().stream()
                .filter(e -> e.id().equals(profileId)).findFirst()
                .ifPresent(versionCombo::setValue);
        } finally {
            syncingVersionUi = false;
        }
    }

    private boolean matchesFilter(ManifestVersionEntry e, String filter) {
        if (filter == null || "Todas".equals(filter)) return true;
        String t = e.type() == null ? "" : e.type();
        return switch (filter) {
            case "Solo releases" -> "release".equalsIgnoreCase(t);
            case "Solo snapshots" -> "snapshot".equalsIgnoreCase(t);
            case "Clásicas (beta/alpha)" -> t.toLowerCase().contains("alpha") || t.toLowerCase().contains("beta");
            default -> true;
        };
    }

    private void addServerToSelected() {
        if (selected == null) return;
        if (selected.servers == null) selected.servers = new ArrayList<>();
        selected.servers.add(new ServerEntry("Nuevo Servidor", "ip:puerto"));
        serverTable.setItems(FXCollections.observableList(selected.servers));
    }

    private void removeSelectedServer() {
        ServerEntry se = serverTable.getSelectionModel().getSelectedItem();
        if (selected != null && se != null) {
            selected.servers.remove(se);
            serverTable.setItems(FXCollections.observableList(selected.servers));
        }
    }

    private void syncIdentityFromDisplayName(String displayName) {
        if (selected == null) return;
        String normalized = displayName == null ? "" : displayName.trim();
        syncingIdentityUi = true;
        try {
            selected.username = normalized;
            usernameField.setText(normalized);
            syncUuidFromUsername();
        } finally {
            syncingIdentityUi = false;
        }
    }

    private void syncUuidFromUsername() {
        if (selected == null) return;
        String name = selected.username == null ? "" : selected.username.trim();
        if (name.isEmpty()) {
            uuidField.clear();
            return;
        }
        try {
            selected.offlineUuid = OfflineUuid.toString(OfflineUuid.forUsername(name));
            uuidField.setText(selected.offlineUuid);
        } catch (Exception ex) {
            // Solo loguear si el nombre tiene contenido pero es inválido tecnicamente
            if (name.length() > 0) {
                log("Aviso: " + name + " no es un nombre técnico estándar.");
            }
        }
    }

    private void refreshHints() {
        if (selected == null) return;
        long ram = HardwareProbe.totalPhysicalRamMiB();
        JvmPresetKind eff = selected.jvmPreset == JvmPresetKind.AUTO ? JvmPresetService.resolveAutoKind(ram) : selected.jvmPreset;
        modHintLabel.setText(AutoOptimizerService.modSuggestionText(eff) + " | RAM: " + ram + " MiB");
    }

    private void refreshAternosRowHint(ServerEntry se) {
        log(se.crackedServer ? "Modo Cracked: Acceso offline permitido." : "Modo Premium: Requiere cuenta Microsoft.");
    }

    private void log(String s) {
        Platform.runLater(() -> {
            logArea.appendText(s + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    @Override
    public void stop() { workers.shutdownNow(); }
}

package com.experimento.launcher;

import com.experimento.launcher.model.*;
import com.experimento.launcher.mojang.*;
import com.experimento.launcher.paths.*;
import com.experimento.launcher.service.*;
import com.experimento.launcher.store.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import com.experimento.launcher.modloaders.ModloaderInstallerService;
import com.experimento.launcher.util.OfflineUuid;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private final Map<String, Process> activeProcesses = new HashMap<>();
    private final Map<String, BooleanProperty> runningState = new HashMap<>();
    
    private StackPane contentStack;
    private final Map<String, Node> views = new HashMap<>();

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
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Stage stage;
    private String currentViewTitle = "General";
    private StackPane deleteConfirmOverlay;
    private Label deleteConfirmMsg;
    private StackPane modloaderOverlay;
    private Label modloaderMcLabel;
    private StackPane updateOverlay;
    private ProgressBar updateProgress;
    private Label updateStatus;
    private Button updateBtn;
    private final Map<String, Button> navButtons = new HashMap<>();
    private StackPane javaDownloadOverlay;
    private ProgressBar javaProgress;
    private Label javaStatus;
    private int detectedJavaVersion = 8;

    // Nuevo Header Dinámico
    private Label headerProfileName;
    private Label headerProfileVersion;

    // Botones (para deshabilitar durante procesos)
    private Button installBtn;
    private Button playBtn;
    private Button saveBtn;
    private Button newProfileBtn;
    private Button deleteProfileBtn;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Stage splashStage = new Stage(javafx.stage.StageStyle.UNDECORATED);
        StackPane splashRoot = new StackPane();
        splashRoot.setStyle("-fx-background-color: linear-gradient(to right, #1177BB, #0E639C); -fx-border-color: #0c507c; -fx-border-width: 4px;");
        
        Label title = new Label("MEACORE\nTHE LAUNCHER FOR MINECRAFT");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: bold; -fx-alignment: center; -fx-text-alignment: center;");
        
        Label version = new Label(LauncherMetadata.VERSION);
        version.setStyle("-fx-text-fill: white; -fx-background-color: rgba(0,0,0,0.2); -fx-padding: 3 8;");
        StackPane.setAlignment(version, Pos.TOP_RIGHT);
        
        splashRoot.getChildren().addAll(title, version);
        Scene splashScene = new Scene(splashRoot, 400, 250);
        splashStage.setScene(splashScene);
        splashStage.show();

        new Thread(() -> {
            try {
                initData();
                Platform.runLater(() -> {
                    try {
                        initLayout(stage);
                        splashStage.close();
                        stage.show();
                        loadVersionManifestAsync();
                        com.experimento.launcher.service.AutoUpdateService.checkForUpdatesAsync();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void initLayout(Stage stage) throws Exception {
        this.stage = stage;
        
        VBox sidebarArea = new VBox();
        sidebarArea.setPrefWidth(260);
        sidebarArea.setStyle("-fx-background-color: #252526;"); // Carbón Premium
        
        Label brandLabel = new Label("🎮 MeaCore Launcher");
        brandLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 20 10;");
        
        sidebarArea.getChildren().addAll(brandLabel, createProfileSidebar(), new Separator(), createNavigationMenu());

        contentStack = new StackPane();
        contentStack.setPadding(new Insets(20));
        
        initializeViews();
        setupFieldListeners();
        
        headerProfileName = new Label("Cargando...");
        headerProfileName.setStyle("-fx-text-fill: white; -fx-font-size: 32px; -fx-font-weight: bold; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 5, 0, 0, 2);");
        headerProfileVersion = new Label("");
        headerProfileVersion.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 16px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 3, 0, 0, 1);");

        VBox headerTextInfo = new VBox(5, headerProfileName, headerProfileVersion);
        headerTextInfo.setAlignment(Pos.CENTER_LEFT);
        headerTextInfo.setPadding(new Insets(0, 0, 0, 30));

        StackPane topHeader = new StackPane(headerTextInfo);
        topHeader.setPrefHeight(120);
        topHeader.setMinHeight(120);
        topHeader.setMaxHeight(120);
        topHeader.setStyle("-fx-background-color: linear-gradient(to right, #111111, #094771);");

        VBox rightArea = new VBox();
        rightArea.getChildren().addAll(topHeader, contentStack, createPersistentFooter());
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        HBox mainLayout = new HBox(sidebarArea, rightArea);
        HBox.setHgrow(rightArea, Priority.ALWAYS);

        // Overlays internos (sin ventanas secundarias del OS)
        deleteConfirmOverlay = buildDeleteOverlay();
        deleteConfirmOverlay.setVisible(false);
        modloaderOverlay = buildModloaderOverlay();
        modloaderOverlay.setVisible(false);
        updateOverlay = buildUpdateOverlay();
        updateOverlay.setVisible(false);
        javaDownloadOverlay = buildJavaDownloadOverlay();
        javaDownloadOverlay.setVisible(false);
        StackPane rootPane = new StackPane(mainLayout, deleteConfirmOverlay, modloaderOverlay, updateOverlay, javaDownloadOverlay);

        Scene scene = new Scene(rootPane, 1080, 720);
        stage.setMinWidth(1080);
        stage.setMinHeight(720);
        
        // Listener de actualización
        AutoUpdateService.setListener(new AutoUpdateService.UpdateListener() {
            @Override
            public void onUpdateFound(String version, String url) {
                Platform.runLater(() -> {
                    updateStatus.setText("MeaCore Launcher v" + version + " disponible.");
                    updateBtn.setText("🚀 Actualizar Ahora");
                    updateBtn.setDisable(false);
                    updateBtn.setOnAction(e -> {
                        updateBtn.setDisable(true);
                        updateBtn.setText("Descargando...");
                        AutoUpdateService.downloadAndInstallAsync(url);
                    });
                    updateOverlay.setVisible(true);
                });
            }

            @Override
            public void onDownloadProgress(double fraction) {
                Platform.runLater(() -> {
                    updateProgress.setProgress(fraction);
                    updateStatus.setText("Descargando actualización: " + (int)(fraction * 100) + "%");
                });
            }

            @Override
            public void onDownloadComplete(Path debPath) {
                Platform.runLater(() -> {
                    updateStatus.setText("¡Descarga lista! Por favor, autoriza la instalación en la ventana de sistema que aparecerá.");
                    updateProgress.setProgress(1.0);
                });
            }

            @Override
            public void onDownloadError(String message) {
                Platform.runLater(() -> {
                    updateOverlay.setVisible(false);
                    new Alert(Alert.AlertType.ERROR, "Error al actualizar: " + message).show();
                });
            }
        });

        try {
            java.net.URL cssUrl = LauncherApp.class.getResource("/com/experimento/launcher/ui/meacore.css");
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        } catch (Exception ignored) {}
        
        stage.setTitle(LauncherMetadata.DISPLAY_NAME);
        stage.getProperties().put("glass.gtk.wm_class", "meacorelauncher");
        try {
            for (String s : new String[]{"/icon-256.png", "/icon-128.png", "/icon.png"}) {
                var stream = LauncherApp.class.getResourceAsStream(s);
                if (stream != null) stage.getIcons().add(new javafx.scene.image.Image(stream));
            }
        } catch (Exception ignored) {}
        
        stage.setScene(scene);
        stage.centerOnScreen();
        
        // Inicialización post-UI
        showView("General");
        profileList.getSelectionModel().selectFirst();

        stage.setOnCloseRequest(ev -> {
            workers.shutdownNow();
            Platform.exit();
            System.exit(0);
        });
    }

    private void initData() throws Exception {
        LauncherDirectories dirs = LauncherDirectories.fromDefault();
        dirs.ensureBaseDirs();
        facade = new LauncherFacade(dirs);
        profiles = new ArrayList<>(facade.profiles().loadOrCreateDefault());
        for (LauncherProfile p : profiles) {
            LauncherFacade.maybeImportTlauncherJvm(p);
            runningState.put(p.id, new SimpleBooleanProperty(false));
        }
        facade.profiles().save(profiles);
        
        // Recopilación de datos (Telemetría Inicial)
        SystemInfoService.collectTelemetry(dirs.launcherData().resolve("telemetry.log"));
    }

    private VBox createProfileSidebar() {
        profileList = new ListView<>(FXCollections.observableList(profiles));
        profileList.setPrefWidth(240);
        profileList.setStyle("-fx-background-color: #252526; -fx-background: #252526; -fx-control-inner-background: #252526; -fx-border-color: transparent;");
        profileList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(LauncherProfile item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(isSelected()
                    ? "-fx-background-color: #37373D;"
                    : "-fx-background-color: #252526;");
                if (empty || item == null) {
                    setGraphic(null); setText(null);
                } else {
                    HBox box = new HBox(12);
                    box.setAlignment(Pos.CENTER_LEFT);
                    box.setPadding(new Insets(5, 5, 5, 5));
                    
                    Circle indicator = new Circle(4);
                    indicator.setFill(Color.TRANSPARENT);
                    BooleanProperty running = runningState.get(item.id);
                    if (running != null) {
                        indicator.fillProperty().bind(javafx.beans.binding.Bindings.when(running)
                            .then(Color.LIMEGREEN)
                            .otherwise(Color.TRANSPARENT));
                    }
                    
                    Label name = new Label(item.displayName + " (" + (item.lastVersionId != null && !item.lastVersionId.isBlank() ? item.lastVersionId : "Sin versión") + ")");
                    name.setStyle("-fx-text-fill: inherit;");
                    
                    box.getChildren().addAll(indicator, name);
                    setGraphic(box);
                }
            }
            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                setStyle(selected ? "-fx-background-color: #37373D;" : "-fx-background-color: #252526;");
            }
        });
        profileList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> bindProfile(n));

        Label perfilesLabel = new Label("  Perfiles");
        perfilesLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 10 0 4 0;");
        
        VBox sidebar = new VBox(0, perfilesLabel, profileList);
        VBox.setVgrow(profileList, Priority.ALWAYS);
        return sidebar;
    }
    private VBox createNavigationMenu() {
        VBox menu = new VBox(10);
        menu.setPadding(new Insets(20, 10, 20, 10));
        menu.setAlignment(Pos.TOP_LEFT);

        menu.getChildren().addAll(
            createNavButton("🏠 General", "General"),
            createNavButton("🛠 Modding", "Modding"),
            createNavButton("🏪 MCMOD", "Store"),
            createNavButton("⚙ Config. Java", "Java"),
            createNavButton("🌐 Servidores", "Servers"),
            createNavButton("📜 Consola", "Log")
        );

        return menu;
    }

    private Button createNavButton(String text, String viewId) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setId("nav-" + viewId); // Para identificarlo fácilmente
        
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; -fx-font-size: 14px; -fx-padding: 10 15; -fx-cursor: hand; -fx-background-radius: 5;");
        
        btn.setOnMouseEntered(e -> {
            if (!viewId.equals(currentViewId)) {
                btn.setStyle("-fx-background-color: #333333; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10 15; -fx-cursor: hand; -fx-background-radius: 5;");
            }
        });
        
        btn.setOnMouseExited(e -> updateNavButtonStyle(btn, viewId));

        btn.setOnAction(e -> showView(viewId));
        navButtons.put(viewId, btn);
        return btn;
    }

    private String currentViewId = "General";

    private void updateNavButtonStyle(Button btn, String viewId) {
        if (viewId.equals(currentViewId)) {
            btn.setStyle("-fx-background-color: #3d3d3d; -fx-text-fill: #0E639C; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10 15; -fx-background-radius: 5;");
        } else {
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; -fx-font-size: 14px; -fx-padding: 10 15; -fx-background-radius: 5;");
        }
    }

    private void showView(String viewId) {
        Node view = views.get(viewId);
        if (view != null) {
            contentStack.getChildren().setAll(view);
            String oldViewId = currentViewId;
            currentViewId = viewId;
            currentViewTitle = viewId;
            updateHeaderTitle();
            
            // Actualización instantánea de botones sin recorrer el árbol
            Button oldBtn = navButtons.get(oldViewId);
            if (oldBtn != null) updateNavButtonStyle(oldBtn, oldViewId);
            
            Button newBtn = navButtons.get(viewId);
            if (newBtn != null) updateNavButtonStyle(newBtn, viewId);
        }
    }

    private void initializeViews() {
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(20);

        jvmArea = new TextArea();
        jvmArea.setPrefRowCount(3);
        
        globalMcCheck = new CheckBox("Usar ~/.minecraft global (avanzado)");
        presetCombo = new ComboBox<>(FXCollections.observableArrayList(JvmPresetKind.values()));
        presetCombo.setOnAction(e -> handlePresetChange());

        views.put("General", createGeneralView());
        views.put("Modding", createModdingView());
        views.put("Store", createStoreView());
        views.put("Java", createJavaView());
        views.put("Servers", createServersView());
        views.put("Log", createLogAreaView());
    }

    private Node createGeneralView() {
        VBox identityCard = new VBox(15, new Label("Ajustes de Identidad"), createIdentitySection());
        identityCard.getStyleClass().add("mc-card");

        VBox versionCard = new VBox(15, new Label("Versión del Juego"), createVersionSection());
        versionCard.getStyleClass().add("mc-card");

        VBox content = new VBox(20, identityCard, versionCard);
        content.setPadding(new Insets(10, 15, 10, 0)); // Evitar que el scrollbar tape cards
        
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        return scroll;
    }

    private VBox createModdingView() {
        Button installModloaderBtn = new Button("✨ Inyectar Forge / Fabric");
        installModloaderBtn.setStyle("-fx-font-size: 16px; -fx-padding: 10 20;");
        installModloaderBtn.setOnAction(e -> handleInstallModloader());
        
        VBox modding = new VBox(20, 
            new Label("Gestión de Modloaders"),
            new Label("Desde aquí puedes inyectar automáticamente Forge o Fabric en tu versión de Minecraft."),
            installModloaderBtn
        );
        modding.setAlignment(Pos.TOP_CENTER);
        return modding;
    }

    private VBox createStoreView() {
        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_LEFT);

        ComboBox<StoreCategory> catCombo = new ComboBox<>(FXCollections.observableArrayList(StoreCategory.values()));
        catCombo.setValue(StoreCategory.MODPACK);

        TextField searchField = new TextField();
        searchField.setPromptText("Buscar...");
        searchField.setPrefWidth(300);

        Button searchBtn = new Button("🔍 Buscar");

        topBar.getChildren().addAll(catCombo, searchField, searchBtn);

        ListView<StoreItem> storeList = new ListView<>();
        storeList.setPrefHeight(400);
        storeList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(StoreItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); }
                else {
                    HBox box = new HBox(15);
                    box.setAlignment(Pos.CENTER_LEFT);
                    box.setPadding(new Insets(10));

                    StackPane iconBox = new StackPane();
                    iconBox.setPrefSize(64, 64);
                    iconBox.setMinSize(64, 64);
                    iconBox.setStyle("-fx-background-color: #2a2a2a; -fx-background-radius: 8px;");

                    Label fallbackIcon = new Label("📦");
                    fallbackIcon.setStyle("-fx-font-size: 32px;");
                    iconBox.getChildren().add(fallbackIcon);

                    if (item.thumbnailUrl() != null && !item.thumbnailUrl().isBlank()) {
                        javafx.scene.image.ImageView imgView = new javafx.scene.image.ImageView();
                        imgView.setFitWidth(64); 
                        imgView.setFitHeight(64);
                        javafx.scene.image.Image img = new javafx.scene.image.Image(item.thumbnailUrl(), true);
                        imgView.setImage(img);
                        iconBox.getChildren().add(imgView);
                        
                        img.errorProperty().addListener((obs, o, isError) -> {
                            if (isError) {
                                Platform.runLater(() -> iconBox.getChildren().remove(imgView));
                            }
                        });
                    }

                    VBox info = new VBox(5);
                    Label title = new Label(item.title());
                    title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: white;");
                    Label author = new Label("Por " + item.author());
                    author.setStyle("-fx-text-fill: #0E639C;");
                    Label desc = new Label(item.description());
                    desc.setWrapText(true); desc.setMaxWidth(400); desc.setStyle("-fx-text-fill: #cccccc;");
                    Label stats = new Label("Descargas: " + item.downloads() + " | Versión: " + item.latestVersion());
                    stats.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");
                    info.getChildren().addAll(title, author, desc, stats);

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Button btnInstall = new Button("✨ Instalar");
                    btnInstall.setStyle("-fx-background-color: #0E639C; -fx-text-fill: white; -fx-font-weight: bold;");
                    btnInstall.setOnAction(ev -> {
                        if (selected == null) {
                            new Alert(Alert.AlertType.WARNING, "Selecciona un perfil en la barra lateral primero.").show();
                            return;
                        }
                        btnInstall.setDisable(true);
                        btnInstall.setText("Instalando...");
                        workers.submit(() -> {
                            try {
                                ModpackDependencies deps = StoreDownloader.install(item, facade.gameDirFor(selected), selected.lastVersionId, msg -> Platform.runLater(() -> log("[STORE] " + msg)));
                                if (deps != null) {
                                    Platform.runLater(() -> log("[STORE] Modpack requiere " + deps.mcVersion() + " con " + deps.loader() + ". Configurando automáticamente..."));
                                    autoConfigureModpack(deps);
                                }
                                Platform.runLater(() -> btnInstall.setText("✅ Listo"));
                            } catch (Exception ex) {
                                Platform.runLater(() -> {
                                    btnInstall.setDisable(false);
                                    btnInstall.setText("✨ Instalar");
                                    log("[STORE] Error: " + ex.getMessage());
                                });
                            }
                        });
                    });

                    box.getChildren().addAll(iconBox, info, spacer, btnInstall);
                    setGraphic(box);
                }
            }
        });
        
        VBox.setVgrow(storeList, Priority.ALWAYS);

        Button loadMoreBtn = new Button("Cargar Más...");
        loadMoreBtn.setMaxWidth(Double.MAX_VALUE);
        
        final int[] offset = {0};

        Runnable performSearch = () -> {
            storeList.getItems().clear();
            offset[0] = 0;
            workers.submit(() -> {
                var results = ModrinthStoreClient.search(searchField.getText(), catCombo.getValue(), 0);
                Platform.runLater(() -> storeList.getItems().addAll(results));
            });
        };

        searchBtn.setOnAction(e -> performSearch.run());
        searchField.setOnAction(e -> performSearch.run());
        catCombo.setOnAction(e -> performSearch.run());

        loadMoreBtn.setOnAction(e -> {
            offset[0] += 20;
            workers.submit(() -> {
                var res = ModrinthStoreClient.search(searchField.getText(), catCombo.getValue(), offset[0]);
                Platform.runLater(() -> storeList.getItems().addAll(res));
            });
        });

        // Trigger initial load
        Platform.runLater(performSearch);

        return new VBox(15, topBar, storeList, loadMoreBtn);
    }

    private VBox createJavaView() {
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(15);
        int r = 0;
        grid.add(new Label("Optimización RAM:"), 0, r); grid.add(presetCombo, 1, r++);
        grid.add(new Label("Argumentos JVM:"), 0, r); grid.add(jvmArea, 1, r++);
        grid.add(globalMcCheck, 1, r++);
        
        return new VBox(15, new Label("Motor de Ejecución Java"), grid, new Separator(), createHintSection());
    }

    private VBox createServersView() {
        return new VBox(10, new Label("Lista de Servidores Multijugador"), createServerTableSection());
    }

    private VBox createLogAreaView() {
        return new VBox(10, new Label("Consola de Diagnóstico en Vivo"), logArea);
    }

    private HBox createPersistentFooter() {
        saveBtn = new Button("💾 Guardar");
        saveBtn.setStyle("-fx-background-color: transparent; -fx-border-color: white; -fx-border-radius: 4px; -fx-text-fill: white;");
        saveBtn.setOnAction(e -> saveProfiles());

        installBtn = new Button("⬇ Instalar");
        installBtn.setStyle("-fx-background-color: #0E639C; -fx-text-fill: white;");
        installBtn.setOnAction(e -> runTask(createInstallTask()));

        playBtn = new Button("▶ ¡JUGAR!");
        playBtn.setDefaultButton(true);
        playBtn.getStyleClass().add("btn-play");
        playBtn.setOnAction(e -> handlePlayClick());

        newProfileBtn = new Button("➕ Nuevo");
        newProfileBtn.setOnAction(e -> createNewProfile());

        deleteProfileBtn = new Button("🗑 Borrar");
        deleteProfileBtn.setStyle("-fx-text-fill: #d32f2f;");
        deleteProfileBtn.setOnAction(e -> deleteSelectedProfile());

        HBox footer = new HBox(15, newProfileBtn, deleteProfileBtn, new Region(), saveBtn, installBtn, playBtn);
        HBox.setHgrow(footer.getChildren().get(2), Priority.ALWAYS);
        footer.setPadding(new Insets(15, 25, 15, 25));
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-background-color: #2d2d2d; -fx-border-color: #3f3f46; -fx-border-width: 1 0 0 0;");
        return footer;
    }

    private GridPane createIdentitySection() {
        displayNameField = new TextField();
        usernameField = new TextField();
        uuidField = new TextField();
        uuidField.setEditable(false);
        Button syncUuidBtn = new Button("🔄 Sync");
        syncUuidBtn.setOnAction(e -> syncUuidFromUsername());

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.add(new Label("Nombre Perfil:"), 0, 0); grid.add(displayNameField, 1, 0);
        grid.add(new Label("Usuario (Offline):"), 0, 1); grid.add(usernameField, 1, 1);
        grid.add(new Label("UUID:"), 0, 2); grid.add(new HBox(8, uuidField, syncUuidBtn), 1, 2);
        return grid;
    }

    private VBox createVersionSection() {
        versionFilter = new ComboBox<>(FXCollections.observableArrayList("Todas", "Solo releases", "Solo snapshots", "Clásicas (beta/alpha)"));
        versionFilter.setValue("Todas");
        versionFilter.setOnAction(e -> applyVersionFilter());

        versionCombo = new ComboBox<>();
        versionCombo.setMaxWidth(Double.MAX_VALUE);
        setupVersionComboCellFactories();

        Button refreshManifestBtn = new Button("Actualizar");
        refreshManifestBtn.setOnAction(e -> loadVersionManifestAsync());

        return new VBox(10, new HBox(10, versionFilter, versionCombo, refreshManifestBtn));
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
                // Validación: Solo cambiar si realmente es una selección manual del usuario
                if (!n.id().equals(selected.lastVersionId)) {
                    selected.lastVersionId = n.id();
                    headerProfileVersion.setText(n.id());
                    profileList.refresh();
                }
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
        colName.setPrefWidth(180);

        TableColumn<ServerEntry, String> colAddr = new TableColumn<>("IP:Puerto");
        colAddr.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().address));
        colAddr.setCellFactory(TextFieldTableCell.forTableColumn());
        colAddr.setOnEditCommit(ev -> { if(ev.getRowValue() != null) ev.getRowValue().address = ev.getNewValue(); });
        colAddr.setPrefWidth(220);

        TableColumn<ServerEntry, Boolean> colCracked = new TableColumn<>("Skins/Aternos");
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
        serverTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        serverTable.setPlaceholder(new Label("⚠️ Pulsa 'Añadir' para sincronizar servidores con Minecraft"));
        
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



    // --- Lógica de Negocio y Helpers ---

    private void updateHeaderTitle() {
        if (selected == null) {
            headerProfileName.setText("Ningún perfil");
            headerProfileVersion.setText("");
            return;
        }
        String name = selected.displayName != null && !selected.displayName.isBlank() ? selected.displayName : (selected.username != null ? selected.username : "Perfil Nuevo");
        headerProfileName.setText(name);
        headerProfileVersion.setText(selected.lastVersionId != null ? selected.lastVersionId : "");
    }

    private void bindProfile(LauncherProfile p) {
        selected = p;
        deleteProfileBtn.setDisable(p == null);
        if (p == null) {
            updateHeaderTitle();
            clearFields();
            return;
        }

        updateHeaderTitle();

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
        
        syncingVersionUi = true;
        try {
            applyVersionFilter();
            // Restaurar selección exacta del perfil
            if (p.lastVersionId != null) {
                for (ManifestVersionEntry mve : versionCombo.getItems()) {
                    if (mve.id().equals(p.lastVersionId)) {
                        versionCombo.getSelectionModel().select(mve);
                        break;
                    }
                }
            }
        } finally {
            syncingVersionUi = false;
        }
        
        if (p.offlineUuid == null || p.offlineUuid.isBlank()) syncUuidFromUsername();
        refreshHints();
        refreshStatusCard();
    }

    private void refreshStatusCard() {
        // Redundante, el cuadro fue eliminado pero mantendremos el hook dummy si algun proceso lo llama
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
        runningState.put(p.id, new SimpleBooleanProperty(false));
        profileList.setItems(FXCollections.observableList(profiles));
        profileList.getSelectionModel().select(p);
        saveProfiles();
    }

    /** Muestra el overlay de confirmación sin abrir ninguna ventana secundaria. */
    private void deleteSelectedProfile() {
        if (selected == null) return;
        deleteConfirmMsg.setText("¿Estás seguro de eliminar el perfil '" + selected.displayName + "'?");
        deleteConfirmOverlay.setVisible(true);
    }

    /** Construye el overlay interno de confirmación (reemplaza Alert nativo). */
    private StackPane buildDeleteOverlay() {
        StackPane dim = new StackPane();
        dim.setStyle("-fx-background-color: rgba(0,0,0,0.55);");

        VBox card = new VBox(16);
        card.setMaxWidth(460);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        card.setStyle("-fx-background-color: #252526; -fx-background-radius: 10; "
                + "-fx-border-radius: 10; -fx-border-color: #454545; -fx-border-width: 1; "
                + "-fx-padding: 28; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 24, 0, 0, 6);");

        deleteConfirmMsg = new Label();
        deleteConfirmMsg.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold;");
        deleteConfirmMsg.setWrapText(true);

        Label warning = new Label("⚠️ ADVERTENCIA: Los mundos, mods y configuraciones se borrarán PERMANENTEMENTE del disco.");
        warning.setWrapText(true);
        warning.setStyle("-fx-text-fill: #f0a0a0; -fx-font-size: 12px;");

        Button acceptBtn = new Button("🗑 Eliminar Permanentemente");
        acceptBtn.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 9 20; -fx-background-radius: 5;");
        acceptBtn.setOnAction(e -> {
            deleteConfirmOverlay.setVisible(false);
            executeDeleteCurrentProfile();
        });

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.setStyle("-fx-background-color: transparent; -fx-border-color: #666666; -fx-border-radius: 5; -fx-text-fill: #cccccc; -fx-padding: 9 20;");
        cancelBtn.setOnAction(e -> deleteConfirmOverlay.setVisible(false));

        HBox buttons = new HBox(12, cancelBtn, acceptBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(deleteConfirmMsg, warning, new Separator(), buttons);
        dim.getChildren().add(card);
        return dim;
    }

    /** Lógica real de borrado, llamada tras confirmar en el overlay. */
    private void executeDeleteCurrentProfile() {
        if (selected == null) return;
        final LauncherProfile toDelete = selected;

        profiles.remove(toDelete);
        selected = null;
        deleteProfileBtn.setDisable(true);
        profileList.getSelectionModel().clearSelection();
        profileList.setItems(FXCollections.observableArrayList(profiles));
        bindProfile(null);
        headerProfileName.setText("Ningún perfil");

        workers.submit(() -> {
            try {
                facade.profiles().save(profiles);
                if (!toDelete.useGlobalMinecraftFolder) {
                    java.nio.file.Path dir = facade.gameDirFor(toDelete);
                    if (java.nio.file.Files.exists(dir)) {
                        try (var stream = java.nio.file.Files.walk(dir)) {
                            stream.sorted(java.util.Comparator.reverseOrder())
                                  .map(java.nio.file.Path::toFile)
                                  .forEach(java.io.File::delete);
                        }
                    }
                }
                Platform.runLater(() -> {
                    log("Perfil '" + toDelete.displayName + "' eliminado permanentemente.");
                    deleteProfileBtn.setDisable(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    log("Error al borrar perfil: " + ex.getMessage());
                    deleteProfileBtn.setDisable(false);
                });
            }
        });
    }

    /** Muestra el overlay de selección de loader sin abrir ventanas secundarias. */
    private void handleInstallModloader() {
        if (selected == null || selected.lastVersionId == null || selected.lastVersionId.isBlank()) {
            log("[ERROR] Selecciona primero una versión Vanilla desde el desplegable 'Versión Juego' antes de instalar Forge/Fabric.");
            return;
        }
        modloaderMcLabel.setText("Inyección de Modloader para Minecraft " + selected.lastVersionId);
        modloaderOverlay.setVisible(true);
    }

    /** Construye el overlay de selección de Forge/Fabric. */
    private StackPane buildModloaderOverlay() {
        StackPane dim = new StackPane();
        dim.setStyle("-fx-background-color: rgba(0,0,0,0.55);");

        VBox card = new VBox(16);
        card.setMaxWidth(460);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        card.setStyle("-fx-background-color: #252526; -fx-background-radius: 10; "
                + "-fx-border-radius: 10; -fx-border-color: #454545; -fx-border-width: 1; "
                + "-fx-padding: 28; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 24, 0, 0, 6);");

        Label titleLbl = new Label("✨ Instalar Modloader Automático");
        titleLbl.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");

        modloaderMcLabel = new Label();
        modloaderMcLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 13px;");

        Label hint = new Label("Elige el motor de mods a instalar. Se descargará automáticamente:");
        hint.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");
        hint.setWrapText(true);

        Button forgeBtn = new Button("⚙ Forge");
        forgeBtn.setPrefWidth(180);
        forgeBtn.setStyle("-fx-background-color: #b07833; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 5;");
        forgeBtn.setOnAction(e -> {
            modloaderOverlay.setVisible(false);
            executeInstallModloader("Forge");
        });

        Button fabricBtn = new Button("🪡 Fabric");
        fabricBtn.setPrefWidth(180);
        fabricBtn.setStyle("-fx-background-color: #4a7c40; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 5;");
        fabricBtn.setOnAction(e -> {
            modloaderOverlay.setVisible(false);
            executeInstallModloader("Fabric");
        });

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.setStyle("-fx-background-color: transparent; -fx-border-color: #666666; -fx-border-radius: 5; -fx-text-fill: #cccccc; -fx-padding: 8 16;");
        cancelBtn.setOnAction(e -> modloaderOverlay.setVisible(false));

        HBox loaderBtns = new HBox(12, forgeBtn, fabricBtn);
        loaderBtns.setAlignment(Pos.CENTER);
        HBox cancelRow = new HBox(cancelBtn);
        cancelRow.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(titleLbl, modloaderMcLabel, hint, new Separator(), loaderBtns, cancelRow);
        dim.getChildren().add(card);
        return dim;
    }

    /** Lógica real de instalación del modloader tras seleccionar en el overlay. */
    private void executeInstallModloader(String choice) {
        if (selected == null) return;
        String mcVersion = selected.lastVersionId;
        Path baseDir = facade.directories().launcherData();
        log("Iniciando instalación automática de " + choice + " para " + mcVersion + "...");
        workers.submit(() -> {
            try {
                if ("Forge".equals(choice)) {
                    ModloaderInstallerService.installForge(mcVersion, baseDir, msg -> Platform.runLater(() -> log(msg)));
                } else if ("Fabric".equals(choice)) {
                    ModloaderInstallerService.installFabric(mcVersion, baseDir, msg -> Platform.runLater(() -> log(msg)));
                }
                Platform.runLater(() -> {
                    log("¡Terminado! Actualizando lista de versiones...");
                    loadVersionManifestAsync();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> log("[CRITICAL] Falló la instalación: " + ex.getMessage()));
            }
        });
    }

    /**
     * Configura automáticamente las dependencias de un modpack recién instalado.
     */
    private void autoConfigureModpack(ModpackDependencies deps) {
        if (deps == null || deps.mcVersion() == null) return;
        
        workers.submit(() -> {
            try {
                String mcVer = deps.mcVersion();
                String loader = deps.loader();

                // 1. Instalar base vanilla si no existe
                Platform.runLater(() -> log("[STORE] Instalando Minecraft " + mcVer + "..."));
                facade.installVersion(mcVer, s -> Platform.runLater(() -> log("[AUTO] " + s)));

                // 2. Instalar loader si se requiere
                if (loader != null && !loader.equalsIgnoreCase("vanilla")) {
                    Platform.runLater(() -> log("[STORE] Inyectando " + loader + "..."));
                    Path baseDir = facade.directories().launcherData();
                    
                    if (loader.equalsIgnoreCase("forge")) {
                        ModloaderInstallerService.installForge(mcVer, baseDir, s -> Platform.runLater(() -> log("[AUTO-FORGE] " + s)));
                        // Intentar adivinar el nombre del perfil forge (suele ser forge-1.12.2-...)
                        // Como es asíncrono y complejo, recargamos y buscamos
                    } else if (loader.equalsIgnoreCase("fabric")) {
                        ModloaderInstallerService.installFabric(mcVer, baseDir, s -> Platform.runLater(() -> log("[AUTO-FABRIC] " + s)));
                    }

                    // Recargar manifiesto para encontrar el nuevo ID
                    Platform.runLater(() -> {
                        log("[STORE] Actualizando lista de versiones...");
                        loadVersionManifestAsync();
                        
                        // Esperar un poco a que el manifiesto cargue y buscar la mejor coincidencia
                        workers.submit(() -> {
                            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                            Platform.runLater(() -> finalizeModpackSetup(mcVer, loader));
                        });
                    });
                } else {
                    // Solo vanilla
                    Platform.runLater(() -> finalizeModpackSetup(mcVer, null));
                }

            } catch (Exception ex) {
                Platform.runLater(() -> log("[STORE] Error en auto-config: " + ex.getMessage()));
            }
        });
    }

    private void finalizeModpackSetup(String mcVer, String loader) {
        if (selected == null) return;
        
        // Buscar en la lista de versiones filtradas la que mejor coincida
        String match = mcVer;
        if (loader != null) {
            String target = loader.toLowerCase();
            for (ManifestVersionEntry v : versionCombo.getItems()) {
                if (v.id().toLowerCase().contains(target) && v.id().contains(mcVer)) {
                    match = v.id();
                    break;
                }
            }
        }
        
        selected.lastVersionId = match;
        // Seleccionar en el combo si está presente
        for (ManifestVersionEntry v : versionCombo.getItems()) {
            if (v.id().equals(match)) {
                versionCombo.getSelectionModel().select(v);
                break;
            }
        }
        saveProfiles();
        applyVersionFilter(); // Refresca UI
        log("[STORE] ✅ Perfil auto-configurado para: " + match);
        
        // Si estamos en la vista General, forzar refresco
        if ("General".equals(currentViewTitle)) showView("General");
    }

    /** Construye el overlay de actualización. */
    private StackPane buildUpdateOverlay() {
        StackPane dim = new StackPane();
        dim.setStyle("-fx-background-color: rgba(0,0,0,0.55);");

        VBox card = new VBox(16);
        card.setMaxWidth(460);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        card.setStyle("-fx-background-color: #252526; -fx-background-radius: 10; "
                + "-fx-border-radius: 10; -fx-border-color: #0E639C; -fx-border-width: 2; "
                + "-fx-padding: 28; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 24, 0, 0, 8);");

        Label title = new Label("✨ ¡Actualización Disponible!");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");

        updateStatus = new Label("Buscando actualizaciones...");
        updateStatus.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 13px;");
        updateStatus.setWrapText(true);

        updateProgress = new ProgressBar(0);
        updateProgress.setMaxWidth(Double.MAX_VALUE);
        updateProgress.setStyle("-fx-accent: #0E639C;");

        Label note = new Label("Se te pedirá tu contraseña para instalar el paquete .deb nativamente.");
        note.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        note.setWrapText(true);

        updateBtn = new Button("🚀 Actualizar");
        updateBtn.setMaxWidth(Double.MAX_VALUE);
        updateBtn.setStyle("-fx-background-color: #0E639C; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 5;");

        Button laterBtn = new Button("Más tarde");
        laterBtn.setStyle("-fx-background-color: transparent; -fx-border-color: #666666; -fx-border-radius: 5; -fx-text-fill: #cccccc; -fx-padding: 8 16;");
        laterBtn.setOnAction(e -> updateOverlay.setVisible(false));

        HBox bottom = new HBox(laterBtn);
        bottom.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(title, updateStatus, updateProgress, note, new Separator(), updateBtn, bottom);
        dim.getChildren().add(card);
        return dim;
    }

    private void handlePlayClick() {
        if (selected == null) return;
        
        // Desactivar UI mientras validamos
        playBtn.setDisable(true);
        playBtn.setText("Validando...");
        
        workers.submit(() -> {
            try {
                // 1. Cargar el version.json para ver si requiere Java 8
                Path jsonPath = facade.directories().versionsDir().resolve(selected.lastVersionId).resolve("version.json");
                if (!java.nio.file.Files.exists(jsonPath)) {
                    // Si no existe, tenemos que instalar la versión primero
                    Platform.runLater(() -> {
                        playBtn.setDisable(false);
                        playBtn.setText("▶ ¡JUGAR!");
                        runTask(createLaunchTask());
                    });
                    return;
                }
                
                JsonNode merged = new ObjectMapper().readTree(java.nio.file.Files.readAllBytes(jsonPath));
                int requiredJava = 0;
                if (merged.has("javaVersion")) {
                    requiredJava = merged.get("javaVersion").path("majorVersion").asInt(0);
                } else {
                    String main = merged.path("mainClass").asText("").toLowerCase();
                    if (main.contains("launchwrapper") || main.contains("fml") || main.contains("forge") || selected.lastVersionId.toLowerCase().contains("1.12.2")) {
                        requiredJava = 8;
                    }
                }

                // Forzar Java 17 para versiones entre 1.17 y 1.20.4 si no se detectó (por seguridad para mods)
                if (requiredJava == 0 || requiredJava > 17) {
                    String vid = selected.lastVersionId;
                    if (vid.contains("1.17") || vid.contains("1.18") || vid.contains("1.19") || vid.contains("1.20.1") || vid.contains("1.20.2") || vid.contains("1.20.4")) {
                        requiredJava = 17;
                    }
                }

                if (requiredJava == 8 || requiredJava == 17) {
                    final int ver = requiredJava;
                    if (facade.runtime().getExecutable(ver) == null) {
                        Platform.runLater(() -> {
                            detectedJavaVersion = ver;
                            javaStatus.setText("Esta versión requiere Java " + ver + " para funcionar correctamente.");
                            javaProgress.setProgress(0);
                            javaDownloadOverlay.setVisible(true);
                            playBtn.setDisable(false);
                            playBtn.setText("▶ ¡JUGAR!");
                        });
                        return;
                    }
                }

                Platform.runLater(() -> {
                    playBtn.setDisable(false);
                    playBtn.setText("▶ ¡JUGAR!");
                    runTask(createLaunchTask());
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    log("Error verificando Java: " + ex.getMessage());
                    playBtn.setDisable(false);
                    playBtn.setText("▶ ¡JUGAR!");
                });
            }
        });
    }

    /** Construye el overlay de descarga de Java. */
    private StackPane buildJavaDownloadOverlay() {
        StackPane dim = new StackPane();
        dim.setStyle("-fx-background-color: rgba(0,0,0,0.65);");

        VBox card = new VBox(20);
        card.setMaxWidth(480);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        card.getStyleClass().add("mc-card");
        card.setStyle(card.getStyle() + "; -fx-border-color: #0E639C; -fx-border-width: 2;");

        Label title = new Label("☕ Motor Java Requerido");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");

        javaStatus = new Label("Detectamos que esta versión de Minecraft necesita Java 8.");
        javaStatus.setWrapText(true);
        javaStatus.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 14px;");

        javaProgress = new ProgressBar(0);
        javaProgress.setMaxWidth(Double.MAX_VALUE);
        javaProgress.setStyle("-fx-accent: #0E639C;");

        Button downloadBtn = new Button("Descargar Java Portátil (Recomendado)");
        downloadBtn.getStyleClass().add("button-primary");
        downloadBtn.setPrefWidth(300);
        downloadBtn.setOnAction(e -> {
            javaDownloadOverlay.setVisible(false);
            runTask(createJavaDownloadTask(detectedJavaVersion));
        });

        Button closeBtn = new Button("Cancelar");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888888;");
        closeBtn.setOnAction(e -> javaDownloadOverlay.setVisible(false));

        card.getChildren().addAll(title, javaStatus, javaProgress, downloadBtn, closeBtn);
        dim.getChildren().add(card);
        return dim;
    }

    private javafx.concurrent.Task<Void> createJavaDownloadTask(int version) {
        return new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Iniciando descarga de Java " + version + "...");
                facade.runtime().downloadJavaAsync(version, p -> {
                    updateProgress(p, 1.0);
                }, path -> {
                    Platform.runLater(() -> log("Java " + version + " Portable instalado en: " + path));
                }, err -> {
                    Platform.runLater(() -> log("Error descargando Java " + version + ": " + err));
                });
                return null;
            }
        };
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
                
                Path jar = facade.directories().versionsDir().resolve(selected.lastVersionId).resolve(selected.lastVersionId + ".jar");
                if (!java.nio.file.Files.exists(jar)) {
                    Platform.runLater(() -> log("Detectada versión faltando. Iniciando auto-instalador para " + selected.lastVersionId + "..."));
                    facade.installVersion(selected.lastVersionId, s -> Platform.runLater(() -> log(s)));
                }
                
                updateMessage("Iniciando Minecraft...");
                long ram = com.experimento.launcher.service.HardwareProbe.totalPhysicalRamMiB();
                
                String pId = selected.id;
                Process proc = facade.startGame(selected, ram, s -> Platform.runLater(() -> log(s)));
                
                Platform.runLater(() -> {
                    activeProcesses.put(pId, proc);
                    runningState.get(pId).set(true);
                    log("Instancia [" + selected.displayName + "] iniciada.");
                });
                
                proc.onExit().thenAccept(p -> Platform.runLater(() -> {
                    activeProcesses.remove(pId);
                    if (runningState.containsKey(pId)) runningState.get(pId).set(false);
                    log("Instancia [" + pId + "] cerrada.");
                }));

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
            logArea.selectPositionCaret(logArea.getLength());
        });
    }

    @Override
    public void stop() { workers.shutdownNow(); }
}

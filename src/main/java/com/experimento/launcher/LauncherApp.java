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
    private final List<ManifestVersionEntry> allManifestEntries = new ArrayList<>();
    private boolean syncingVersionUi;
    private boolean syncingIdentityUi;

    private LauncherFacade facade;
    private List<LauncherProfile> profiles;
    private LauncherProfile selected;

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

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        LauncherDirectories dirs = LauncherDirectories.fromDefault();
        dirs.ensureBaseDirs();
        facade = new LauncherFacade(dirs);
        profiles = new ArrayList<>(facade.profiles().loadOrCreateDefault());
        for (LauncherProfile p : profiles) {
            LauncherFacade.maybeImportTlauncherJvm(p);
        }
        facade.profiles().save(profiles);

        profileList = new ListView<>(FXCollections.observableList(profiles));
        profileList.setCellFactory(
                lv ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(LauncherProfile item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(empty || item == null ? null : item.displayName + " (" + item.username + ")");
                            }
                        });
        profileList
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, o, n) -> bindProfile(n));
        profileList.getSelectionModel().selectFirst();

        displayNameField = new TextField();
        usernameField = new TextField();
        uuidField = new TextField();
        uuidField.setPromptText("UUID offline");

        versionFilter =
                new ComboBox<>(
                        FXCollections.observableArrayList(
                                "Todas",
                                "Solo releases",
                                "Solo snapshots",
                                "Clásicas (beta/alpha)"));
        versionFilter.setPrefWidth(140);
        versionFilter.setValue("Todas");
        versionFilter.setOnAction(e -> applyVersionFilter());

        versionCombo = new ComboBox<>();
        versionCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(versionCombo, Priority.ALWAYS);
        versionCombo.setCellFactory(
                lv ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(ManifestVersionEntry item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(empty || item == null ? null : item.label());
                            }
                        });
        versionCombo.setButtonCell(
                new ListCell<>() {
                    @Override
                    protected void updateItem(ManifestVersionEntry item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item.label());
                    }
                });
        versionCombo
                .valueProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (!syncingVersionUi && selected != null && n != null) {
                                selected.lastVersionId = n.id();
                            }
                        });

        Button refreshManifestBtn = new Button("Actualizar lista");
        refreshManifestBtn.setOnAction(e -> loadVersionManifestAsync());

        presetCombo = new ComboBox<>(FXCollections.observableArrayList(JvmPresetKind.values()));
        jvmArea = new TextArea();
        jvmArea.setPrefRowCount(3);
        globalMcCheck = new CheckBox("Usar ~/.minecraft global (avanzado)");
        Button syncUuidBtn = new Button("UUID desde nombre");
        syncUuidBtn.setOnAction(e -> syncUuidFromUsername());

        displayNameField
                .textProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (selected != null && !syncingIdentityUi) {
                                selected.displayName = n;
                                syncIdentityFromDisplayName(n);
                            }
                            profileList.refresh();
                        });
        usernameField
                .textProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (selected != null && !syncingIdentityUi) {
                                selected.username = n;
                                syncUuidFromUsername();
                            }
                        });
        uuidField
                .textProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (selected != null) {
                                selected.offlineUuid = n;
                            }
                        });
        presetCombo.setOnAction(
                e -> {
                    if (selected != null && presetCombo.getValue() != null) {
                        selected.jvmPreset = presetCombo.getValue();
                        refreshHints();
                    }
                });
        jvmArea
                .textProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (selected != null) {
                                selected.customJvmArgs = n;
                            }
                        });
        globalMcCheck.setOnAction(
                e -> {
                    if (selected != null) {
                        selected.useGlobalMinecraftFolder = globalMcCheck.isSelected();
                    }
                });

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.setPadding(new Insets(8));
        int r = 0;
        form.add(new Label("Nombre visible"), 0, r);
        form.add(displayNameField, 1, r);
        r++;
        // Usuario MC y UUID se manejan internamente a partir de "Nombre visible".
        // Se ocultan en la UI para simplificar la experiencia.
        form.add(new Label("Versión"), 0, r);
        HBox versionRow = new HBox(8, versionFilter, versionCombo, refreshManifestBtn);
        HBox.setHgrow(versionCombo, Priority.ALWAYS);
        form.add(versionRow, 1, r);
        r++;
        form.add(new Label("Preset JVM"), 0, r);
        form.add(presetCombo, 1, r);
        r++;
        form.add(new Label("JVM extra"), 0, r);
        form.add(jvmArea, 1, r);
        r++;
        form.add(globalMcCheck, 1, r);
        r++;

        serverTable = new TableView<>();
        TableColumn<ServerEntry, String> colName = new TableColumn<>("Nombre");
        colName.setCellValueFactory(
                cd -> new SimpleStringProperty(cd.getValue() == null ? "" : cd.getValue().name));
        TableColumn<ServerEntry, String> colAddr = new TableColumn<>("IP:puerto");
        colAddr.setCellValueFactory(
                cd -> new SimpleStringProperty(cd.getValue() == null ? "" : cd.getValue().address));
        TableColumn<ServerEntry, Boolean> colCracked = new TableColumn<>("Cracked");
        colCracked.setCellValueFactory(
                cd -> {
                    ServerEntry v = cd.getValue();
                    boolean b = v != null && v.crackedServer;
                    return new javafx.beans.property.SimpleBooleanProperty(b).asObject();
                });
        colCracked.setCellFactory(
                column ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(Boolean item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                } else {
                                    ServerEntry se = getTableRow().getItem();
                                    CheckBox cb = new CheckBox("Servidor cracked / Aternos");
                                    cb.setSelected(se.crackedServer);
                                    cb.setOnAction(ev -> {
                                        se.crackedServer = cb.isSelected();
                                        refreshAternosRowHint(se);
                                    });
                                    setGraphic(cb);
                                }
                            }
                        });
        colName.setPrefWidth(160);
        colAddr.setPrefWidth(260);
        serverTable.getColumns().addAll(colName, colAddr, colCracked);
        serverTable.setPrefHeight(180);
        serverTable.setEditable(true);
        colName.setCellFactory(TextFieldTableCell.forTableColumn());
        colName.setOnEditCommit(
                ev -> {
                    ServerEntry se = ev.getRowValue();
                    if (se != null) {
                        se.name = ev.getNewValue();
                    }
                });
        colAddr.setCellFactory(TextFieldTableCell.forTableColumn());
        colAddr.setOnEditCommit(
                ev -> {
                    ServerEntry se = ev.getRowValue();
                    if (se != null) {
                        se.address = ev.getNewValue();
                    }
                });

        Button addSrv = new Button("Añadir servidor");
        addSrv.setOnAction(
                e -> {
                    if (selected == null) {
                        return;
                    }
                    if (selected.servers == null) {
                        selected.servers = new ArrayList<>();
                    }
                    ServerEntry se = new ServerEntry("Mi servidor", "nombre.aternos.me:25565");
                    se.crackedServer = true;
                    selected.servers.add(se);
                    serverTable.setItems(FXCollections.observableList(selected.servers));
                });
        Button delSrv = new Button("Quitar selección");
        delSrv.setOnAction(
                e -> {
                    if (selected == null) {
                        return;
                    }
                    ServerEntry se = serverTable.getSelectionModel().getSelectedItem();
                    if (se != null) {
                        selected.servers.remove(se);
                        serverTable.setItems(FXCollections.observableList(selected.servers));
                    }
                });

        aternosHint =
                new Label(
                        "UX Aternos: marca «Cracked» si en Aternos tienes Cracked ON (usuario offline). "
                                + "Si Cracked OFF, necesitas cuenta premium (login Microsoft, fase futura). "
                                + "Misma versión MC que el servidor; modded → mismo loader en el cliente.");
        aternosHint.setWrapText(true);
        modHintLabel = new Label();
        modHintLabel.setWrapText(true);

        VBox center =
                new VBox(
                        8,
                        form,
                        new Separator(),
                        new Label("Servidores → servers.dat (NBT gzip)"),
                        serverTable,
                        new HBox(8, addSrv, delSrv),
                        aternosHint,
                        modHintLabel);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(8);

        Button newProfile = new Button("Nuevo perfil");
        newProfile.setOnAction(
                e -> {
                    LauncherProfile p = LauncherProfile.createDefault();
                    profiles.add(p);
                    profileList.setItems(FXCollections.observableList(profiles));
                    profileList.getSelectionModel().select(p);
                    try {
                        facade.profiles().save(profiles);
                    } catch (Exception ex) {
                        log(ex.getMessage());
                    }
                });
        Button saveBtn = new Button("Guardar");
        saveBtn.setOnAction(
                e -> {
                    try {
                        if (selected != null && (selected.offlineUuid == null || selected.offlineUuid.isBlank())) {
                            syncUuidFromUsername();
                        }
                        facade.profiles().save(profiles);
                        log("Perfiles guardados.");
                    } catch (Exception ex) {
                        log("Error guardando: " + ex.getMessage());
                    }
                });
        Button installBtn = new Button("Instalar versión");
        installBtn.setOnAction(
                e ->
                        workers.submit(
                                () -> {
                                    try {
                                        if (selected == null) {
                                            return;
                                        }
                                        String v = selected.lastVersionId;
                                        log("Instalando " + v + "… (primera vez descarga assets completos)");
                                        facade.installVersion(v, this::log);
                                        Platform.runLater(this::refreshHints);
                                        log("Instalación terminada.");
                                    } catch (Exception ex) {
                                        log("Error instalación: " + ex.getMessage());
                                    }
                                }));
        Button playBtn = new Button("Jugar");
        playBtn.setDefaultButton(true);
        playBtn.setOnAction(
                e ->
                        workers.submit(
                                () -> {
                                    try {
                                        if (selected == null) {
                                            return;
                                        }
                                        if (!OfflineUuid.uuidMatchesUsername(selected.username, selected.offlineUuid)) {
                                            log("UUID no coincide con el nombre. Pulsa «UUID desde nombre».");
                                            return;
                                        }
                                        long ram = HardwareProbe.totalPhysicalRamMiB();
                                        facade.startGame(selected, ram, this::log);
                                    } catch (Exception ex) {
                                        log("Error al lanzar: " + ex.getMessage());
                                    }
                                }));

        HBox actions = new HBox(8, newProfile, saveBtn, installBtn, playBtn);
        actions.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.setLeft(profileList);
        profileList.setPrefWidth(230);
        root.setCenter(center);
        BorderPane.setMargin(center, new Insets(8));
        VBox bottom = new VBox(actions, new Label("Registro"), logArea);
        root.setBottom(bottom);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        Scene scene = new Scene(root, 980, 720);
        stage.setTitle(LauncherMetadata.DISPLAY_NAME);
        stage.setScene(scene);
        stage.show();

        loadVersionManifestAsync();

        stage.setOnCloseRequest(ev -> workers.shutdownNow());
    }

    private void loadVersionManifestAsync() {
        workers.submit(
                () -> {
                    try {
                        List<ManifestVersionEntry> list = facade.fetchManifestVersions();
                        Platform.runLater(
                                () -> {
                                    allManifestEntries.clear();
                                    allManifestEntries.addAll(list);
                                    if (selected != null) {
                                        ensureProfileVersionInBackingList(selected.lastVersionId);
                                    }
                                    applyVersionFilter();
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> log("Error cargando manifest: " + ex.getMessage()));
                    }
                });
    }

    private void ensureProfileVersionInBackingList(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        for (ManifestVersionEntry e : allManifestEntries) {
            if (id.equals(e.id())) {
                return;
            }
        }
        allManifestEntries.add(new ManifestVersionEntry(id, "perfil"));
    }

    private boolean matchesVersionFilter(ManifestVersionEntry e, String filter) {
        if (filter == null || "Todas".equals(filter)) {
            return true;
        }
        String t = e.type();
        if (t == null) {
            t = "";
        }
        if ("Solo releases".equals(filter)) {
            return "release".equalsIgnoreCase(t);
        }
        if ("Solo snapshots".equals(filter)) {
            return "snapshot".equalsIgnoreCase(t);
        }
        if ("Clásicas (beta/alpha)".equals(filter)) {
            return "old_beta".equalsIgnoreCase(t) || "old_alpha".equalsIgnoreCase(t);
        }
        return true;
    }

    private List<ManifestVersionEntry> computeFilteredList() {
        String filter = versionFilter.getValue();
        if (filter == null) {
            filter = "Todas";
        }
        String profileId =
                selected != null && selected.lastVersionId != null ? selected.lastVersionId : "";

        List<ManifestVersionEntry> filtered = new ArrayList<>();
        for (ManifestVersionEntry e : allManifestEntries) {
            if (matchesVersionFilter(e, filter) || (!profileId.isBlank() && profileId.equals(e.id()))) {
                filtered.add(e);
            }
        }
        return filtered;
    }

    private void applyVersionFilter() {
        if (selected != null) {
            ensureProfileVersionInBackingList(selected.lastVersionId);
        }
        List<ManifestVersionEntry> filtered = computeFilteredList();
        syncingVersionUi = true;
        try {
            versionCombo.setItems(FXCollections.observableArrayList(filtered));
            if (selected != null) {
                selectVersionForProfile(selected.lastVersionId);
            } else {
                versionCombo.setValue(null);
            }
        } finally {
            syncingVersionUi = false;
        }
    }

    private void selectVersionForProfile(String id) {
        if (id == null || id.isBlank()) {
            versionCombo.setValue(null);
            return;
        }
        for (ManifestVersionEntry e : versionCombo.getItems()) {
            if (id.equals(e.id())) {
                versionCombo.setValue(e);
                return;
            }
        }
        versionCombo.setValue(null);
    }

    private void bindProfile(LauncherProfile p) {
        selected = p;
        if (p == null) {
            syncingVersionUi = true;
            try {
                versionCombo.setItems(FXCollections.observableArrayList());
                versionCombo.setValue(null);
            } finally {
                syncingVersionUi = false;
            }
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
        jvmArea.setText(p.customJvmArgs == null ? "" : p.customJvmArgs);
        globalMcCheck.setSelected(p.useGlobalMinecraftFolder);
        if (p.servers == null) {
            p.servers = new ArrayList<>();
        }
        serverTable.setItems(FXCollections.observableList(p.servers));
        applyVersionFilter();
        if (p.offlineUuid == null || p.offlineUuid.isBlank()) {
            syncUuidFromUsername();
        }
        refreshHints();
    }

    private void syncIdentityFromDisplayName(String displayName) {
        if (selected == null) {
            return;
        }
        String normalized = displayName == null ? "" : displayName.trim();
        syncingIdentityUi = true;
        try {
            selected.username = normalized;
            usernameField.setText(normalized);
        } finally {
            syncingIdentityUi = false;
        }
        syncUuidFromUsername();
    }

    private void refreshAternosRowHint(ServerEntry se) {
        if (se == null) {
            return;
        }
        log(
                se.crackedServer
                        ? "Servidor marcado Cracked ON → puedes entrar con usuario offline de este perfil."
                        : "Servidor Cracked OFF → hace falta cuenta premium (Microsoft) en el futuro.");
    }

    private void syncUuidFromUsername() {
        if (selected == null) {
            return;
        }
        try {
            selected.offlineUuid =
                    OfflineUuid.toString(OfflineUuid.forUsername(selected.username == null ? "Player" : selected.username));
            uuidField.setText(selected.offlineUuid);
        } catch (Exception ex) {
            log("Usuario inválido (3-16, [a-zA-Z0-9_]): " + ex.getMessage());
        }
    }

    private void refreshHints() {
        if (selected == null) {
            return;
        }
        long ram = HardwareProbe.totalPhysicalRamMiB();
        JvmPresetKind eff =
                selected.jvmPreset == JvmPresetKind.AUTO
                        ? JvmPresetService.resolveAutoKind(ram)
                        : selected.jvmPreset;
        modHintLabel.setText(
                AutoOptimizerService.modSuggestionText(eff)
                        + " | RAM ~"
                        + ram
                        + " MiB | AUTO resuelve a "
                        + JvmPresetService.resolveAutoKind(ram));
    }

    private void log(String s) {
        Platform.runLater(
                () -> {
                    logArea.appendText(s + "\n");
                    logArea.setScrollTop(Double.MAX_VALUE);
                });
    }

    @Override
    public void stop() {
        workers.shutdownNow();
    }
}

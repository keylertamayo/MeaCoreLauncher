import { useEffect, useMemo, useState } from "react";
import {
  Play, ChevronDown, Settings, Globe, Newspaper, Package,
  RefreshCw, Wifi, X, Plus, Trash2,
  Terminal, UserCircle, Download, Save, RotateCcw
} from "lucide-react";

const BG_IMAGE = "https://images.unsplash.com/photo-1656261650870-5c416854f8ba?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxkYXJrJTIwbWluZWNyYWZ0JTIwbGFuZHNjYXBlJTIwd2FsbHBhcGVyfGVufDF8fHx8MTc3NTQ5Mjk1MXww&ixlib=rb-4.1.0&q=80&w=1080";
const NEWS_IMAGE = "https://images.unsplash.com/photo-1761662826595-a379027d6c0f?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxtaW5lY3JhZnQlMjBuaWdodCUyMGZvcmVzdCUyMGRhcmslMjBzY2VuZXJ5fGVufDF8fHx8MTc3NTQ5Mjk1NXww&ixlib=rb-4.1.0&q=80&w=1080";

const allVersions = [
  { id: "release-1214", label: "1.21.4 [release]", type: "release" },
  { id: "release-1213", label: "1.21.3 [release]", type: "release" },
  { id: "release-1206", label: "1.20.6 [release]", type: "release" },
  { id: "snapshot-25w14a", label: "25w14a [snapshot]", type: "snapshot" },
  { id: "snapshot-25w12a", label: "25w12a [snapshot]", type: "snapshot" },
  { id: "forge-1211", label: "Forge 1.21.1", type: "modded" },
  { id: "fabric-1214", label: "Fabric 1.21.4", type: "modded" },
];

const jvmPresets = [
  { id: "auto", label: "Automático" },
  { id: "java21", label: "Java 21 (LTS)" },
  { id: "java17", label: "Java 17 (LTS)" },
  { id: "java8", label: "Java 8" },
];

const INSTALLABLE_TYPES = ["release", "modded"] as const;

type Profile = {
  id: string;
  name: string;
  displayName: string;
  version: string;
  versionFilter: string;
  jvmPreset: string;
  jvmExtra: string;
  useGlobalMinecraft: boolean;
  servers: { id: string; name: string; ip: string; cracked: boolean }[];
};

type LauncherBridge = {
  getProfiles?: () => string | void;
  saveProfiles?: (profilesJson: string) => string | void;
  installVersion?: (versionJson: string) => string | void;
  startGame?: (payloadJson: string) => string | void;
};

declare global {
  interface Window {
    meacoreBridge?: LauncherBridge;
  }
}

const initialProfiles: Profile[] = [
  {
    id: "p1",
    name: "KronoxYT (KronoxYT)",
    displayName: "KronoxYT",
    version: "1.21.4 [release]",
    versionFilter: "Todas",
    jvmPreset: "java21",
    jvmExtra: "",
    useGlobalMinecraft: false,
    servers: [],
  },
  {
    id: "p2",
    name: "Supervivencia 1.20",
    displayName: "Supervivencia",
    version: "1.20.6 [release]",
    versionFilter: "Release",
    jvmPreset: "auto",
    jvmExtra: "-Xms512M",
    useGlobalMinecraft: false,
    servers: [
      { id: "s1", name: "Mi Servidor", ip: "play.miservidor.net:25565", cracked: false },
    ],
  },
];

const newsItems = [
  {
    id: 1,
    tag: "ACTUALIZACIÓN",
    date: "31 MAR 2026",
    title: "Minecraft 1.21.4 – The Garden Awakens",
    description: "Explora los nuevos biomas subacuáticos, criaturas marinas y bloques de coral mejorados en la última actualización.",
    image: NEWS_IMAGE,
  },
  {
    id: 2,
    tag: "EVENTO",
    date: "15 MAR 2026",
    title: "Evento de Primavera",
    description: "Celebra la primavera con skins exclusivas y misiones especiales disponibles por tiempo limitado.",
    image: BG_IMAGE,
  },
  {
    id: 3,
    tag: "PARCHE",
    date: "2 MAR 2026",
    title: "Hotfix 1.21.3 – Correcciones",
    description: "Corrección de errores críticos de rendimiento y estabilidad en el sistema de chunks.",
    image: NEWS_IMAGE,
  },
];

const navTabs = [
  { id: "news", icon: <Newspaper size={13} />, label: "Updates" },
  { id: "installations", icon: <Package size={13} />, label: "Instalaciones" },
  { id: "servers", icon: <Globe size={13} />, label: "Servidores" },
  { id: "mods", icon: <Globe size={13} />, label: "Mods" },
  { id: "registro", icon: <Terminal size={13} />, label: "Registro en linea" },
];

const logLines = [
  { time: "10:42:01", level: "INFO", msg: "MeaCore Launcher iniciado correctamente." },
  { time: "10:42:01", level: "INFO", msg: "Versión del launcher: 2.4.1" },
  { time: "10:42:02", level: "INFO", msg: "Verificando integridad de Java 21.0.3..." },
  { time: "10:42:02", level: "OK", msg: "Java 21.0.3 encontrado en /usr/lib/jvm/java-21" },
  { time: "10:42:03", level: "INFO", msg: "Cargando perfil: KronoxYT (KronoxYT)" },
  { time: "10:42:03", level: "INFO", msg: "Versión seleccionada: 1.21.4 [release]" },
  { time: "10:42:04", level: "INFO", msg: "Comprobando archivos de Minecraft 1.21.4..." },
  { time: "10:42:05", level: "OK", msg: "Todos los archivos verificados (2048/2048)" },
  { time: "10:42:05", level: "INFO", msg: "Descargando assets faltantes: 0 archivos" },
  { time: "10:42:06", level: "WARN", msg: "Memoria asignada inferior al recomendado (4GB < 6GB)" },
  { time: "10:42:07", level: "INFO", msg: "Iniciando Minecraft 1.21.4 con JVM: Java 21.0.3" },
  { time: "10:42:07", level: "INFO", msg: "Argumentos JVM: -Xmx4G -Xms512M -XX:+UseG1GC" },
  { time: "10:42:08", level: "OK", msg: "Proceso de Minecraft iniciado (PID: 18423)" },
];

// ─── Input style helper ─────────────────────────────────────────────────────
const inputStyle: React.CSSProperties = {
  background: "rgba(255,255,255,0.04)",
  border: "1px solid #2a2a2a",
  borderRadius: "4px",
  color: "#ccc",
  fontSize: "12px",
  padding: "6px 10px",
  outline: "none",
  width: "100%",
};

// ─── Small button helper ─────────────────────────────────────────────────────
function SmallBtn({ children, red = false, onClick }: { children: React.ReactNode; red?: boolean; onClick?: () => void }) {
  const [hover, setHover] = useState(false);
  return (
    <button
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        background: red
          ? hover ? "#cc0000" : "rgba(204,0,0,0.2)"
          : hover ? "rgba(255,255,255,0.12)" : "rgba(255,255,255,0.06)",
        border: `1px solid ${red ? "rgba(204,0,0,0.5)" : "#2a2a2a"}`,
        borderRadius: "4px",
        color: red ? "#ff6666" : "#aaa",
        fontSize: "11px",
        padding: "5px 12px",
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        gap: "5px",
        transition: "all 0.15s",
      }}
    >
      {children}
    </button>
  );
}

// ─── Select helper ────────────────────────────────────────────────────────────
function Select({ value, options, onChange }: { value: string; options: string[]; onChange: (v: string) => void }) {
  return (
    <div className="relative" style={{ display: "inline-block" }}>
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        style={{
          ...inputStyle,
          width: "auto",
          minWidth: "110px",
          paddingRight: "28px",
          appearance: "none",
          cursor: "pointer",
        }}
      >
        {options.map(o => <option key={o} value={o} style={{ background: "#111" }}>{o}</option>)}
      </select>
      <ChevronDown size={11} style={{ position: "absolute", right: "8px", top: "50%", transform: "translateY(-50%)", color: "#555", pointerEvents: "none" }} />
    </div>
  );
}

// ─── PROFILES TAB ────────────────────────────────────────────────────────────
function ProfilesTab({
  profiles,
  selectedId,
  onSelectProfile,
  profile,
  onUpdateProfile,
  onAddProfile,
  onDeleteProfile,
  onSave,
  onPlay,
}: {
  profiles: Profile[];
  selectedId: string;
  onSelectProfile: (id: string) => void;
  profile: Profile;
  onUpdateProfile: (fields: Partial<Profile>) => void;
  onAddProfile: () => void;
  onDeleteProfile: () => void;
  onSave: () => void;
  onPlay: () => void;
}) {
  const profileVersions = allVersions
    .filter(v => INSTALLABLE_TYPES.includes(v.type as (typeof INSTALLABLE_TYPES)[number]))
    .map(v => v.label);

  return (
    <div className="flex flex-col h-full">
      <div className="flex-1 overflow-y-auto px-6 py-5 flex flex-col gap-4" style={{ scrollbarWidth: "thin", scrollbarColor: "#222 transparent" }}>

          {/* Nombre visible */}
          <div className="flex items-center gap-4">
            <label style={{ color: "#888", fontSize: "12px", minWidth: "110px", textAlign: "right" }}>Perfil</label>
            <div className="relative" style={{ display: "inline-block" }}>
              <select
                value={selectedId}
                onChange={e => onSelectProfile(e.target.value)}
                style={{ ...inputStyle, width: "220px", paddingRight: "28px", appearance: "none", cursor: "pointer" }}
              >
                {profiles.map(p => <option key={p.id} value={p.id} style={{ background: "#111" }}>{p.displayName}</option>)}
              </select>
              <ChevronDown size={11} style={{ position: "absolute", right: "8px", top: "50%", transform: "translateY(-50%)", color: "#555", pointerEvents: "none" }} />
            </div>
            <SmallBtn red onClick={onDeleteProfile}><Trash2 size={11} /> Eliminar perfil</SmallBtn>
          </div>

          {/* Nombre visible */}
          <div className="flex items-center gap-4">
            <label style={{ color: "#888", fontSize: "12px", minWidth: "110px", textAlign: "right" }}>Nombre visible</label>
            <input
              style={inputStyle}
              value={profile.displayName}
              onChange={e => onUpdateProfile({ displayName: e.target.value, name: e.target.value })}
              placeholder="Nombre del perfil..."
            />
          </div>

          {/* Versión */}
          <div className="flex items-center gap-2 flex-wrap">
            <label style={{ color: "#888", fontSize: "12px", minWidth: "110px", textAlign: "right" }}>Versión</label>
            <Select
              value={profile.version}
              options={profileVersions}
              onChange={v => onUpdateProfile({ version: v })}
            />
            <SmallBtn>
              <RotateCcw size={11} /> Actualizar lista
            </SmallBtn>
          </div>

          {/* Preset JVM */}
          <div className="flex items-center gap-4">
            <label style={{ color: "#888", fontSize: "12px", minWidth: "110px", textAlign: "right" }}>Preset JVM</label>
            <Select
              value={profile.jvmPreset}
              options={jvmPresets.map(j => j.id)}
              onChange={v => onUpdateProfile({ jvmPreset: v })}
            />
          </div>

          {/* JVM Extra */}
          <div className="flex items-start gap-4">
            <label style={{ color: "#888", fontSize: "12px", minWidth: "110px", textAlign: "right", paddingTop: "6px" }}>JVM extra</label>
            <textarea
              style={{ ...inputStyle, resize: "vertical", minHeight: "52px", fontFamily: "monospace" }}
              value={profile.jvmExtra}
              onChange={e => onUpdateProfile({ jvmExtra: e.target.value })}
              placeholder="-Xms512M -XX:+UseG1GC ..."
            />
          </div>

          {/* Global Minecraft checkbox */}
          <div className="flex items-center gap-4">
            <span style={{ minWidth: "110px" }} />
            <label className="flex items-center gap-2 cursor-pointer">
              <div
                onClick={() => onUpdateProfile({ useGlobalMinecraft: !profile.useGlobalMinecraft })}
                className="flex items-center justify-center cursor-pointer transition-all"
                style={{
                  width: "14px", height: "14px",
                  border: `1px solid ${profile.useGlobalMinecraft ? "#cc0000" : "#333"}`,
                  borderRadius: "3px",
                  background: profile.useGlobalMinecraft ? "#cc0000" : "transparent",
                  flexShrink: 0,
                }}
              >
                {profile.useGlobalMinecraft && <span style={{ color: "white", fontSize: "9px", lineHeight: 1 }}>✓</span>}
              </div>
              <span style={{ color: "#777", fontSize: "12px" }}>Usar ~/.minecraft global (avanzado)</span>
            </label>
          </div>

      </div>

      {/* ── Bottom Action Bar ── */}
      <div className="flex items-center gap-2 px-4 py-3" style={{ background: "rgba(6,6,6,0.95)", borderTop: "1px solid #1a1a1a" }}>
        <SmallBtn onClick={onAddProfile}><Plus size={11} /> Nuevo perfil</SmallBtn>
        <SmallBtn onClick={onSave}><Save size={11} /> Guardar</SmallBtn>
        <SmallBtn><Download size={11} /> Instalar versión</SmallBtn>
        <div className="ml-auto">
          <button
            onClick={onPlay}
            className="flex items-center gap-2 px-6 py-2 cursor-pointer transition-all"
            style={{
              background: "#cc0000",
              border: "1px solid #ff2222",
              borderRadius: "5px",
              color: "white",
              fontSize: "12px",
              letterSpacing: "0.08em",
            }}
            onMouseEnter={e => (e.currentTarget.style.background = "#e60000")}
            onMouseLeave={e => (e.currentTarget.style.background = "#cc0000")}
          >
            <Play size={12} fill="white" /> JUGAR
          </button>
        </div>
      </div>
    </div>
  );
}

function ServersTab({
  profile,
  onUpdateProfile,
}: {
  profile: Profile;
  onUpdateProfile: (fields: Partial<Profile>) => void;
}) {
  const [newServer, setNewServer] = useState({ name: "", ip: "", cracked: false });
  const [selectedServer, setSelectedServer] = useState<string | null>(null);

  const addServer = () => {
    if (!newServer.name || !newServer.ip) return;
    onUpdateProfile({ servers: [...profile.servers, { id: `s${Date.now()}`, ...newServer }] });
    setNewServer({ name: "", ip: "", cracked: false });
  };

  const removeServer = () => {
    if (!selectedServer) return;
    onUpdateProfile({ servers: profile.servers.filter(s => s.id !== selectedServer) });
    setSelectedServer(null);
  };

  return (
    <div className="flex-1 overflow-y-auto px-8 py-6 flex flex-col gap-4" style={{ scrollbarWidth: "thin", scrollbarColor: "#222 transparent" }}>
      <h2 className="text-white" style={{ fontSize: "14px", letterSpacing: "0.05em" }}>SERVIDORES</h2>
      <p style={{ color: "#777", fontSize: "11px", marginBottom: "8px", letterSpacing: "0.04em" }}>
        Se guardan en el perfil y al iniciar Minecraft se reflejan en servers.dat.
      </p>
      <div style={{ border: "1px solid #222", borderRadius: "4px", overflow: "hidden" }}>
        <div className="grid" style={{ gridTemplateColumns: "1fr 1.4fr 80px", background: "rgba(255,255,255,0.04)", borderBottom: "1px solid #222" }}>
          {["Nombre", "IP:puerto", "Cracked"].map(h => (
            <div key={h} style={{ padding: "6px 10px", color: "#666", fontSize: "11px", letterSpacing: "0.05em" }}>{h}</div>
          ))}
        </div>
        {profile.servers.length === 0 ? (
          <div style={{ padding: "24px", textAlign: "center", color: "#444", fontSize: "12px" }}>Tabla sin contenido</div>
        ) : (
          profile.servers.map(s => (
            <div
              key={s.id}
              onClick={() => setSelectedServer(s.id === selectedServer ? null : s.id)}
              className="grid cursor-pointer transition-colors"
              style={{
                gridTemplateColumns: "1fr 1.4fr 80px",
                background: selectedServer === s.id ? "rgba(204,0,0,0.12)" : "transparent",
                borderBottom: "1px solid #1a1a1a",
              }}
            >
              <div style={{ padding: "6px 10px", color: "#aaa", fontSize: "12px" }}>{s.name}</div>
              <div style={{ padding: "6px 10px", color: "#aaa", fontSize: "12px", fontFamily: "monospace" }}>{s.ip}</div>
              <div style={{ padding: "6px 10px", color: s.cracked ? "#cc0000" : "#444", fontSize: "11px" }}>{s.cracked ? "ON" : "OFF"}</div>
            </div>
          ))
        )}
      </div>
      <div className="flex gap-2 mt-2 flex-wrap">
        <input style={{ ...inputStyle, flex: "1", minWidth: "80px" }} placeholder="Nombre" value={newServer.name} onChange={e => setNewServer(p => ({ ...p, name: e.target.value }))} />
        <input style={{ ...inputStyle, flex: "1.5", minWidth: "120px" }} placeholder="IP:puerto" value={newServer.ip} onChange={e => setNewServer(p => ({ ...p, ip: e.target.value }))} />
        <label className="flex items-center gap-1.5 cursor-pointer">
          <div
            onClick={() => setNewServer(p => ({ ...p, cracked: !p.cracked }))}
            style={{
              width: "13px", height: "13px", border: `1px solid ${newServer.cracked ? "#cc0000" : "#333"}`,
              borderRadius: "3px", background: newServer.cracked ? "#cc0000" : "transparent", cursor: "pointer",
              flexShrink: 0, display: "flex", alignItems: "center", justifyContent: "center",
            }}
          >
            {newServer.cracked && <span style={{ color: "white", fontSize: "8px" }}>✓</span>}
          </div>
          <span style={{ color: "#666", fontSize: "11px" }}>Cracked</span>
        </label>
      </div>
      <div className="flex gap-2 mt-2">
        <SmallBtn onClick={addServer}><Plus size={11} /> Añadir servidor</SmallBtn>
        <SmallBtn red onClick={removeServer}><Trash2 size={11} /> Quitar selección</SmallBtn>
      </div>
      <p style={{ color: "#cc6666", fontSize: "10px", marginTop: "8px", lineHeight: 1.5 }}>
        UX Aternos: marca «Cracked» si en Aternos tienes Cracked ON (usuario offline). Si Cracked OFF, necesitas cuenta oficial.
      </p>
    </div>
  );
}

// ─── REGISTRO TAB ─────────────────────────────────────────────────────────────
function RegistroTab() {
  const [filter, setFilter] = useState("Todos");
  const levelColor = (lvl: string) => {
    if (lvl === "OK") return "#4ade80";
    if (lvl === "WARN") return "#facc15";
    if (lvl === "ERROR") return "#f87171";
    return "#6b7280";
  };
  const filtered = filter === "Todos" ? logLines : logLines.filter(l => l.level === filter);
  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-3 px-4 py-2" style={{ background: "rgba(6,6,6,0.9)", borderBottom: "1px solid #1a1a1a" }}>
        <Terminal size={13} style={{ color: "#555" }} />
        <span style={{ color: "#555", fontSize: "11px", letterSpacing: "0.06em" }}>CONSOLA DE REGISTRO</span>
        <div className="ml-auto flex gap-1">
          {["Todos", "INFO", "OK", "WARN", "ERROR"].map(f => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              style={{
                background: filter === f ? "rgba(204,0,0,0.2)" : "transparent",
                border: `1px solid ${filter === f ? "rgba(204,0,0,0.5)" : "#222"}`,
                borderRadius: "3px",
                color: filter === f ? "#ff6666" : "#555",
                fontSize: "10px",
                padding: "2px 8px",
                cursor: "pointer",
                letterSpacing: "0.04em",
              }}
            >
              {f}
            </button>
          ))}
        </div>
      </div>
      <div
        className="flex-1 overflow-y-auto p-4 font-mono flex flex-col gap-0.5"
        style={{ background: "#050505", scrollbarWidth: "thin", scrollbarColor: "#222 transparent" }}
      >
        {filtered.map((line, i) => (
          <div key={i} className="flex gap-3" style={{ fontSize: "11px", lineHeight: "1.6" }}>
            <span style={{ color: "#333", minWidth: "60px" }}>{line.time}</span>
            <span style={{ color: levelColor(line.level), minWidth: "38px" }}>[{line.level}]</span>
            <span style={{ color: "#888" }}>{line.msg}</span>
          </div>
        ))}
        <div className="flex items-center gap-1 mt-2">
          <span style={{ color: "#cc0000", fontSize: "11px" }}>▌</span>
          <span style={{ color: "#333", fontSize: "11px", fontFamily: "monospace" }}>_</span>
        </div>
      </div>
    </div>
  );
}

// ─── MAIN LAUNCHER ───────────────────────────────────────────────────────────
export function MinecraftLauncher() {
  const [activeTab, setActiveTab] = useState("news");
  const [profiles, setProfiles] = useState<Profile[]>(initialProfiles);
  const [selectedId, setSelectedId] = useState("p1");
  const selectableVersions = useMemo(
    () => allVersions.filter(v => INSTALLABLE_TYPES.includes(v.type as (typeof INSTALLABLE_TYPES)[number])),
    []
  );
  const [selectedVersion, setSelectedVersion] = useState(selectableVersions[0]);
  const [installedVersionIds, setInstalledVersionIds] = useState<string[]>(["release-1214", "forge-1211"]);
  const [versionOpen, setVersionOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [saved, setSaved] = useState(false);
  const username = "KronoxYT";
  const profile = profiles.find(p => p.id === selectedId) ?? profiles[0];

  useEffect(() => {
    const tryLoad = () => {
      if (!window.meacoreBridge?.getProfiles) return;
      try {
        const raw = window.meacoreBridge.getProfiles();
        if (typeof raw === "string" && raw.startsWith("[")) {
          const fromBackend = JSON.parse(raw) as Profile[];
          if (fromBackend.length > 0) {
            setProfiles(fromBackend);
            setSelectedId(fromBackend[0].id);
          }
        }
      } catch {
        /* keep initialProfiles */
      }
    };
    window.addEventListener("meacoreBridgeReady", tryLoad);
    tryLoad();
    const retry = window.setTimeout(tryLoad, 500);
    const retry2 = window.setTimeout(tryLoad, 1500);
    return () => {
      window.removeEventListener("meacoreBridgeReady", tryLoad);
      window.clearTimeout(retry);
      window.clearTimeout(retry2);
    };
  }, []);

  const ensureBridge = () => {
    if (!window.meacoreBridge) {
      window.alert("Bridge Java no conectado. Reinicia con: cd frontend && npm run build ; cd .. && ./gradlew run");
      return false;
    }
    return true;
  };

  useEffect(() => {
    if (!profile && profiles.length) {
      setSelectedId(profiles[0].id);
    }
  }, [profile, profiles]);

  const updateProfile = (fields: Partial<Profile>) => {
    setProfiles(prev => prev.map(p => p.id === selectedId ? { ...p, ...fields } : p));
  };

  const addProfile = () => {
    const id = `p${Date.now()}`;
    const newP: Profile = {
      id,
      name: "Nuevo Perfil",
      displayName: "Nuevo Perfil",
      version: selectableVersions[0]?.label ?? "1.21.4 [release]",
      versionFilter: "Todas",
      jvmPreset: "auto",
      jvmExtra: "",
      useGlobalMinecraft: false,
      servers: [],
    };
    setProfiles(prev => [...prev, newP]);
    setSelectedId(id);
  };

  const deleteSelectedProfile = () => {
    if (profiles.length <= 1) return;
    const idx = profiles.findIndex(p => p.id === selectedId);
    const next = profiles.filter(p => p.id !== selectedId);
    setProfiles(next);
    const fallback = next[Math.max(0, idx - 1)] ?? next[0];
    if (fallback) {
      setSelectedId(fallback.id);
    }
  };

  const saveProfiles = async () => {
    if (!ensureBridge()) return;
    setSaved(true);
    try {
      const res = window.meacoreBridge?.saveProfiles?.(JSON.stringify(profiles));
      if (typeof res === "string" && res !== "OK") {
        window.alert(`No se pudo guardar perfiles: ${res}`);
      }
    } finally {
      setTimeout(() => setSaved(false), 2000);
    }
  };

  const handlePlay = () => {
    if (!profile || isPlaying) return;
    if (!ensureBridge()) return;
    setIsPlaying(true);
    setProgress(0);
    setActiveTab("registro");
    const res = window.meacoreBridge.startGame?.(
      JSON.stringify({ profile, version: selectedVersion.label })
    );
    if (res === undefined) {
      window.alert("Bridge no respondió al iniciar juego.");
      setIsPlaying(false);
      setProgress(0);
      return;
    }
    if (typeof res === "string" && res !== "OK") {
      window.alert(`No se pudo iniciar Minecraft: ${res}`);
      setIsPlaying(false);
      setProgress(0);
      return;
    }
    const interval = setInterval(() => {
      setProgress((prev) => {
        if (prev >= 100) {
          clearInterval(interval);
          setTimeout(() => { setIsPlaying(false); setProgress(0); }, 800);
          return 100;
        }
        return prev + Math.random() * 12;
      });
    }, 200);
  };

  return (
    <div className="relative w-full h-screen overflow-hidden flex flex-col select-none"
      style={{ background: "#0a0a0a", fontFamily: "'Segoe UI', sans-serif" }}>

      {/* Background */}
      <div className="absolute inset-0 z-0">
        <img src={BG_IMAGE} alt="bg" className="w-full h-full object-cover opacity-20" />
        <div className="absolute inset-0" style={{ background: "linear-gradient(to bottom, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0.5) 40%, rgba(0,0,0,0.85) 80%, rgba(0,0,0,0.98) 100%)" }} />
      </div>

      {/* ── Title Bar ── */}
      <div className="relative z-20 flex items-center px-4 py-2"
        style={{ background: "rgba(10,10,10,0.97)", borderBottom: "1px solid #1a1a1a" }}>
        <div className="flex items-center gap-3">
          <svg width="20" height="20" viewBox="0 0 32 32" fill="none">
            <rect width="32" height="32" rx="4" fill="#1a1a1a"/>
            <rect x="10" y="4" width="12" height="10" fill="#4a7c3f"/>
            <rect x="8" y="6" width="16" height="14" fill="#5a9e4e"/>
            <rect x="12" y="16" width="8" height="6" fill="#4a7c3f"/>
            <rect x="12" y="14" width="3" height="3" fill="#0a0a0a"/>
            <rect x="17" y="14" width="3" height="3" fill="#0a0a0a"/>
            <rect x="13" y="18" width="6" height="2" fill="#0a0a0a"/>
            <rect x="14" y="17" width="1" height="1" fill="#0a0a0a"/>
            <rect x="17" y="17" width="1" height="1" fill="#0a0a0a"/>
            <rect x="14" y="20" width="1" height="3" fill="#4a7c3f"/>
            <rect x="17" y="20" width="1" height="3" fill="#4a7c3f"/>
          </svg>
          <span style={{ color: "#ccc", fontSize: "12px", letterSpacing: "0.05em" }}>MeaCore Launcher</span>
        </div>
      </div>

      {/* ── Navigation ── */}
      <div className="relative z-20 flex items-center px-4" style={{ background: "rgba(8,8,8,0.92)", borderBottom: "1px solid #1c1c1c" }}>
        {navTabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className="flex items-center gap-1.5 px-4 py-3 cursor-pointer relative transition-colors"
            style={{
              color: activeTab === tab.id ? "#fff" : "#555",
              fontSize: "11px",
              letterSpacing: "0.07em",
              background: "transparent",
              border: "none",
              outline: "none",
            }}
          >
            {tab.icon}
            {tab.label.toUpperCase()}
            {activeTab === tab.id && <span className="absolute bottom-0 left-0 right-0 h-0.5" style={{ background: "#cc0000" }} />}
          </button>
        ))}
        <div className="ml-auto flex items-center gap-2">
          <div className="flex items-center gap-1.5 px-2 py-1 rounded" style={{ background: "rgba(0,200,0,0.08)", border: "1px solid rgba(0,180,0,0.2)" }}>
            <Wifi size={9} style={{ color: "#4ade80" }} />
            <span style={{ color: "#4ade80", fontSize: "9px", letterSpacing: "0.06em" }}>EN LÍNEA</span>
          </div>
          <button className="p-2 rounded cursor-pointer" style={{ color: "#444", background: "transparent" }}
            onClick={() => setActiveTab("servers")}
            onMouseEnter={e => { e.currentTarget.style.color = "#cc0000"; e.currentTarget.style.background = "rgba(204,0,0,0.1)"; }}
            onMouseLeave={e => { e.currentTarget.style.color = "#444"; e.currentTarget.style.background = "transparent"; }}>
            <Settings size={14} />
          </button>
        </div>
      </div>

      {/* ── Content ── */}
      <div className="relative z-10 flex-1 overflow-hidden flex flex-col">

        {/* UPDATES */}
        {activeTab === "news" && (
          <div className="flex-1 overflow-y-auto px-8 py-6 flex flex-col gap-4" style={{ scrollbarWidth: "thin", scrollbarColor: "#222 transparent" }}>
            <h2 className="text-white" style={{ fontSize: "14px", letterSpacing: "0.05em" }}>UPDATES DEL LAUNCHER</h2>
            <div className="relative rounded-lg overflow-hidden cursor-pointer group"
              style={{ border: "1px solid #222", height: "240px" }}
              onMouseEnter={e => (e.currentTarget.style.borderColor = "#cc0000")}
              onMouseLeave={e => (e.currentTarget.style.borderColor = "#222")}>
              <img src={newsItems[0].image} alt="" className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105" />
              <div className="absolute inset-0" style={{ background: "linear-gradient(to top, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0.3) 60%, transparent 100%)" }} />
              <div className="absolute bottom-0 left-0 right-0 p-5">
                <span className="inline-block px-2 py-0.5 rounded text-white mb-2" style={{ background: "#cc0000", fontSize: "9px", letterSpacing: "0.1em" }}>{newsItems[0].tag}</span>
                <h2 className="text-white mb-1" style={{ fontSize: "18px" }}>{newsItems[0].title}</h2>
                <p style={{ color: "#aaa", fontSize: "13px" }}>{newsItems[0].description}</p>
                <span style={{ color: "#555", fontSize: "11px" }}>{newsItems[0].date}</span>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              {newsItems.slice(1).map(item => (
                <div key={item.id} className="relative rounded-lg overflow-hidden cursor-pointer group"
                  style={{ border: "1px solid #1e1e1e", height: "150px" }}
                  onMouseEnter={e => (e.currentTarget.style.borderColor = "#cc0000")}
                  onMouseLeave={e => (e.currentTarget.style.borderColor = "#1e1e1e")}>
                  <img src={item.image} alt="" className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105" />
                  <div className="absolute inset-0" style={{ background: "linear-gradient(to top, rgba(0,0,0,0.92) 0%, rgba(0,0,0,0.2) 70%, transparent 100%)" }} />
                  <div className="absolute bottom-0 left-0 right-0 p-3">
                    <span className="inline-block px-2 py-0.5 rounded mb-1" style={{ background: "rgba(204,0,0,0.8)", color: "white", fontSize: "9px", letterSpacing: "0.08em" }}>{item.tag}</span>
                    <h3 className="text-white" style={{ fontSize: "13px" }}>{item.title}</h3>
                    <span style={{ color: "#555", fontSize: "10px" }}>{item.date}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* PERFILES */}
        {activeTab === "profiles" && profile && (
          <ProfilesTab
            profiles={profiles}
            selectedId={selectedId}
            onSelectProfile={setSelectedId}
            profile={profile}
            onUpdateProfile={updateProfile}
            onAddProfile={addProfile}
            onDeleteProfile={deleteSelectedProfile}
            onSave={saveProfiles}
            onPlay={handlePlay}
          />
        )}

        {/* SERVIDORES */}
        {activeTab === "servers" && profile && (
          <ServersTab profile={profile} onUpdateProfile={updateProfile} />
        )}

        {/* INSTALACIONES */}
        {activeTab === "installations" && (
          <div className="flex-1 overflow-y-auto px-8 py-6">
            <div className="flex items-center justify-between mb-5">
              <h2 className="text-white" style={{ fontSize: "14px", letterSpacing: "0.05em" }}>INSTALACIONES</h2>
              <SmallBtn
                onClick={() => {
                  const next = selectableVersions.find(v => !installedVersionIds.includes(v.id));
                  if (!next) return;
                  if (!ensureBridge()) return;
                  const res = window.meacoreBridge?.installVersion?.(JSON.stringify({ version: next.label }));
                  if (typeof res === "string" && res !== "OK") {
                    window.alert(`No se pudo instalar versión: ${res}`);
                    return;
                  }
                  setInstalledVersionIds(prev => [...prev, next.id]);
                }}
              >
                <Plus size={11} /> Nueva Instalación
              </SmallBtn>
            </div>
            <p style={{ color: "#666", fontSize: "11px", marginBottom: "10px" }}>INSTALADAS</p>
            <div className="flex flex-col gap-2 mb-5">
              {selectableVersions.filter(v => installedVersionIds.includes(v.id)).map(v => (
                <div key={v.id}
                  className="flex items-center justify-between p-3 rounded-lg cursor-pointer transition-all"
                  style={{ background: "rgba(255,255,255,0.02)", border: "1px solid #1e1e1e" }}
                  onMouseEnter={e => { e.currentTarget.style.background = "rgba(204,0,0,0.06)"; e.currentTarget.style.borderColor = "#333"; }}
                  onMouseLeave={e => { e.currentTarget.style.background = "rgba(255,255,255,0.02)"; e.currentTarget.style.borderColor = "#1e1e1e"; }}>
                  <div className="flex items-center gap-3">
                    <div className="w-7 h-7 rounded flex items-center justify-center" style={{ background: "rgba(204,0,0,0.15)" }}>
                      <Package size={14} style={{ color: "#cc0000" }} />
                    </div>
                    <div>
                      <p className="text-white" style={{ fontSize: "12px" }}>{v.label}</p>
                      <p style={{ color: "#444", fontSize: "10px", textTransform: "uppercase", letterSpacing: "0.04em" }}>{v.type}</p>
                    </div>
                  </div>
                  <button className="px-3 py-1.5 rounded text-white cursor-pointer text-xs transition-all"
                    style={{ background: "rgba(204,0,0,0.6)", border: "none" }}
                    onMouseEnter={e => (e.currentTarget.style.background = "#cc0000")}
                    onMouseLeave={e => (e.currentTarget.style.background = "rgba(204,0,0,0.6)")}
                    onClick={() => { setSelectedVersion(v); setActiveTab("news"); }}>
                    Jugar
                  </button>
                </div>
              ))}
            </div>
            <p style={{ color: "#666", fontSize: "11px", marginBottom: "10px" }}>NO INSTALADAS</p>
            <div className="flex flex-col gap-2">
              {selectableVersions.filter(v => !installedVersionIds.includes(v.id)).map(v => (
                <div key={v.id}
                  className="flex items-center justify-between p-3 rounded-lg cursor-pointer transition-all"
                  style={{ background: "rgba(255,255,255,0.02)", border: "1px solid #1e1e1e" }}
                  onMouseEnter={e => { e.currentTarget.style.background = "rgba(204,0,0,0.06)"; e.currentTarget.style.borderColor = "#333"; }}
                  onMouseLeave={e => { e.currentTarget.style.background = "rgba(255,255,255,0.02)"; e.currentTarget.style.borderColor = "#1e1e1e"; }}>
                  <div>
                    <p className="text-white" style={{ fontSize: "12px" }}>{v.label}</p>
                    <p style={{ color: "#444", fontSize: "10px", textTransform: "uppercase", letterSpacing: "0.04em" }}>{v.type}</p>
                  </div>
                  <SmallBtn
                    onClick={() => {
                      if (!ensureBridge()) return;
                      const res = window.meacoreBridge?.installVersion?.(JSON.stringify({ version: v.label }));
                      if (typeof res === "string" && res !== "OK") {
                        window.alert(`No se pudo instalar versión: ${res}`);
                        return;
                      }
                      setInstalledVersionIds(prev => [...prev, v.id]);
                    }}
                  >
                    <Download size={11} /> Instalar
                  </SmallBtn>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* MODS */}
        {activeTab === "mods" && (
          <div className="flex-1 flex items-center justify-center flex-col gap-3">
            <Globe size={44} style={{ color: "#2a2a2a" }} />
            <p style={{ color: "#3a3a3a", fontSize: "13px", letterSpacing: "0.06em" }}>SOPORTE DE MODS PRÓXIMAMENTE</p>
          </div>
        )}

        {/* REGISTRO */}
        {activeTab === "registro" && <RegistroTab />}
      </div>

      {/* ── Bottom Bar ── */}
      <div className="relative z-20 flex items-center justify-between px-5 py-3"
        style={{ background: "rgba(6,6,6,0.98)", borderTop: "1px solid #1a1a1a" }}>

        {/* Profile */}
        <div className="relative flex items-center gap-3">

          {/* ── Profiles panel (popup above bottom bar) ── */}
          {profileOpen && (
            <>
              {/* Backdrop */}
              <div
                className="fixed inset-0 z-40"
                onClick={() => setProfileOpen(false)}
              />
              <div
                className="absolute bottom-full mb-2 left-0 z-50 rounded-lg overflow-hidden flex flex-col"
                style={{
                  width: "640px",
                  height: "420px",
                  background: "#0d0d0d",
                  border: "1px solid #1e1e1e",
                  boxShadow: "0 -12px 40px rgba(0,0,0,0.9)",
                }}
              >
                {/* Panel header */}
                <div className="flex items-center justify-between px-4 py-2" style={{ background: "rgba(6,6,6,0.95)", borderBottom: "1px solid #1a1a1a" }}>
                  <div className="flex items-center gap-2">
                    <UserCircle size={13} style={{ color: "#cc0000" }} />
                    <span style={{ color: "#777", fontSize: "10px", letterSpacing: "0.08em" }}>CONFIGURACIÓN</span>
                  </div>
                  <button
                    onClick={() => setProfileOpen(false)}
                    className="flex items-center justify-center w-5 h-5 rounded cursor-pointer"
                    style={{ color: "#444", background: "transparent", border: "none" }}
                    onMouseEnter={e => { e.currentTarget.style.color = "#cc0000"; e.currentTarget.style.background = "rgba(204,0,0,0.1)"; }}
                    onMouseLeave={e => { e.currentTarget.style.color = "#444"; e.currentTarget.style.background = "transparent"; }}
                  >
                    <X size={11} />
                  </button>
                </div>
                <div className="flex-1 overflow-hidden">
                  {profile && (
                    <ProfilesTab
                      profiles={profiles}
                      selectedId={selectedId}
                      onSelectProfile={setSelectedId}
                      profile={profile}
                      onUpdateProfile={updateProfile}
                      onAddProfile={addProfile}
                      onDeleteProfile={deleteSelectedProfile}
                      onSave={saveProfiles}
                      onPlay={() => { handlePlay(); setProfileOpen(false); }}
                    />
                  )}
                </div>
              </div>
            </>
          )}

          {/* Clickable profile trigger */}
          <button
            onClick={() => setProfileOpen(v => !v)}
            className="flex items-center gap-3 cursor-pointer rounded px-2 py-1 transition-all"
            style={{
              background: profileOpen ? "rgba(204,0,0,0.1)" : "transparent",
              border: `1px solid ${profileOpen ? "rgba(204,0,0,0.35)" : "transparent"}`,
              borderRadius: "6px",
            }}
            onMouseEnter={e => { if (!profileOpen) e.currentTarget.style.background = "rgba(255,255,255,0.04)"; }}
            onMouseLeave={e => { if (!profileOpen) e.currentTarget.style.background = "transparent"; }}
          >
            <div className="w-9 h-9 rounded overflow-hidden flex items-center justify-center"
              style={{ border: "2px solid #cc0000", background: "#1a1a1a", imageRendering: "pixelated" }}>
              <svg width="26" height="26" viewBox="0 0 8 8" style={{ imageRendering: "pixelated" }}>
                <rect x="1" y="1" width="6" height="6" fill="#c68642"/>
                <rect x="2" y="2" width="1" height="1" fill="#222"/>
                <rect x="5" y="2" width="1" height="1" fill="#222"/>
                <rect x="3" y="4" width="2" height="1" fill="#8b5e3c"/>
                <rect x="1" y="0" width="6" height="2" fill="#4a3728"/>
              </svg>
            </div>
            <div className="text-left">
              <p className="text-white" style={{ fontSize: "12px" }}>{username}</p>
              <p style={{ color: "#444", fontSize: "9px", letterSpacing: "0.05em" }}>{saved ? "GUARDADO" : "PERFIL ACTIVO"}</p>
            </div>
            <ChevronDown size={10} style={{ color: "#555", transform: profileOpen ? "rotate(180deg)" : "rotate(0deg)", transition: "transform 0.2s" }} />
          </button>
        </div>

        {/* Play controls */}
        <div className="flex items-center">
          <div className="relative">
            <button
              onClick={() => setVersionOpen(!versionOpen)}
              className="flex items-center gap-2 px-3 cursor-pointer transition-all"
              style={{
                height: "40px",
                background: "rgba(25,25,25,0.9)",
                border: "1px solid #2a2a2a",
                borderRight: "none",
                borderRadius: "5px 0 0 5px",
                outline: "none",
              }}
              onMouseEnter={e => (e.currentTarget.style.background = "rgba(40,40,40,0.9)")}
              onMouseLeave={e => (e.currentTarget.style.background = "rgba(25,25,25,0.9)")}
            >
              <div className="text-left">
                <p style={{ color: "#666", fontSize: "8px", letterSpacing: "0.08em" }}>VERSIÓN</p>
                <p style={{ color: "#ccc", fontSize: "11px" }}>{selectedVersion.label}</p>
              </div>
              <ChevronDown size={12} style={{ color: "#444", transform: versionOpen ? "rotate(180deg)" : "rotate(0)", transition: "transform 0.2s" }} />
            </button>
            {versionOpen && (
              <div className="absolute bottom-full mb-1 left-0 w-52 rounded-lg overflow-hidden z-50"
                style={{ background: "#0d0d0d", border: "1px solid #222", boxShadow: "0 -8px 24px rgba(0,0,0,0.7)" }}>
                {selectableVersions.map(v => (
                  <button
                    key={v.id}
                    onClick={() => { setSelectedVersion(v); setVersionOpen(false); }}
                    className="w-full flex items-center justify-between px-4 py-2 cursor-pointer text-left transition-colors"
                    style={{
                      background: selectedVersion.id === v.id ? "rgba(204,0,0,0.15)" : "transparent",
                      color: selectedVersion.id === v.id ? "#ff5555" : "#777",
                      fontSize: "12px",
                      border: "none",
                      outline: "none",
                    }}
                    onMouseEnter={e => { if (selectedVersion.id !== v.id) (e.currentTarget as HTMLButtonElement).style.background = "rgba(255,255,255,0.03)"; }}
                    onMouseLeave={e => { if (selectedVersion.id !== v.id) (e.currentTarget as HTMLButtonElement).style.background = "transparent"; }}
                  >
                    <span>{v.label}</span>
                    <span style={{ color: "#333", fontSize: "9px", textTransform: "uppercase" }}>{v.type}</span>
                  </button>
                ))}
              </div>
            )}
          </div>

          <button
            onClick={handlePlay}
            disabled={isPlaying}
            className="relative overflow-hidden flex items-center gap-2 cursor-pointer transition-all"
            style={{
              height: "40px", minWidth: "120px",
              background: isPlaying ? "#8b0000" : "#cc0000",
              border: "1px solid", borderColor: isPlaying ? "#8b0000" : "#ff2222",
              borderRadius: "0 5px 5px 0",
              color: "white", fontSize: "12px", letterSpacing: "0.1em",
              justifyContent: "center", outline: "none", padding: "0 20px",
            }}
            onMouseEnter={e => { if (!isPlaying) (e.currentTarget as HTMLButtonElement).style.background = "#e00000"; }}
            onMouseLeave={e => { if (!isPlaying) (e.currentTarget as HTMLButtonElement).style.background = "#cc0000"; }}
          >
            {isPlaying && <div className="absolute inset-0" style={{ background: "#6b0000", width: `${progress}%`, opacity: 0.5 }} />}
            <span className="relative z-10 flex items-center gap-1.5">
              {isPlaying
                ? <><RefreshCw size={12} className="animate-spin" />{progress < 100 ? `${Math.floor(progress)}%` : "CARGANDO..."}</>
                : <><Play size={12} fill="white" />JUGAR</>}
            </span>
          </button>
        </div>

        {/* Stats */}
        <div className="flex items-center gap-4">
          <div className="text-right">
            <p style={{ color: "#444", fontSize: "9px", letterSpacing: "0.05em" }}>RAM ASIGNADA</p>
            <p style={{ color: "#666", fontSize: "11px" }}>4 GB / 16 GB</p>
          </div>
          <div style={{ width: "1px", height: "28px", background: "#1a1a1a" }} />
          <div className="text-right">
            <p style={{ color: "#444", fontSize: "9px", letterSpacing: "0.05em" }}>JAVA</p>
            <p style={{ color: "#666", fontSize: "11px" }}>21.0.3</p>
          </div>
        </div>
      </div>

      {versionOpen && <div className="fixed inset-0 z-40" onClick={() => setVersionOpen(false)} />}
    </div>
  );
}
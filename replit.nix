{pkgs}: {
  deps = [
    pkgs.glib
    pkgs.gtk3
    pkgs.xorg.libXinerama
    pkgs.xorg.libXxf86vm
    pkgs.libGL
    pkgs.mesa
    pkgs.xorg.libxcb
    pkgs.xorg.libXfixes
    pkgs.xorg.libXdamage
    pkgs.xorg.libXcomposite
    pkgs.xorg.libXcursor
    pkgs.xorg.libXrandr
    pkgs.xorg.libXi
    pkgs.xorg.libXtst
    pkgs.xorg.libXrender
    pkgs.xorg.libXext
    pkgs.xorg.libX11
  ];
}

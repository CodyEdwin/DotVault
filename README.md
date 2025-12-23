# DotVault - Linux Dotfiles Backup Manager

A modern, feature-rich JavaFX application for backing up and restoring Linux configuration files (dotfiles). DotVault provides an intuitive GUI interface for managing your Linux configurations with support for compression, profiles, and comprehensive application support.

![DotVault Screenshot](docs/screenshot.png)

## Features

### Core Functionality
- **Intelligent Scanning**: Automatically discovers known configuration files across your home directory
- **Backup Operations**: Copy or compress selected configurations to a backup location
- **Restore Functionality**: Restore backups with conflict resolution
- **Permission Preservation**: Maintains file permissions and attributes (Linux-specific)
- **Timestamped Backups**: Creates organized, timestamped backup folders

### Comprehensive Application Support
DotVault includes a built-in database of **200+** known Linux applications and their configuration paths across **30+ categories**:

- **Shells**: Bash, Zsh, Fish
- **Terminals**: Alacritty, Kitty, WezTerm, URxvt, GNOME Terminal, Konsole, Terminator
- **Editors**: Neovim, Vim, Emacs, VS Code, Sublime Text, Atom, Micro, Spacemacs
- **Window Managers**: i3, Sway, Openbox, Awesome, bspwm, Qtile, XMonad, Hyprland
- **Status Bars**: Polybar, Waybar, i3status, Tint2, Lemonbar
- **Application Launchers**: Rofi, dmenu, Wofi, Fuzzel
- **File Managers**: Nautilus, Thunar, Dolphin, Ranger, LF, Yazi, Nnn
- **Media Players**: MPV, MOC, CMus, Spotify, VLC
- **Network & Security**: SSH, GnuPG, NetworkManager
- **System Tools**: HTop, Btop, Bottom, Neofetch, Fastfetch
- **Version Control**: Git, Mercurial, Subversion
- **Theme & Appearance**: GTK, KDE Plasma, Qt5ct, Qt6ct, GNOME Shell
- **Package Managers**: Pacman, Yay, Paru, Flatpak, Snap, NPM, Cargo, Pip
- And many more...

### Profiles
- Save frequently used configurations as named profiles
- Quick load/switch between different setups
- Export and import profiles

### Backup Options
- **Compression**: ZIP or tar.gz archives
- **Selective Backup**: Choose specific applications or categories
- **Exclude Patterns**: Skip files matching patterns (e.g., `*.log`, `cache`)
- **Symlink Handling**: Follow symlinks or copy as-is

### Modern UI
- **Dark Theme**: Easy on the eyes for extended use
- **Responsive Design**: Adaptable to different screen sizes
- **Progress Tracking**: Real-time progress during backup/restore
- **Detailed Logging**: Activity log with timestamps

## Requirements

- **Java**: OpenJDK 17 or later (JavaFX 21+ requires JDK 17+)
- **Operating System**: Linux (designed for X11/Wayland environments)
- **Build Tool**: Gradle 8.4 or later

## Installation

### Option 1: Build from Source

```bash
# Clone the repository
git clone https://github.com/yourusername/DotVault.git
cd DotVault

# Build the project
./gradlew build

# Run the application
./gradlew run
```

### Option 2: Create Distribution

```bash
# Create a fat JAR
./gradlew shadowJar

# Or create native packages (requires jpackage)
./gradlew jpackage
```

### Option 3: Install via Package Manager

On Arch Linux:
```bash
yay -S dotvault
```

## Usage

### Starting DotVault

```bash
./gradlew run
# or
java -jar build/libs/DotVault-1.0.0-all.jar
```

### Backing Up Your Dotfiles

1. Launch DotVault
2. The application automatically scans for known configurations
3. Select the configurations you want to backup:
   - Click categories in the sidebar to filter
   - Use checkboxes to select/deselect items
   - Click "Select All" to select everything found
4. Choose a backup destination (default: `~/Backups`)
5. Optionally enable compression
6. Click "Backup" to start

### Creating a Profile

1. Select the configurations you want in a profile
2. Go to the "Profiles" tab
3. Enter a profile name and description
4. Click "Save Profile"
5. Load the profile anytime to restore your selection

### Restoring Backups

1. Go to the "Restore" tab
2. Select the backup directory or archive
3. Choose restore options (overwrite, skip, rename)
4. Click "Start Restore"

### Command Line Options

```bash
java -jar DotVault.jar [OPTIONS]

Options:
  --backup <path>     Create backup immediately
  --restore <path>    Restore from backup path
  --profile <name>    Use specified profile
  --compress          Compress backup
  --quiet             Minimize output
  --help              Show help
```

## Configuration

### Settings Location
DotVault stores its settings in:
- **Config**: `~/.config/dotvault/settings.json`
- **Cache**: `~/.cache/dotvault/`
- **Logs**: `~/.cache/dotvault/logs/`

### Custom Applications

Add custom applications to the configuration database by editing `src/main/resources/known_configs.json`:

```json
{
  "Custom Category": {
    "MyApp": {
      "description": "My custom application",
      "configs": [
        {"path": "~/.config/myapp/config.json", "description": "Main config"},
        {"path": "~/.config/myapp/", "description": "Config directory", "directory": true}
      ]
    }
  }
}
```

## Architecture

```
src/main/java/com/dotvault/
├── App.java              # Main application entry point
├── controller/
│   ├── MainController.java     # Primary UI controller
│   ├── SettingsController.java # Settings dialog controller
│   └── ProfileController.java  # Profile management controller
├── model/
│   ├── ConfigEntry.java        # Configuration file model
│   ├── BackupProfile.java      # Profile model
│   └── Settings.java           # Application settings model
├── service/
│   ├── ConfigScanner.java      # Scans for config files
│   ├── BackupService.java      # Backup operations
│   └── RestoreService.java     # Restore operations
└── util/
    ├── PathUtils.java          # Path manipulation utilities
    └── FileUtils.java          # File operation utilities
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Adding New Applications

To add support for a new application:

1. Edit `src/main/resources/known_configs.json`
2. Add the application under the appropriate category
3. Include all config files and directories
4. Test the changes by running the application

## Troubleshooting

### Application won't start
- Ensure Java 17+ is installed: `java -version`
- Check that JavaFX is available
- Review logs at `~/.cache/dotvault/logs/`

### Config not found
- Verify the config path in `known_configs.json`
- Check file permissions
- Ensure the application is installed

### Backup fails
- Check destination directory permissions
- Verify sufficient disk space
- Review error messages in the log tab

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [JavaFX](https://openjfx.io/) - UI framework
- [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) - Archive support
- [Gson](https://github.com/google/gson/) - JSON serialization
- [Gradle](https://gradle.org/) - Build tool

## Support

- [GitHub Issues](https://github.com/yourusername/DotVault/issues)
- [Documentation](docs/)
- [Wiki](https://github.com/yourusername/DotVault/wiki)

---

Made with ❤️ for the Linux community

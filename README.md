# Set Spawn Mod for SSG

* Overrides player spawnpoint with specified coordinates 
* Configurable seeds and coordinates in `/config/setspawn.json`
* Coordinates are validated before overriding
* Works with multi using the global config file

## Usage

* Start Minecraft to generate the local and global config files
* If you have no config files, they will be populated with data from https://github.com/Minecraft-Java-Edition-Speedrunning/set-spawn-meta
* You must restart MC after editing the config for changes to go through
* The mod will only be active if you're on a seed that exists in the config
* Spawn will only be set if the coordinates are naturally rollable
* You may add or change as many seeds as you want to the config

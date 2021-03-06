# Percy WebStorm Editor Extension

Java 1.8

Intellij Idea

## Build WebStorm Extension

```bash
npm install -g lerna
lerna bootstrap --hoist

# Build web app
lerna run --scope=percy-cake-web-app --stream build:prod

# Build vscode extension
lerna run --scope=percy-vscode-extension --stream build:prod

# Copy file from vscode extension to webstorm extension
lerna run --scope=percy-webstorm-extension --stream build

# Package webstorm extension
lerna run --scope=percy-webstorm-extension --stream package

# To launch a sand-boxed instance of the IDE with the plugin (for testing)
lerna run --scope=percy-webstorm-extension --stream launch
```

The extension will be packaged at: `build/distributions/PercyEditor-1.0-SNAPSHOT.zip`.

After build, you can install the plugin by following these steps:
1. Open WebStorm
2. open WebStorm settings
3. Go to `Plugins` > `Install plugin from disk` > Browse to the location of the plugin zip > Click `Open`.
4. Restart WebStorm.
